package interface_adapter.canvas;

import use_case.command.Command;

/**
 * Zooms around a screen point. The notches of one wheel / pinch gesture arrive as many commands a
 * few milliseconds apart; they merge into one undo step while they keep coming within
 * {@link #GESTURE_GAP_MILLIS}.
 */
public final class ZoomCommand extends ViewportCommand {
    public static final long GESTURE_GAP_MILLIS = 600;

    private long lastWhen;

    /**
     * @param when the time of the input event, in milliseconds
     */
    public ZoomCommand(ViewportTarget target, double screenX, double screenY, double factor,
                       double minZoom, double maxZoom, long when) {
        super("Zoom", target, target.viewport().zoomedAt(screenX, screenY, factor, minZoom, maxZoom));
        this.lastWhen = when;
    }

    @Override
    public boolean mergeWith(Command next) {
        if (!(next instanceof ZoomCommand zoom) || zoom.target() != target()) {
            return false;
        }
        long gap = zoom.lastWhen - lastWhen;
        if (gap < 0 || gap > GESTURE_GAP_MILLIS) {
            return false;
        }
        extendTo(zoom.after());
        lastWhen = zoom.lastWhen;
        return true;
    }
}
