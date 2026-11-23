import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
//
//
//
//

abstract public class Piece { // just contains basic information like positioning, backed range

final long size;

protected Piece(long size) {

if (size < 0) {throw new IllegalArgumentException("size cannot be negative");}

this.size = size;

}

public long getSize() {return this.size;}

public abstract Piece splitLeft(long localOffset);
public abstract Piece splitRight(long localOffset);

public abstract long getBackedIndex();

public abstract byte[] streamBytes();
//public abstract byte[] streamBytes(long localIndex,long size);
//public abstract int streamBytes(long localIndex,long size, byte[] dest, int destinationOffset) // future optimized signature


public static final class OriginalPiece extends Piece {

    final private long backedIndex;
    final private String backingPath;
    public OriginalPiece(long backedIndex, long backedSize, String backingPath) { // backedIndices must have a length of 2
        super(backedSize);
        if (backedIndex < 0) {throw new IllegalArgumentException("backedIndex cannot be negative");}
        this.backedIndex = backedIndex;
        this.backingPath = Objects.requireNonNull(
            backingPath, "backingPath cannot be null"
        );
    }

    public long getBackedIndex() {return this.backedIndex;}
    public String getBackingPath() {return this.backingPath;}

    public String toString() {return super.toString() + " len:"+this.size+" backedIndex: "+backedIndex;}

    public OriginalPiece splitLeft(long localOffset) {
        return new OriginalPiece(backedIndex,localOffset,backingPath);
    }

    public OriginalPiece splitRight(long localOffset) {
        return new OriginalPiece(backedIndex+localOffset,size-localOffset,backingPath);
    }

    public byte[] streamBytes() {

    byte[] cb = null;

    try (RandomAccessFile file = new RandomAccessFile(this.backingPath, "r")) {

    cb = new byte[(int) Math.max(1,Math.min(this.size,Integer.MAX_VALUE-8))];

    file.seek(this.backedIndex);

    file.readFully(cb);

    } catch (IOException e) {
      e.printStackTrace();
    }
    return cb;
    }

}

public static final class FillPiece extends Piece {

    byte fill;
    public FillPiece(long size, byte fill) {
    super(size);
    this.fill = fill;
    }

    public byte getFill() {return this.fill;}

    public FillPiece splitLeft(long localOffset) {
        return new FillPiece(localOffset,fill);
    }

    public FillPiece splitRight(long localOffset) {
        return new FillPiece(size-localOffset,fill);
    }

    public String toString() {
    return (super.toString()+" len:"+this.size+" fill: '"+String.format("0x%02X", this.fill)+"'");
    }

    public byte[] streamBytes() {

    byte[] cb = new byte[(int) Math.max(1,Math.min(Integer.MAX_VALUE-8,this.size))];

    Arrays.fill(cb,this.fill);

    return cb;
    }

    public long getBackedIndex() {return 0;}

    }



public static final class InsertPiece extends Piece {

    private final long backedIndex;
    private final Insert insert;

    public InsertPiece(long backedIndex, long backedSize, Insert insert) {

    super(backedSize);

    this.insert = Objects.requireNonNull(
            insert, "insert cannot be null"
        );

    if (backedIndex < 0 ||
    backedIndex > insert.getSize() ||
    backedSize > insert.getSize() - backedIndex) {
        throw new IllegalArgumentException("Invalid Insert backing range");
    }
    this.backedIndex = backedIndex;

    }

    public String toString() {return super.toString() + " len:"+this.size+" backedIndex: "+backedIndex;}

    public long getBackedIndex() {return this.backedIndex;}
    public Insert getInsert() {return this.insert;}

    public InsertPiece splitLeft(long localOffset) {
        return new InsertPiece(backedIndex,localOffset,insert);
    }

    public InsertPiece splitRight(long localOffset) {
        return new InsertPiece(backedIndex+localOffset,size-localOffset,insert);
    }

    public byte[] streamBytes() {return this.insert.getContents();}
}

public static final class WritePiece extends Piece {

    private final long backedIndex;
    private final Write write;

    public WritePiece(long backedIndex, long backedSize, Write write) {
    super(backedSize);

    this.write = Objects.requireNonNull(
            write, "write cannot be null"
        );

    if (backedIndex < 0 ||
    backedIndex > write.getSize() ||
    backedSize > write.getSize() - backedIndex) {
        throw new IllegalArgumentException("Invalid Write backing range");
    }
    this.backedIndex = backedIndex;
    }

    public long getBackedIndex() {return this.backedIndex;}
    public Write getWrite() {return this.write;}

    public String toString() {return super.toString() + " len:"+this.size+" backedIndex: "+backedIndex;}

    public WritePiece splitLeft(long localOffset) {
        return new WritePiece(backedIndex,localOffset,write);
    }

    public WritePiece splitRight(long localOffset) {
        return new WritePiece(backedIndex+localOffset,size-localOffset,write);
    }

    public byte[] streamBytes() {return this.write.getContents();}

}

}
