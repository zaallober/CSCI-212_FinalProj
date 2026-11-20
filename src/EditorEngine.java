import java.io.IOException;
import java.io.RandomAccessFile; // for file buffer
import java.util.Arrays;

final public class EditorEngine {

  private final EditHistory history;

  private String filePath;

  // private byte[] fileBuffer; retiring this to move ownership of file buffers
  // to OpInterpreter() entirely.
  private int previewBufferSize;

  private final long previewSeek;

  private byte defaultFill = (byte) 0;

  public EditorEngine(int previewBufferSize) {
    this.previewBufferSize = previewBufferSize;
    this.previewSeek = 0;
    this.history = new EditHistory();
  }
  public EditorEngine() { this(4096); }

  public boolean isFileFound() {
      if (this.filePath == null) {return false;}
      try (RandomAccessFile file = new RandomAccessFile(this.filePath,"r")) {
          return true;
      } catch (final IOException e) {
      e.printStackTrace();
      return false;
      }
    }

  public void hexLoad(final String filePath) {
    this.filePath = filePath;
    return;
  }

  private byte[] hexLoad(final int bufferSize, final String filePath, final long seek) {

    byte[] fb = {};

    try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {

      file.seek(seek);

      final int available = (int)Math.min(
          bufferSize,
          file.length() - seek); // handles EOF better than initial impl.

      fb = new byte[Math.max(0, available)]; // directly creates a new buffer
                                             // that is correctly sized.

      file.readFully(fb); // populates it as much as it can.
      // file.close(); //handled automaticall on scope escape

    } catch (final IOException e) {
      e.printStackTrace();
    }
    return fb; // return handle to new file buffer
  }

  public void readBuffer(int len, final byte[] fb) {
    len = Math.min(len,fb.length); //guard against out of bounds indexing
    for (int i = 0; i < len; i++) {
      final byte b = fb[i];
      System.out.printf("%02X ", b & 0xFF);
    }
    System.out.println();
  }

  public boolean isModified() {if (this.history.getHead()==-1) {return false;} else {return true;}}

  public long getFileSize() {
  if (this.filePath == null) {return 0;}

  try (RandomAccessFile file = new RandomAccessFile(this.filePath,"r")) {

    return file.length();

  } catch (final IOException e) {
    e.printStackTrace();
  }
  return 0;
  }

  public void setFill(final byte fill) {
    this.defaultFill = fill;
  }

  public void hexWrite(final long offset, final byte[] contents) {
    this.history.appendEdit(new Write(offset, contents));
  }

  public void hexInsert(final long offset, final byte[] contents) {
    this.history.appendEdit(new Insert(offset, contents)); //insert substring support
  }

  public void hexInsert(final long offset, final long size) {
    this.history.appendEdit(new Insert(offset,size,this.defaultFill));
  }

  public void hexInsert(final long offset, final long size, final byte fill) {
    this.history.appendEdit(new Insert(offset, size, fill));
  }

  public void hexDelete(final long offset, final long size) {
    this.history.appendEdit(new Delete(offset, size));
  }

  public void hexUndo() {this.history.undo();}
  public void hexRedo() {this.history.redo();}

  private byte[] OpInterpreter(final long previewSeek) {
    return OpInterpreter(previewSeek, this.history.getHead(),
                         this.previewBufferSize);
  }


    private long calcDiff(final Edit edit, long previewFileSize) {
          final long offset = edit.getOffset();
          final long originalSize = previewFileSize;
      switch (edit.getType()) {
        case EditType.WRITE:
          if (offset > previewFileSize) previewFileSize = offset;
          final long writeEnd = ((Write)edit).getUpperBound() + 1;
          if (writeEnd > previewFileSize) previewFileSize = writeEnd;
          break;
        case EditType.INSERT:
          if (offset > previewFileSize) previewFileSize = offset;
          previewFileSize += ((Insert)edit).getSize();
          break;
        case EditType.DELETE:
          if (offset >= previewFileSize) return 0;
          previewFileSize -= Math.min(previewFileSize - offset, ((Delete)edit).getSize());
          break;
      }
          return previewFileSize - originalSize;
  }


  public long getPreviewFileSize() { //required both for gui presentation but solving a dependency issue fawned by extending writes/inserts in calcReversePassDelta, previously the simple model with inserts and deletes was enough,
    return getPreviewFileSize(getFileSize(),this.history.getHead(),this.history);
  }

  private long getPreviewFileSize(final long rawFileLength,final int historyHead, final EditHistory activeHistory) {
    long previewFileSize = rawFileLength;

    for (int i=0; i<=historyHead;i++) {
      previewFileSize += calcDiff(activeHistory.getEdit(i),previewFileSize);
    }
    return previewFileSize;
  }


