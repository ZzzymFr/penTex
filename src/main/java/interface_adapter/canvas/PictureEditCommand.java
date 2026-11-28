package interface_adapter.canvas;

/** Places, edits or removes a picture (an item dragged in from History, or an image). */
public final class PictureEditCommand extends CanvasEditCommand {
    /** What is done to the picture. */
    public enum Kind {
        PLACE("Place picture"), EDIT("Edit picture"), REMOVE("Remove picture");

        private final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private final Kind kind;

    public PictureEditCommand(Kind kind, CanvasMementoOriginator canvas, Runnable edit) {
        super(kind.label, canvas, edit);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
