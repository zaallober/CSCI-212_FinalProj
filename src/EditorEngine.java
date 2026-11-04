import java.util.ArrayList;
import java.nio.file.*

    final public class EditorEngine {

  private List<Edit> editHistory = new ArrayList<>();

  private String filePath;
  private boolean fileFound;

  private byte[] fileBuffer;
  private int fileBufferSize;

  private String state;

  public class EditorEngine() {
    this.state = "Normal"

    // TODO init
  }

  public boolean isFileFound() { return fileFound; }

  public void hexLoad(String filePath) { hexLoad(filePath, 0); }

  public void hexLoad(String filePath, long seek) {

    if () {
      this.state = "File not found at provided path.";
      return;
    }

    // TODO impliment file streaming
  }
}
