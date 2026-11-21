import java.util.Objects;
import java.util.List;
import java.util.ArrayList;

//
// okay, going to pivot the approach from treating OpInterpreter as a buffer mutator.
// This approach should be substantially cheaper too.
//
// It should be easier to bild a representation of the final file that is cheap and disposable, then project and back just the portions that we need with their referenced content.
// Should also easily handle empty file creation with just one big fill piece.
//
// 1 [Original] <- Original file
// 2 [Original][Insert][Original] <- keeps original backed file location index.
// 3 [Original][Insert][Original][Original] <- delete splits any two pieces
// 4 [Original][Insert][Insert][Original][Original] <- this goes for insert pieces too.
// 5 [Original][Insert][Insert][Original][Write] <- Write overwrites offset regions in the piece table, overlapping etc, split,pop, whatever.
// 6 [Original][Insert][Insert][Original][Write][Fill] <- Offsets past logical EOF generate a fill piece that is subsequently partially or entirely overwritten
// 7 [Original][Insert][Insert][Original][Write][Write] <- Final piece table to be materialized with the backed portions, logical portions, offsets are final, preview seek should easily use this.


// PieceTable compile(EditHistory history);

// byte[] project(PieceTable table, long seek, int length);

final public class PieceTable{

    final private List<Piece> pieces = new ArrayList<>();
    private long length = 0;

    public PieceTable() {}

    public List<Piece> getPieces() {return pieces;}

    public int findPieceIndex(
    long offset) { // find the index of the piece containing this offset.
    long rollingSize = 0;
    for (int i = 0; i < pieces.size(); i++) {
        long pieceSize = pieces.get(i).getSize();

        if (rollingSize <= offset &&
            offset - rollingSize < pieceSize) {
            return i;
        }
        rollingSize += pieceSize;
    }
    return -1;
    }

    public Piece getPiece(int pieceIndex) {return pieces.get(pieceIndex);}

    public Piece findPiece(long offset) {
    int index = findPieceIndex(offset);

    if (offset < 0) {
    throw new IllegalArgumentException("offset cannot be negative");
    } else if (index == -1) {
    throw new IndexOutOfBoundsException("no piece contains offset: "+offset);
    } else {
    return pieces.get(index);}
    }

    public int findLocalOffset(long offset) {
    int index = findPieceIndex(offset);
    if (index == -1) {return -1;}

    long sizeUpTo=0;
    for (int i=0; i<index;i++) {
        sizeUpTo     += pieces.get(i).getSize();
        }
    return Math.toIntExact(offset-sizeUpTo);
    }

    public int findInsertionIndex(long offset) { // handle EOF boundary conditions

    if (offset < 0) {return -1;}

    long rollingSize = 0;

    for (int i = 0; i<pieces.size();i++) {
        if (offset == rollingSize) {return i;}

        rollingSize += pieces.get(i).getSize();
    }

        if (rollingSize == offset) { // insert at EOF
            return pieces.size();
        }
        return -1;
    }

    public long getLength() {
    long rollingSize=0;

    for (int i=0; i<pieces.size();i++) {
        rollingSize = Math.addExact(rollingSize,pieces.get(i).getSize());
    }

    return rollingSize;
    }


    private void splitPiece(int pieceIndex, long localOffset) {

    Piece piece = pieces.get(pieceIndex);

    if (localOffset <= 0 || localOffset >= piece.getSize()) {
        throw new IllegalArgumentException("Split must be inside piece bounds");
    }

    Piece left = piece.splitLeft(localOffset);
    Piece right = piece.splitRight(localOffset);

    pieces.set(pieceIndex, left);
    pieces.add(pieceIndex + 1, right);
    }

    private void insertPiece(int pieceIndex, Piece piece) {
        pieces.add(pieceIndex,piece);
    }

    public void replaceRange(long offset, long size, Piece piece) { // here be dragons

    //split at both offset and offset+size, remove both inner members, then add the piece



    if (offset < 0 || size < 0) {throw new IllegalArgumentException("Invalid range");}
    if (size == 0) {Objects.requireNonNull(piece, "Cannot insert a null piece");}



    long end = Math.addExact(offset,size); // Math.addExact catches integer overflows. java would otherwise just return a sign flipped value.
    long currentLength = getLength();

    if (end > currentLength) {
        pieces.add(new Piece.FillPiece(end-currentLength,(byte)0x00));
    }

    int firstIndex = findPieceIndex(offset);

    if (firstIndex != -1) {
        int firstLocal = findLocalOffset(offset);

        if (firstLocal > 0) {
            splitPiece(firstIndex, firstLocal);
            firstIndex++;
        }
    } else {
        firstIndex = findInsertionIndex(offset);

        if (firstIndex == -1) {
            throw new IndexOutOfBoundsException(
                "Invalid insertion position");
        }
    }

    if (size == 0) {pieces.add(firstIndex,piece); return;} // early exit for size zero inserts

    int secondIndex = findPieceIndex(end);
    int secondLocal = findLocalOffset(end);
    //second split
    if (secondIndex != -1 && secondLocal > 0){splitPiece(secondIndex,secondLocal);} // now two splits have been performed, the first right and second left split encompass the range we are interested in replacing.

    int endIndex = findInsertionIndex(end);

    pieces.subList(firstIndex,endIndex).clear();



    if (piece != null){
    pieces.add(firstIndex,piece);
    }
    return;
    }

    public static void main(String[] args) {

    PieceTable pTable = new PieceTable();

    pTable.replaceRange(0,0,new Piece.FillPiece(32,(byte)0xFF));
    System.out.println(pTable.getPieces().toString());

    pTable.replaceRange(4,8,new Piece.FillPiece(8,(byte)0x20));
    System.out.println(pTable.getPieces().toString());

    pTable.replaceRange(0,26,null);
    System.out.println(pTable.getPieces().toString());

    pTable.replaceRange(3,0,new Piece.FillPiece(10,(byte)0x40));
    System.out.println(pTable.getPieces().toString()+" Final Length: "+pTable.getLength());

    pTable.replaceRange(32,0,new Piece.FillPiece(32,(byte)0x50));
    System.out.println(pTable.getPieces().toString()+" Final Length: "+pTable.getLength());

    pTable.replaceRange(0,64,null);
    System.out.println(pTable.getPieces().toString()+" Final Length: "+pTable.getLength());

    pTable.replaceRange(10,5,new Piece.FillPiece(6,(byte)0xFF));
    System.out.println(pTable.getPieces().toString()+" Final Length: "+pTable.getLength());


    pTable.replaceRange(30,5,new Piece.FillPiece(6,(byte)0xFE));
    System.out.println(pTable.getPieces().toString()+" Final Length: "+pTable.getLength());

    return;
    }

}
