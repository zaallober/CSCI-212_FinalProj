import java.awt.desktop.OpenURIEvent;
import java.io.IOException;
import java.io.RandomAccessFile; // for file buffer
import java.util.ArrayList;
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

  public PieceTable OpInterpreter(final long previewSeek) {
    return OpInterpreter(previewSeek, this.history.getHead(),this.previewBufferSize);
  }

  private long getPreviewFileSize() {
  return -1;
  }


  public PieceTable OpInterpreter(final long previewSeek, int historyHead, int previewBufferSize) {
    PieceTable pTable = new PieceTable();

    //initial fill
    long backedFileSize=0;

    try (RandomAccessFile file = new RandomAccessFile(this.filePath, "r")) {
      backedFileSize = file.length();
    } catch (final IOException e) {
      e.printStackTrace();
    }

    pTable.replaceRange(0,0,new Piece.OriginalPiece(0,backedFileSize,this.filePath)); // insert original backed material // if no file is found, nothing is inserted.

    for (int i = 0;i<=historyHead;i++) { // mulching time

      Edit edit = this.history.getEdit(i);
      long offset = edit.getOffset();
      long size = edit.getSize();

      //System.out.println("i: "+i+" offset: "+offset+" size: "+size);

      switch (edit.getType()) {

      case EditType.DELETE:
        pTable.replaceRange(offset,size,null);
        break;
      case EditType.INSERT:
        pTable.replaceRange(offset,0,new Piece.InsertPiece(0,size,(Insert) edit));
        break;
      case EditType.WRITE:
        pTable.replaceRange(offset,size,new Piece.WritePiece(0,size,(Write) edit));
        break;

      }
    }

    return pTable;
  }

  private void materializePreview() {return;}

  public List<Piece> generatePreviewWindow(final long previewSeek) {

      // we have a compiled piece PieceTable
      // we have a file offset, previewSeek.

      List<Piece> window = new ArrayList<>();

      PieceTable pTable = this.OpInterpreter(0);

      long length = pTable.getLength();

      if (previewSeek < 0 || previewSeek >= length || this.previewBufferSize <= 0) {return window;}

      long endIndex = Math.min(length,Math.addExact(previewSeek,this.previewBufferSize)) - 1;

      if (endIndex < previewSeek) {return window;}

      int firstPieceIndex = pTable.findPieceIndex(previewSeek);
      int secondPieceIndex = pTable.findPieceIndex(endIndex);

      // we can create a sublist of the pieces that are within the buffer

      System.out.println("Window indices: ["+previewSeek+","+endIndex+"]");
      System.out.println("Piece indices: ["+firstPieceIndex+","+secondPieceIndex+"]");

      if (firstPieceIndex < 0 || secondPieceIndex < 0) {return window;}

      window = pTable.getPieces().subList(firstPieceIndex,secondPieceIndex+1);

      return window;
    }
}

  public void main(final String[] args) {

  EditorEngine e = new EditorEngine(8);

  e.hexLoad(args[0]);

  e.hexInsert(6,24,(byte)0xFF);
  e.hexWrite(4,new byte[] {0x01,0x02,0x03,0x04});
  e.hexDelete(18,12);

  PieceTable pTable = e.OpInterpreter(0);
  System.out.println(pTable.getPieces().toString()+" Final Length: "+pTable.getLength());

  System.out.println(e.generatePreviewWindow(Integer.parseInt(args[1])).toString());

  return;
  }
