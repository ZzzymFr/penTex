package view.drawing;

import interface_adapter.canvas.ViewportState;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * The window onto the infinite canvas: {@code screen = world * zoom + origin}.
 *
 * <p>Only geometry lives here (Single Responsibility); the canvas decides when to repaint and whom
 * to tell. The immutable {@link ViewportState} is what view commands save and restore.
 */
public final class CanvasViewport {
    private ViewportState state = ViewportState.IDENTITY;

    public ViewportState state() {
        return state;
    }

    public void set(ViewportState next) {
        state = java.util.Objects.requireNonNull(next, "next");
    }

    /** 1 = 100%. */
    public double zoom() {
        return state.zoom();
    }

    public double originX() {
        return state.originX();
    }

    public double originY() {
        return state.originY();
    }

    /** Moves the view by (dx, dy) screen pixels. */
    public void panBy(double dx, double dy) {
        state = state.pannedBy(dx, dy);
    }

    public Point2D toWorld(Point2D screen) {
        return new Point2D.Double(state.toWorldX(screen.getX()), state.toWorldY(screen.getY()));
    }

    public Point2D toScreen(Point2D world) {
        return new Point2D.Double(state.toScreenX(world.getX()), state.toScreenY(world.getY()));
    }

    public Rectangle2D toScreen(Rectangle2D world) {
        double zoom = state.zoom();
        return new Rectangle2D.Double(state.toScreenX(world.getX()), state.toScreenY(world.getY()),
                world.getWidth() * zoom, world.getHeight() * zoom);
    }

    /** A length of screen pixels in world units. */
    public double toWorldLength(double screenLength) {
        return screenLength / state.zoom();
    }

    /** Makes {@code g} draw in world coordinates. */
    public void applyTo(Graphics2D g) {
        g.translate(state.originX(), state.originY());
        g.scale(state.zoom(), state.zoom());
    }
}