  private byte[] OpInterpreter(final long previewSeek, int historyHead,
                               int previewBufferSize) {


    final int W = Math.max(0, previewBufferSize);
    if (W == 0) {
      return new byte[0];
    }

    final long S = previewSeek;
    final long logicalLen = getPreviewFileSize();

    // Full original (pre-edit) content: O[k].
    final byte[] O = loadOriginalFile();


    final Edit[] edits = new Edit[Math.max(0, historyHead + 1)];
    for (int i = 0; i < historyHead + 1; i++) {
      edits[i] = this.history.getEdit(i);
    }

    // Mark original bytes that get deleted so they are not re-placed.
    final boolean[] deleted = new boolean[O.length];
    for (final Edit e : edits) {
      if (e.getType() == EditType.DELETE) {
        final int o = (int) e.getOffset();
        final int s = (int) ((Delete) e).getSize();
        for (int i = o; i < Math.min(o + s, O.length); i++) {
          deleted[i] = true;
        }
      }
    }


    // Result is zero-filled; any frame left untouched is the EOF fill (0).
    final byte[] res = new byte[W];

    // 1) Place each surviving original byte at its translated frame:
    //    frame = (k + deltaUpTo(k)) - S
    for (int k = 0; k < O.length; k++) {
      if (deleted[k]) continue;
      final long L = k + deltaUpTo(edits, k);
      if (L >= S && L < S + W) {
        res[(int) (L - S)] = O[k];
      }
    }

    // 2) Overlay each edit at its translated logical range.
    //    frameStart = (offset + deltaBefore(offset)) - S
    for (final Edit e : edits) {
      final long ls = e.getOffset() + deltaBefore(edits, e.getOffset());
      final long fStart = ls - S;
      switch (e.getType()) {
        case EditType.INSERT: {
          final Insert ins = (Insert) e;
          final int len = (int) ins.getSize();
          final byte[] contents = ins.getContents();
          for (int d = 0; d < len; d++) {
            final long f = fStart + d;
            if (f < 0 || f >= W) continue;
            final byte b = (contents != null && d < contents.length) ? contents[d] : ins.getFill();
            res[(int) f] = b;
          }
          break;
        }
        case EditType.WRITE: {
          final byte[] c = ((Write) e).getContents();
          for (int d = 0; d < c.length; d++) {
            final long lp = ls + d; // logical position
            if (lp >= logicalLen) break; // clip writes to the file's logical end
            final long f = fStart + d;
            if (f < 0 || f >= W) continue;
            res[(int) f] = c[d];
          }
          break;
        }
        case EditType.DELETE:
          // Nothing to overlay; deletion is already reflected by `deleted[]`
          // and by the negative delta in deltaUpTo / deltaBefore.
          break;
        default:
          break;
      }
    }

    return res;
  }

  // Full original (pre-edit) file content, O[k].
  private byte[] loadOriginalFile() {
    final int len = (int) getFileSize();
    final byte[] buf = new byte[Math.max(0, len)];
    try (RandomAccessFile raf = new RandomAccessFile(this.filePath, "r")) {
      raf.seek(0);
      if (len > 0) {
        raf.readFully(buf);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return buf;
  }

  // Forward twin of the reverse-pass delta: net (insert - delete) size of
  // edits with offset <= p.
  private long deltaUpTo(final Edit[] edits, final long p) {
    long d = 0;
    for (final Edit e : edits) {
      if (e.getOffset() <= p) {
        if (e.getType() == EditType.INSERT) d += ((Insert) e).getSize();
        else if (e.getType() == EditType.DELETE) d -= ((Delete) e).getSize();
      }
    }
    return d;
  }

  // Forward twin of the reverse-pass delta: net (insert - delete) size of
  // edits with offset < p (strictly before).
  private long deltaBefore(final Edit[] edits, final long p) {
    long d = 0;
    for (final Edit e : edits) {
      if (e.getOffset() < p) {
        if (e.getType() == EditType.INSERT) d += ((Insert) e).getSize();
        else if (e.getType() == EditType.DELETE) d -= ((Delete) e).getSize();
      }
    }
    return d;
  }

  public byte[] generatePreview(final long previewSeek) {
      return OpInterpreter(previewSeek, history.getHead(), previewBufferSize);
    }
}

  public void main(final String[] args) {
    final EditorEngine e = new EditorEngine(8);

    e.hexLoad(args[0]);


    e.history.appendEdit(new Write(2,new byte[] {0x02,0x03}));
    e.history.appendEdit(new Insert(1,4,(byte) 0x10));

    System.out.println(e.generatePreview(Integer.parseInt(args[1])));

  }
