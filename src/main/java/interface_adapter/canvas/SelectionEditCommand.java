package interface_adapter.canvas;

/** An edit of the lasso / box selection: move, resize, recolour or delete. */
public final class SelectionEditCommand extends CanvasEditCommand {
    /** What is done to the selection. */
    public enum Kind {
        MOVE("Move"), RESIZE("Resize"), RECOLOR("Recolour"), DELETE("Delete");

        private final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private final Kind kind;

    public SelectionEditCommand(Kind kind, CanvasMementoOriginator canvas, Runnable edit) {
        super(kind.label, canvas, edit);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
