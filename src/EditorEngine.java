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

  private int fileSeek;

  private String state;


  public EditorEngine(int fileBufferSize) {
    this.fileBufferSize = fileBufferSize;
    this.fileBuffer = new byte[fileBufferSize];
    this.fileSeek = 0;


    // TODO init
  }

    public EditorEngine() {
    this(4096);
  }


  public boolean isFileFound() { return fileFound; }

  public int hexLoad(String filePath) { return hexLoad(filePath, 0); }

  public int hexLoad(String filePath, int seek) {

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


  public static void main(String[] args) {
    EditorEngine e = new EditorEngine();

    e.readBuffer(e.hexLoad(args[0], Integer.parseInt(args[1])));

    return;
  }

}
