enum EditType { WRITE, INSERT, DELETE }

abstract public class Edit {
  final private EditType type;
  final private long offset;

  public Edit(EditType type, long offset) {
    this.type = type;
    this.offset = offset;
  }

  public EditType getType() { return this.type; }

  public long getOffset() { return this.offset; }
}

final class Write extends Edit {
  final private byte[] contents;

  public Write(long offset, byte[] contents) {
    super(EditType.WRITE, offset);
    this.contents =
        contents != null
            ? contents.clone()
            : new byte[0]; //!= null guards against passing a null pointer,
                           //which will throw an exception.
  }

  public byte[] getContents() { return this.contents.clone(); }
  public long getUpperBound() { return (this.contents.length -1 + this.offset);}
}

final class Insert extends Edit {

  final private long size;
  final private byte fillInit;
  final private byte[] contents;

  public Insert(long offset,
                long size) { // java doesn't support default arguments so we
                             // have to overload the constructor with an
                             // additional constructor that uses the default
    this(offset, size, (byte)0);
  }

  public Insert(long offset, long size, byte fillInit) {
    super(EditType.INSERT, offset);
    this.size = size;
    this.fillInit = fillInit;
    this.contents = null;
  }

  public Insert(long offset, byte[] contents) {
  super(EditType.INSERT, offset);
  this.contents = contents.clone();
  this.size = contents.length;
  this.fillInit = (byte)0;
  }



  public long getSize() { return this.size; }

  public long getUpperBound() { //walking back size amount of elements from the upper bound takes you to this.offset + 1.
    return this.offset+this.size;
  }

  public byte getFill() { return this.fillInit; }
}

final class Delete extends Edit {

  final private long size;

  public Delete(long offset, long size) {
    super(EditType.DELETE, offset);
    this.size = size;
  }

  public long getSize() { return this.size; }
}
