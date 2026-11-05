# Final CSCI-212 Project Design Brief
#### operation based hex editor

This is all a very loose design brief. I threw together last night and this morning.

## Components

### 1.0 - GUI

Accept user input and coordinate it with calls to the EditorEngine's API

    1. File menu - New, Load, Save, Save As.
    2. Edit menu - Undo, Redo (?), Insert, Delete.
    3. Preview GUI - Displays current frame: Pg up/down for file traversal, arrow keys or mouse (?) for cursor movement.
    
    Very loose statement, but ideally it would have an entry field similar to that of notepad's or any other text editor, 

### 2.0 - PreviewBuffer

Where the current preview is stored, this is public facing and stores the result of generatePreview()

### 2.1 - FileBuffer

Private byte buffer on the heap streamed from the target file, not gui facing.

### 2.2 - EditorEngine
    
    1. Accept API calls
    2. Stash edits to the edit history
    3. Generate a preview buffer for the gui
    4. Materialize the edits to the final saved file
    
#### 2.2.1 Edit History

The edit history is the one source of truth in the program, and stores exclusively operations performed on the filebuffer.

Because edits are not applied to the original file until we tell the program to save them, we can maintain a head on a buffer of edits/operations, and abuse that fact to perform strictly chronological undo/redo.

1.) Write: Simple byte offset and replacement values would suffice, capable of writing multiple values starting at the offset, consuming each entry of its contents until it is done. 

##### WRITE, integer offset, char contents[]

2.) INSERT: Simple byte offset and byte length would suffice. Zero-fill by default.

##### INSERT, integer offset, integer length, char contents[] 

3.) DELETE: Simple byte offset and byte length would suffice.

##### DELETE, integer offset, integer length


Through enums, on the heap this could be simplified down to an AoS to be applied in chronological order. In human readable text, they may look like this.
    
    WRITE,1024,[0xFF]
    INSERT,1024,64
    WRITE,1025,[0d1]
    ...
    WRITE,1088,[0d64]
    INSERT,0,8
    WRITE,1016,[0xFE] #where 1024 now resides

The primary technical debt in this project is developing an engine that consumes the file buffer and correctly reproduces the edits for both the GUI preview, as well as streaming the output.

Which, fun fact, this will involve writing an interpreter.

## Implimentation

* The smallest logical size of a file is zero. **EOF** is strictly a stream terminator.
* Any loaded file is represented by a byte sequence.

### Writing

* All real number offsets (int n ≥ 0) are ALWAYS valid. Extend the byte sequence if necessary.
* Furthermore, all writes are ALWAYS valid. Extend the byte sequence if necessary.
* Any consequently created undefined space is initialized with a provided value, eg. 0x00 or 0xFF.

### Insertion

* Insertion inherits all of Writing's behavior
* Insertion into a byte sequence can be expressed as shifting all elements' indices by n away from zero.
* Insertion at offset *k* with size *n* shifts every existing element at index *i* ≥ *k* to index *i* + *n*
* Insertion ***never*** overwrites existing data.
* Insert at offset 0 ***always*** inserts at the beginning boundary of the file to handle EOF only stream.
* Insert at offset=length ***always*** inserts at the ending boundary of the file.

### Deletion

* Deletion in a byte sequence may delete infinitely many elements, but ***always*** stops at **EOF**
* Deletion in a byte sequence can be expressed as shifting all elements after the deleted region by the amount of elements deleted.
* The resulting sequence is truncated by the number of elements deleted.
* The number of elements deleted is defined as the minimum of the requested size and the number of elements remaining between the offset and EOF. Expressed mathematically below.

    d=min(n,max(0,L−k))

### misc

* Committing edits will utilize the RandomAccessFile class, since the edit history uses current file offsets, this will essentially stream the preview into the temporary save file. 
* Insertion will require a handler to:
    1. Extend the file by the provided amount.
    2. Stream all preceding data to its new offset by iterating backwards over the file, moving the data towards the end of the file up until the desired seek location.
    3. Write anything present in contents[].
    4. Zero-fill any bytes not defined in contents[].

* public byte[] generatePreview will return a raw byte array 1 KiB in size. GeneratePreview should be called with every edit made to refresh what is visible in the GUI. This is *just* a suggestion regarding implementation:

* Private materializeEdits() will create a new temporary file, apply all edits, then move it to the chosen destination

* public hexUndo/hexRedo will walk the head of the edit history back and forward along its list respectively, iff a new edit is performed, the list will be resized to remove all edits that occured after the current head location, after which the next edit will be appended.

## Prototype API
File handling

- public int hexSaveAs(string filename) *Save can be expressed as SaveAs current loaded file*
- public int hexLoad(string filename)

GUI Function

- public byte[] generatePreview(int offset, int size) *default to 1KiB for size*

Editing Calls

- public void hexWrite(long offset, byte[] contents)
- public void hexInsert(long offset, long size, char fill) *zeroFill default to 0x00*
- public void hexDelete(long offset, long size)
- public void hexUndo(void)
- public void hexRedo(void)

