package view.drawing;

import entity.InkEditor;
import entity.Stroke;
import view.answer.AnswerInk;
import view.answer.CanvasAnswer;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * The move or resize of the selection that is being dragged right now, and how the selection looks
 * mid-drag. A move follows the pointer in whole world units; a resize scales around the corner
 * opposite the dragged handle, by how far along that diagonal the pointer is (1 = where it started).
 */
public final class SelectionDrag {
    public static final double MIN_RESIZE = 0.1;
    public static final double MAX_RESIZE = 20;

    private Point2D moveStart;     // where a move started (world), or null
    private int dx;
    private int dy;
    private int handle = -1;       // corner being dragged to resize (0 top-left, clockwise), or -1
    private Point2D anchor;        // the opposite corner, which stays put
    private Point2D corner;        // where the dragged corner started
    private double factor = 1;

    /** Starts moving the selection from this world point. */
    public void startMove(Point2D from) {
        moveStart = from;
        dx = 0;
        dy = 0;
    }

    /** Starts resizing by the given handle; {@code anchor} is the corner that stays put. */
    public void startResize(int handle, Point2D corner, Point2D anchor) {
        this.handle = handle;
        this.corner = corner;
        this.anchor = anchor;
        factor = 1;
    }

    /** Follows the pointer (world coordinates) with whichever drag is in progress. */
    public void dragTo(Point2D p) {
        if (isResizing()) {
            double vx = corner.getX() - anchor.getX();
            double vy = corner.getY() - anchor.getY();
            double along = ((p.getX() - anchor.getX()) * vx + (p.getY() - anchor.getY()) * vy) / (vx * vx + vy * vy);
            factor = Math.max(MIN_RESIZE, Math.min(MAX_RESIZE, along));
        } else if (isMoving()) {
            dx = (int) Math.round(p.getX() - moveStart.getX());
            dy = (int) Math.round(p.getY() - moveStart.getY());
        }
    }

    public boolean isMoving() {
        return moveStart != null;
    }

    public boolean isResizing() {
        return handle >= 0;
    }

    /** Mouse is down on the selection, moving or resizing it. */
    public boolean isActive() {
        return isMoving() || isResizing();
    }

    public void endMove() {
        moveStart = null;
        dx = 0;
        dy = 0;
    }

    public void endResize() {
        handle = -1;
        factor = 1;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public double factor() {
        return factor;
    }

    public Point2D anchor() {
        return anchor;
    }

    private boolean resizedNow() {
        return isResizing() && factor != 1;
    }

    private boolean movedNow() {
        return dx != 0 || dy != 0;
    }

    /** A selected stroke as it looks right now. */
    public Stroke preview(Stroke stroke) {
        if (resizedNow()) {
            return InkEditor.scale(stroke, anchor.getX(), anchor.getY(), factor);
        }
        return movedNow() ? InkEditor.translate(stroke, dx, dy) : stroke;
    }

    /** A selected answer as it looks right now. */
    public CanvasAnswer preview(CanvasAnswer answer) {
        if (resizedNow()) {
            return resized(answer, anchor, factor);
        }
        return movedNow() ? answer.translated(dx, dy) : answer;
    }

    /** A selected picture's box as it looks right now. */
    public Rectangle2D preview(Rectangle2D box) {
        if (resizedNow()) {
            return new Rectangle2D.Double(anchor.getX() + (box.getX() - anchor.getX()) * factor,
                    anchor.getY() + (box.getY() - anchor.getY()) * factor,
                    box.getWidth() * factor, box.getHeight() * factor);
        }
        if (movedNow()) {
            return new Rectangle2D.Double(box.getX() + dx, box.getY() + dy, box.getWidth(), box.getHeight());
        }
        return box;
    }

    /** Where a selected picture's box ends up when this drag is let go (never smaller than 8 units). */
    public Rectangle2D committed(Rectangle2D box) {
        if (isResizing()) {
            double x = anchor.getX() + (box.getX() - anchor.getX()) * factor;
            double y = anchor.getY() + (box.getY() - anchor.getY()) * factor;
            return new Rectangle2D.Double(x, y, Math.max(8, box.getWidth() * factor),
                    Math.max(8, box.getHeight() * factor));
        }
        return new Rectangle2D.Double(box.getX() + dx, box.getY() + dy, box.getWidth(), box.getHeight());
    }

    /** An answer scaled around a point; its pen width does not scale, like strokes. */
    public static CanvasAnswer resized(CanvasAnswer a, Point2D anchor, double factor) {
        return AnswerInk.refit(a.scaled(anchor.getX(), anchor.getY(), factor), a.getPenWidth());
    }
}
