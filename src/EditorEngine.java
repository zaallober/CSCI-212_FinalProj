import java.io.IOException;
import java.io.RandomAccessFile; // for file buffer

final public class EditorEngine {

  private EditHistory history;

  private String filePath;
  private boolean fileFound;

  // private byte[] fileBuffer; retiring this to move ownership of file buffers
  // to OpInterpreter() entirely.
  private int previewBufferSize;

  private long previewSeek;

  private String state;

  public EditorEngine(int previewBufferSize) {
    this.previewBufferSize = previewBufferSize;
    this.previewSeek = 0;
  }
  public EditorEngine() { this(4096); }

  public boolean isFileFound() { return fileFound; }

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
    for (int i = 0; i < len; i++) {
      byte b = fb[i];
      System.out.printf("%02X ", b & 0xFF);
    }
    System.out.println();
  }

  public void hexWrite(long offset, byte[] contents) {
    this.history.appendEdit(new Write(offset, contents));
  }

  public void hexInsert(long offset, long size, byte fill) {
    this.history.appendEdit(new Insert(offset, size, fill));
  }

  public void hexDelete(long offset, long size) {
    this.history.appendEdit(new Delete(offset, size));
  }

  private byte[] OpInterpreter(long previewSeek) {
    return OpInterpreter(previewSeek, this.history.getHead(),
                         this.previewBufferSize);
  }

  private byte[] OpInterpreter(long previewSeek, int historyHead,
                               int previewBufferSize) {

    byte[] fb;

    EditHistory activeHistory = this.history;
    long originalFileSeek;
    long reversePassDelta = 0;
    long forwardPassDelta = 0;

    for (int i = historyHead; i >= 0; i--) {
      if (activeHistory.getEdit(i).getType() == EditType.INSERT &&
          activeHistory.getEdit(i).getOffset() < previewSeek) {
        reversePassDelta -= ((Insert)activeHistory.getEdit(i))
                                .getSize(); // downcast to child class
      }
      if (activeHistory.getEdit(i).getType() == EditType.DELETE &&
          activeHistory.getEdit(i).getOffset() < previewSeek) {
        reversePassDelta += ((Delete)activeHistory.getEdit(i)).getSize();
      }
    }
    originalFileSeek = previewSeek + reversePassDelta;
    fb = this.hexLoad(previewBufferSize, this.filePath,
                      originalFileSeek); // showtime motherfucker :: receives
                                         // newly created byte[] from callee.

    for (int i = 0; i <= historyHead; i++) {

      // if offset > filebuffer upper bound && op upper bound < offset, edit is
      // irrelevant

      switch (activeHistory.getEdit(i).getType()) {
      case EditType.WRITE:
        long upperBound = ((Write)activeHistory.getEdit(i)).getUpperBound();
        if (true) { // we add the -1, as write occupies its immediate offset,
                    // and its bound must reflect this
        }
        break;
      case EditType.INSERT:

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

        break;
      }
    } // I can only hope the JVM finds a way to vectorize this.

    return fb;
  }

  // public byte[] generatePreview() {}
}
