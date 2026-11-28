package interface_adapter.canvas;

/** Back to 100% with the ink centred; undo returns to the previous zoom and position. */
public final class ResetViewCommand extends ViewportCommand {
    public ResetViewCommand(ViewportTarget target, ViewportState reset) {
        super("Reset view", target, reset);
    }
}
