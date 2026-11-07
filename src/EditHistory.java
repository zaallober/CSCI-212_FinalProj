import java.util.ArrayList;

import java.util.List;

final public class EditHistory {

    private final List<Edit> edits;
    private int head;

    public EditHistory() {
        this.edits = new ArrayList<>();
        this.head = 0;
    }

    public int getHead() {return this.head;}

    public Edit getEdit(int index) {
        return this.edits.get(index);
    }

    public void appendEdit(Edit edit) {

    while (this.edits.size()>this.head) {this.edits.remove(this.edits.size()-1);}

    this.edits.add(edit);
    this.head++;
    }

    public void undo() {
        if (this.head > 0) {this.head -= 1;}
    }

    public void redo() {
        if (this.head<this.edits.size()) {this.head++;}
    }

}

