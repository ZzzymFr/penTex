package interface_adapter.canvas;

/** One eraser drag: everything rubbed out until the button is released is a single undo step. */
public final class EraseCommand extends CanvasEditCommand {
    public EraseCommand(CanvasMementoOriginator canvas, Runnable erase) {
        super("Erase", canvas, erase);
    }
}
