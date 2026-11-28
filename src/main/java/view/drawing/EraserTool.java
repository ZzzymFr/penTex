package view.drawing;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Objects;

/**
 * The eraser: rubs along the pointer's path while the button is down. Everything rubbed out in
 * one drag is a single undo step. Clicking a picture removes that picture instead.
 */
public final class EraserTool implements CanvasTool {

    /** What the eraser needs from the canvas. */
    public interface Target {
        Point2D toWorld(Point screen);

        /** Removes the topmost picture under this screen point; false if there is none. */
        boolean erasePictureAt(Point screen);

        /**
         * Erases along the segment (world coordinates).
         *
         * @param newUndoStep whether a change now starts this drag's undo step
         * @return whether anything was erased
         */
        boolean eraseBetween(Point2D from, Point2D to, boolean newUndoStep);

        void repaint();
    }

    private final Target canvas;
    private Point outlineAt;       // where the eraser outline is drawn (screen); null when the mouse is away
    private Point2D last;          // previous drag position (world), to erase along the path
    private boolean stepRecorded;  // one undo step per eraser drag

    public EraserTool(Target canvas) {
        this.canvas = Objects.requireNonNull(canvas, "canvas");
    }

    @Override
    public void press(MouseEvent e) {
        if (canvas.erasePictureAt(e.getPoint())) {
            return;
        }
        stepRecorded = false;
        last = canvas.toWorld(e.getPoint());
        rubTo(canvas.toWorld(e.getPoint()));
    }

    @Override
    public void drag(MouseEvent e) {
        outlineAt = e.getPoint();
        rubTo(canvas.toWorld(e.getPoint()));
    }

    private void rubTo(Point2D to) {
        Point2D from = last == null ? to : last;
        if (canvas.eraseBetween(from, to, !stepRecorded)) {
            stepRecorded = true;  // the rest of this drag belongs to the same undo step
        }
        last = to;
        canvas.repaint();
    }

    @Override
    public void release(MouseEvent e) {
        last = null;
    }

    @Override
    public void releaseWhileInactive() {
        last = null;
    }

    @Override
    public void hover(MouseEvent e) {
        outlineAt = e.getPoint();
        canvas.repaint();
    }

    @Override
    public void pointerLeft() {
        if (outlineAt != null) {
            outlineAt = null;
            canvas.repaint();
        }
    }

    @Override
    public void reset() {
        outlineAt = null;
    }

    /** Where to draw the eraser outline (screen), or null. */
    public Point outlineAt() {
        return outlineAt;
    }

    /** The button is down on the canvas with the eraser. */
    public boolean isErasing() {
        return last != null;
    }
}
