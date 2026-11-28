package view.drawing;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.List;
import java.util.Objects;

/**
 * The canvas' one mouse listener. It handles what every tool shares: a graph card's close
 * button, panning (right or middle drag, or a left drag with Space held), the wheel (scroll,
 * Shift for sideways, Ctrl to zoom around the pointer) and hover feedback. It hands everything
 * else to the active {@link CanvasTool}.
 */
public final class CanvasInput extends MouseAdapter {
    private static final double SCROLL_STEP = 48;    // screen pixels per wheel notch
    private static final double ZOOM_STEP = 1.1;     // zoom factor per wheel notch

    /** What the router needs from the canvas. */
    public interface Target {
        /** So Delete, Ctrl+Z and Space reach the canvas. */
        void focus();

        /** The tool chosen now. */
        CanvasTool activeTool();

        /** The user started doing something with a tool. */
        void userActed();

        /** The topmost graph card whose close button is at this screen point, or null. */
        GraphSlot closeButtonAt(Point screen);

        void closeGraph(GraphSlot card);

        /** The card whose close button is highlighted, or null. */
        GraphSlot hoveredClose();

        void setHoveredClose(GraphSlot card);

        void panBy(double dx, double dy);

        /** Zooms around a screen point; {@code when} groups one wheel gesture into one undo step. */
        void zoomAt(double screenX, double screenY, double factor, long when);

        void setCursor(int cursorType);

        void repaint();
    }

    private final Target canvas;
    private final List<CanvasTool> tools;
    private Point panFrom;       // last mouse position of a pan drag; null when not panning
    private boolean spaceDown;   // Space held: left drag pans

    /**
     * @param tools every tool, so each hears the button come up even when another one is active
     */
    public CanvasInput(Target canvas, List<CanvasTool> tools) {
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.tools = List.copyOf(tools);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        canvas.focus();
        GraphSlot closing = e.getButton() == MouseEvent.BUTTON1 ? canvas.closeButtonAt(e.getPoint()) : null;
        if (closing != null) {
            canvas.closeGraph(closing);
            return;
        }
        if (spaceDown || e.getButton() != MouseEvent.BUTTON1) {
            panFrom = e.getPoint();
            canvas.setCursor(Cursor.MOVE_CURSOR);
            return;
        }
        canvas.userActed();
        canvas.activeTool().press(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (panFrom != null) {
            canvas.panBy(e.getX() - panFrom.x, e.getY() - panFrom.y);
            panFrom = e.getPoint();
            return;
        }
        canvas.activeTool().drag(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (panFrom != null) {
            panFrom = null;
            canvas.setCursor(spaceDown ? Cursor.MOVE_CURSOR : Cursor.CROSSHAIR_CURSOR);
            return;
        }
        CanvasTool active = canvas.activeTool();
        for (CanvasTool tool : tools) {
            if (tool != active) {
                tool.releaseWhileInactive();
            }
        }
        active.release(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        GraphSlot closeHit = canvas.closeButtonAt(e.getPoint());
        boolean onClose = closeHit != null;
        if (closeHit != canvas.hoveredClose()) {
            canvas.setHoveredClose(closeHit);
            canvas.setCursor(onClose ? Cursor.HAND_CURSOR : Cursor.CROSSHAIR_CURSOR);
            canvas.repaint();
        }
        if (onClose) {
            return;
        }
        canvas.activeTool().hover(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        for (CanvasTool tool : tools) {
            tool.pointerLeft();
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double notches = e.getPreciseWheelRotation();
        if (e.isControlDown()) {
            canvas.zoomAt(e.getX(), e.getY(), Math.pow(ZOOM_STEP, -notches), e.getWhen());
        } else if (e.isShiftDown()) {
            canvas.panBy(-notches * SCROLL_STEP, 0);
        } else {
            canvas.panBy(0, -notches * SCROLL_STEP);
        }
    }

    /** Space pressed or released: a left drag pans while it is held. */
    public void setSpaceDown(boolean down) {
        spaceDown = down;
        if (panFrom == null) {
            canvas.setCursor(down ? Cursor.MOVE_CURSOR : Cursor.CROSSHAIR_CURSOR);
        }
    }

    public boolean isSpaceDown() {
        return spaceDown;
    }

    /** A pan drag is in progress. */
    public boolean isPanning() {
        return panFrom != null;
    }
}
