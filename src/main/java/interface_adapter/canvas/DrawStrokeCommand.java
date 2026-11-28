package interface_adapter.canvas;

/** Draws one pen stroke; the stroke keeps growing while the pen moves, all in this one undo step. */
public final class DrawStrokeCommand extends CanvasEditCommand {
    public DrawStrokeCommand(CanvasMementoOriginator canvas, Runnable beginStroke) {
        super("Draw", canvas, beginStroke);
    }
}
