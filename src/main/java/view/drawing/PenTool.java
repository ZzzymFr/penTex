package view.drawing;

import entity.InkPoint;
import entity.Stroke;

import java.awt.event.MouseEvent;
import java.util.Objects;

/** The pen: every press-drag-release writes one stroke. */
public final class PenTool implements CanvasTool {

    /** What the pen needs from the canvas. */
    public interface Target {
        /** The pointer position as an ink sample in world coordinates. */
        InkPoint worldPoint(MouseEvent e);

        /** Adds a new stroke written with the current pen, starting at {@code first}, as one undo step. */
        void beginStroke(Stroke stroke, InkPoint first);

        /** The stroke is complete: check the formulas it touches and tell whoever listens. */
        void strokeFinished(Stroke stroke);

        void repaint();
    }

    private final Target canvas;
    private Stroke current;  // the stroke being written, or null

    public PenTool(Target canvas) {
        this.canvas = Objects.requireNonNull(canvas, "canvas");
    }

    @Override
    public void press(MouseEvent e) {
        InkPoint first = canvas.worldPoint(e);
        current = new Stroke();
        canvas.beginStroke(current, first);
    }

    @Override
    public void drag(MouseEvent e) {
        if (current != null) {
            current.addPoint(canvas.worldPoint(e));
            canvas.repaint();
        }
    }

    @Override
    public void release(MouseEvent e) {
        finish();
    }

    @Override
    public void releaseWhileInactive() {
        finish();
    }

    private void finish() {
        if (current != null) {
            Stroke done = current;
            current = null;
            canvas.strokeFinished(done);
        }
    }

    /** Another tool was chosen mid-stroke: the stroke is already on the canvas, so finish it properly. */
    @Override
    public void reset() {
        finish();
    }

    /** Stops writing without finishing: the content was replaced (undo, Clear, opening a canvas). */
    public void abandon() {
        current = null;
    }

    /** The stroke being written, or null. */
    public Stroke current() {
        return current;
    }
}
