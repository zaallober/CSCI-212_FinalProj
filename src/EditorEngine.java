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

  private PieceTable OpInterpreter(final long previewSeek) {
    return OpInterpreter(previewSeek, this.history.getHead(),this.previewBufferSize);
  }

  private long getPreviewFileSize() {
  return -1;
  }


  private PieceTable OpInterpreter(final long previewSeek, int historyHead, int previewBufferSize) {


    return null;
  }

  public byte[] generatePreview(final long previewSeek) {

      return null;
    }
}

  public void main(final String[] args) {
  return;
  }
