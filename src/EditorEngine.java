import java.util.ArrayList;

import java.util.List;
import java.nio.file.*;

import java.io.RandomAccessFile; // for file buffer
import java.io.IOException;


    final public class EditorEngine {

  private EditHistory history;

  private String filePath;
  private boolean fileFound;

  private byte[] fileBuffer;
  private int fileBufferSize;

  private long previewSeek;

  private String state;


  public EditorEngine(int fileBufferSize) {
    this.fileBufferSize = fileBufferSize;
    this.fileBuffer = new byte[fileBufferSize];
    this.previewSeek = 0;
  }
    public EditorEngine() {
    this(4096);
  }


  public boolean isFileFound() { return fileFound; }

  public int hexLoad(String filePath) { return hexLoad(filePath, 0); }

  public int hexLoad(String filePath, long seek) {

    int bytesRead = 0;

    try (RandomAccessFile file = new RandomAccessFile(filePath,"r")) {

      file.seek(seek);
      bytesRead = file.read(this.fileBuffer,0,this.fileBufferSize);

      //file.close(); //handled automaticall on scope escape

    } catch (IOException e) {
      e.printStackTrace();
    }
    return bytesRead;
  }

  public void readBuffer(int len) {
    for (int i = 0 ; i < len ; i++ ) {
      byte b = this.fileBuffer[i];
      System.out.printf("%02X ", b & 0xFF);
    }
    System.out.println();
  }

  public void hexWrite(long offset, byte[] contents) {
    this.history.appendEdit(new Write(offset,contents));
  }

  public void hexInsert(long offset, long size, char fill) {
    this.history.appendEdit(new Insert(offset,size,fill));
  }

  public void hexDelete(long offset, long size) {
    this.history.appendEdit(new Delete(offset,size));
  }

  private void OpInterpreter() {

  EditHistory activeHistory = this.history;
  long previewSeek = this.previewSeek;
  long originalFileSeek;
  long reversePassDelta = 0;
  long forwardPassDelta = 0;

  for (int i = activeHistory.getHead(); i >= 0; i--) {
    if (activeHistory.getEdit(i).getType() == INSERT && activeHistory.getEdit(i).getOffset() < previewSeek) {
      reversePassDelta -= activeHistory[i].getSize();
    }
    if (activeHistory.getEdit(i).getType() == DELETE && activeHistory.getEdit(i).getOffset() < previewSeek) {
      reversePassDelta += activeHistory[i].getSize();
    }
  }
  originalFileSeek = previewSeek+reversePassDelta;
  this.hexLoad(this.filePath,originalFileSeek); // showtime motherfucker

  for (int i = 0 ; i < activeHistory.getHead(); i++) {



    switch (activeHistory[i].getType()) {
      case EditType.WRITE:
        break;
      case EditType.INSERT:
        break;
      case EditType.DELETE:
        break;
    }
  } // I can only hope the JVM finds a way to vectorize this.


  }

  public byte[] generatePreview() {}






  public static void main(String[] args) {
    EditorEngine e = new EditorEngine();

    e.readBuffer(e.hexLoad(args[0], Integer.parseInt(args[1])));

    return;
  }

}
