package interface_adapter.canvas;

/** Removes all ink, answers, pictures and graph cards; undo brings them all back. */
public final class ClearCanvasCommand extends CanvasEditCommand {
    public ClearCanvasCommand(CanvasMementoOriginator canvas, Runnable clear) {
        super("Clear", canvas, clear);
    }
}
