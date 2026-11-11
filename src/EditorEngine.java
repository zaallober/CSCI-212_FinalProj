import java.io.IOException;
import java.io.RandomAccessFile; // for file buffer

final public class EditorEngine {

  private EditHistory history;

  private String filePath;

  // private byte[] fileBuffer; retiring this to move ownership of file buffers
  // to OpInterpreter() entirely.
  private int previewBufferSize;

  private long previewSeek;

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
      } catch (IOException e) {
      e.printStackTrace();
      return false;
      }
    }

  public void hexLoad(String filePath) {
    this.filePath = filePath;
    return;
  }

  private byte[] hexLoad(int bufferSize, String filePath, long seek) {

    byte[] fb = {};

    try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {

      file.seek(seek);

      int available = (int)Math.min(
          bufferSize,
          file.length() - seek); // handles EOF better than initial impl.

      fb = new byte[Math.max(0, available)]; // directly creates a new buffer
                                             // that is correctly sized.

      file.readFully(fb); // populates it as much as it can.
      // file.close(); //handled automaticall on scope escape

    } catch (IOException e) {
      e.printStackTrace();
    }
    return fb; // return handle to new file buffer
  }

  public void readBuffer(int len, byte[] fb) {
    len = Math.min(len,fb.length); //guard against out of bounds indexing
    for (int i = 0; i < len; i++) {
      byte b = fb[i];
      System.out.printf("%02X ", b & 0xFF);
    }
    System.out.println();
  }

  public boolean isModified() {if (this.history.getHead()==-1) {return false;} else {return true;}}

  public long getFileSize() {
  if (this.filePath == null) {return 0;}

  try (RandomAccessFile file = new RandomAccessFile(this.filePath,"r")) {

    return file.length();

  } catch (IOException e) {
    e.printStackTrace();
  }
  return 0;
  }

  public void setFill(byte fill) {
    this.defaultFill = fill;
  }

  public void hexWrite(long offset, byte[] contents) {
    this.history.appendEdit(new Write(offset, contents));
  }

  public void hexInsert(long offset, byte[] contents) {
    this.history.appendEdit(new Insert(offset, contents)); //insert substring support
  }

  public void hexInsert(long offset, long size) {
    this.history.appendEdit(new Insert(offset,size,this.defaultFill));
  }

  public void hexInsert(long offset, long size, byte fill) {
    this.history.appendEdit(new Insert(offset, size, fill));
  }

  public void hexDelete(long offset, long size) {
    this.history.appendEdit(new Delete(offset, size));
  }

  public void hexUndo() {this.history.undo();}
  public void hexRedo() {this.history.redo();}

  private byte[] OpInterpreter(long previewSeek) {
    return OpInterpreter(previewSeek, this.history.getHead(),
                         this.previewBufferSize);
  }



  public long getPreviewFileSize() { //required both for gui presentation but solving a dependency issue fawned by extending writes/inserts in calcReversePassDelta, previously the simple model with inserts and deletes was enough,
    return getPreviewFileSize(getFileSize(),this.history.getHead(),this.history);
  }

  private long getPreviewFileSize(long rawFileLength,int historyHead, EditHistory activeHistory) {
    long previewFileSize = rawFileLength;

    for (int i=0; i<=historyHead;i++) {
      long offset = activeHistory.getEdit(i).getOffset();
      switch (activeHistory.getEdit(i).getType()) {
        case EditType.WRITE:
          if (offset > previewFileSize) {previewFileSize += (offset-previewFileSize);}
          previewFileSize += Math.max(((Write)activeHistory.getEdit(i)).getUpperBound()+1-previewFileSize,0); //getUpperBound returns an index. +1 to correct into a count.
          break;
        case EditType.INSERT:
          if (offset > previewFileSize) {previewFileSize += (offset-previewFileSize);}
          previewFileSize += ((Insert)activeHistory.getEdit(i)).getSize();
          break;
        case EditType.DELETE:
          if (offset >= previewFileSize) {break;} // ignore deletes past EOF // undetermined whethr or not > is the correct comparator here. > suggests offset marks the beginning, which would mirror Insert semantics
          previewFileSize -= Math.min(previewFileSize-offset,((Delete)activeHistory.getEdit(i)).getSize());
          break;
      }
    }
    return previewFileSize;
  }


  private long calcReversePassDelta(long previewSeek, int historyHead, EditHistory activeHistory) { //needs redone to account for extending writes/inserts
    long reversePassDelta = 0;
    long rawFileLength = getFileSize();
    long effectiveFileLength = getPreviewFileSize(rawFileLength,historyHead,activeHistory);

    for (int i = historyHead; i >= 0; i--) {

      switch (activeHistory.getEdit(i).getType()) {
        case EditType.INSERT:
          if (activeHistory.getEdit(i).getOffset() < previewSeek) {
            reversePassDelta -= ((Insert)activeHistory.getEdit(i)).getSize();
          }
          break;
        case EditType.DELETE:
          if (activeHistory.getEdit(i).getOffset() < previewSeek) {
            reversePassDelta += ((Delete)activeHistory.getEdit(i)).getSize();
          }
          break;
        case EditType.WRITE: // to accomodate for writes beyond EOF, rpd needs to correctly catch and account for these.
          //TODO catch logic for this implementation
          //
          // if offset+contents.size()-1 > rawFileLength+reversePassDelta then handle offset + contents.size()-1 -= rpd
          //
          // need to algorithmically treat these cases like they are inserts
          // beginning at EOF for
          // calculating rpd

          break;
      }
    }
    return reversePassDelta;
  }

  private byte[] OpInterpreter(long previewSeek, int historyHead,
                               int previewBufferSize) {

    byte[] pb;

    EditHistory activeHistory = this.history;
    long originalFileSeek;
    long reversePassDelta = 0;
    long forwardPassDelta = 0;


    reversePassDelta = calcReversePassDelta(previewSeek,historyHead,activeHistory);

    originalFileSeek = Math.max(0,previewSeek + reversePassDelta);
    pb = this.hexLoad(previewBufferSize, this.filePath,
                      originalFileSeek); // showtime motherfucker :: receives
                                         // newly created byte[] from callee.

    for (int i = 0; i <= historyHead; i++) { //now correctly skips both passes as head represents index instead of count now.

      // if op offset > filebuffer upper bound || op upper bound < previewSeek, edit is
      // irrelevant
      // conversely,
      // if previewSeek <= op offset && op offset <= filebuffer upper bound, edit is
      // relevant

      switch (activeHistory.getEdit(i).getType()) {
      case EditType.WRITE:
        long upperBound = ((Write)activeHistory.getEdit(i)).getUpperBound();
        if (forwardPassDelta+activeHistory.getEdit(i).getOffset() > previewSeek+previewBufferSize
            ||
            ((Write)activeHistory.getEdit(i)).getUpperBound()+forwardPassDelta< previewSeek) {break;} //Early exit for irrelevancy

            long editFrameIndex = activeHistory.getEdit(i).getOffset()-originalFileSeek+forwardPassDelta; //we love pointer arithmetic

            if (editFrameIndex>Integer.MAX_VALUE) {throw new IndexOutOfBoundsException("Frame index is out of bounds");}

            byte[] cb = ((Write)activeHistory.getEdit(i)).getContents();

            for (int j = 0;j<cb.length;j++) {
              if (editFrameIndex+j >= pb.length) {break;} //upper bound guarding
              if (editFrameIndex+j < 0) {continue;}
              pb[((int)editFrameIndex+j)] = cb[j];
            }

        // TODO Implement sequence extension logic for cases wherein EOF < offset. extend sequence by offset-EOF+contents.length+1, then write. which, this kinda fucking sucks. Will have to revisit reversePassDelta calcs.

        break;
      case EditType.INSERT:
        // TODO Actually implement insert
        break;
      case EditType
          .DELETE: // OOPS! Delete will involve recursively calling
                   // generatePreview. this presents a bit of a new problem
                   // though, hexLoad is relying on the fileBuffer owned by the
                   // engine. architecturally it's beginning to make a lot more
                   // sense to allocate that file buffer to the function scope
                   // of generatePreview. Changes to make this happen: hexLoad
                   // will now return byte[] copy by clone. a new overload, void
                   // hexLoad(String f) is born to set the filePath in the
                   // engine state.
        // TODO Actually implement Delete
        break;
      }
    } // I can only hope the JVM finds a way to vectorize this.

    return pb;
  }

  // public byte[] generatePreview() {} //TODO implement this puppy
}
