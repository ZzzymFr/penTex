package view.graph;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * The floating card shell every live plot uses: header drag moves it, corner grip resizes it, ↺ / ×
 * buttons, click vs drag on the plot, wheel, double click home. What the plot does with those is the
 * subclass's call (pan, spin, trace, pin...).
 */
public abstract class PlotCard extends JComponent {
    // where the card sits is the canvas's business, the card just asks
    public interface Host {
        void moveBy(double dx, double dy);

        void resizeTo(double width, double height);

        void close();

        // something the snapshot should show changed (pan, zoom, spin, pin)
        void viewChanged();

        /** Capture source placement before a header drag; copying must not move its source. */
        default void dragStarted() { }

        /** Optional cross-workspace drop handoff after a card header drag. */
        default void dragFinished(Point cursorOnScreen) { }

        /** Cross-workspace handoff is checked while dragging, not only on mouse release. */
        default void dragMoved(Point cursorOnScreen) { }

        /** A workspace may support smaller media than a floating Canvas card. */
        default double minimumWidth() { return MIN_WIDTH; }
        default double minimumHeight() { return MIN_HEIGHT; }
    }

    static final double WHEEL_STEP = 1.12;
    static final double MIN_WIDTH = 240;
    static final double MIN_HEIGHT = 180;
    private static final int CLICK_SLOP = 3;   // pixels a click can wobble and still be a click

    private enum Drag { NONE, MOVE, RESIZE, PLOT }

    private static final Host NO_HOST = new Host() {
        @Override
        public void moveBy(double dx, double dy) {
        }

        @Override
        public void resizeTo(double width, double height) {
        }

        @Override
        public void close() {
        }

        @Override
        public void viewChanged() {
        }
    };

    private Host host = NO_HOST;
    private Drag drag = Drag.NONE;
    private Point pressScreen;
    private Point lastScreen;
    private boolean moved;
    private int hotButton = GraphCardRenderer.NO_BUTTON;
    private boolean dropHighlight;      // a lassoed formula is being dragged over this card

    protected PlotCard() {
        setOpaque(false);
        setVisible(false);
        MouseAdapter mouse = new Mouse();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    public void setHost(Host host) {
        this.host = host == null ? NO_HOST : host;
    }

    protected Host host() {
        return host;
    }

    // what the card looks like right now as a picture, for saving
    public abstract BufferedImage snapshot(int width, int height);

    // same picture but done may come later (slow cards draw it off the edt); done always runs on the edt
    public void snapshotLater(int width, int height, java.util.function.Consumer<BufferedImage> done) {
        done.accept(snapshot(width, height));
    }

    /** Header scale for the card's current size, so floating controls can match the title. */
    public float headerScale() {
        return ui();
    }

    /** Lights the card up while a lassoed formula is dragged over it: let go to add it to the graph. */
    public void setDropHighlight(boolean on) {
        if (dropHighlight != on) {
            dropHighlight = on;
            repaint();
        }
    }

    public boolean isDropHighlighted() {
        return dropHighlight;
    }

    /** The plot under the header, in card coordinates, so floating controls can sit on it. */
    public Rectangle plotBounds() {
        return plot().getBounds();
    }

    // ---- what the plot does, subclass fills these in

    abstract boolean hasContent();

    abstract void paintCard(Graphics2D g, int hotButton);

    // drag on the plot, screen pixels since last time
    abstract void plotDragged(double dx, double dy);

    // plain click, no drag
    abstract void plotClicked(Point p);

    // cursor over the card (may be off the plot)
    abstract void hover(Point p);

    abstract void hoverEnded();

    abstract void wheel(MouseWheelEvent e, double factor);

    // ↺ / double click
    abstract void resetView();

    // cursor over the plot itself
    int plotCursor() {
        return Cursor.CROSSHAIR_CURSOR;
    }

    Rectangle2D plot() {
        return GraphCardRenderer.plotArea(getWidth(), getHeight());
    }

    float ui() {
        return GraphCardRenderer.ui(getWidth());
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (hasContent()) {
            paintCard((Graphics2D) g, hotButton);
            if (dropHighlight) {
                GraphCardRenderer.dropHint((Graphics2D) g, getWidth(), getHeight());
            }
        }
    }

    private int buttonAt(Point p) {
        if (GraphCardRenderer.closeButton(getWidth()).contains(p)) {
            return GraphCardRenderer.CLOSE_BUTTON;
        }
        if (GraphCardRenderer.resetButton(getWidth()).contains(p)) {
            return GraphCardRenderer.RESET_BUTTON;
        }
        return GraphCardRenderer.NO_BUTTON;
    }

    private void cursorFor(Point p) {
        int cursor;
        if (buttonAt(p) != GraphCardRenderer.NO_BUTTON) {
            cursor = Cursor.HAND_CURSOR;
        } else if (GraphCardRenderer.grip(getWidth(), getHeight()).contains(p)) {
            cursor = Cursor.SE_RESIZE_CURSOR;
        } else if (p.y < GraphCardRenderer.headerHeight(getWidth())) {
            cursor = Cursor.MOVE_CURSOR;
        } else {
            cursor = plotCursor();
        }
        setCursor(Cursor.getPredefinedCursor(cursor));
    }

    private final class Mouse extends MouseAdapter {
        private java.awt.event.AWTEventListener outsideCardEvents;
        // A native drag may stop dispatching to a Swing child once that child is
        // clipped at the Canvas/PDF split. Sample the pointer only during a header
        // drag so the preview is independent of the component beneath the cursor.
        private final javax.swing.Timer dragPointer = new javax.swing.Timer(25, event -> followPointer());

        private void followPointer() {
            if (drag != Drag.MOVE) return;
            try {
                java.awt.PointerInfo info = java.awt.MouseInfo.getPointerInfo();
                if (info != null) updateDrag(info.getLocation());
            } catch (java.awt.HeadlessException | SecurityException unavailable) {
                // Local mouse events remain functional when pointer lookup is blocked.
            }
        }

        /** Mouse releases can be delivered to the PDF sibling instead of the clipped Canvas. */
        private void watchCrossComponentRelease() {
            if (!PlotCard.this.isShowing()) return;
            if (!dragPointer.isRunning()) dragPointer.start();
            if (outsideCardEvents != null) return;
            java.awt.Window owner = SwingUtilities.getWindowAncestor(PlotCard.this);
            if (owner == null) return;
            outsideCardEvents = event -> {
                if (event instanceof MouseEvent mouse && mouse.getSource() instanceof java.awt.Component source
                        && source != PlotCard.this && SwingUtilities.getWindowAncestor(source) == owner) {
                    if (mouse.getID() == MouseEvent.MOUSE_DRAGGED
                            && (mouse.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0) {
                        updateDrag(mouse.getLocationOnScreen());
                    } else if (mouse.getID() == MouseEvent.MOUSE_RELEASED
                            && SwingUtilities.isLeftMouseButton(mouse)) {
                        finishDrag(mouse.getLocationOnScreen(), null);
                    }
                } else if (event instanceof java.awt.event.WindowEvent window
                        && window.getWindow() == owner
                        && (window.getID() == java.awt.event.WindowEvent.WINDOW_DEACTIVATED
                            || window.getID() == java.awt.event.WindowEvent.WINDOW_LOST_FOCUS)) {
                    cancelDrag();
                }
            };
            try {
                java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(outsideCardEvents,
                        java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK | java.awt.AWTEvent.MOUSE_EVENT_MASK
                                | java.awt.AWTEvent.WINDOW_EVENT_MASK | java.awt.AWTEvent.WINDOW_FOCUS_EVENT_MASK);
            } catch (SecurityException denied) {
                outsideCardEvents = null; // local mouseDragged/mouseReleased still work
            }
        }

        private void stopWatching() {
            dragPointer.stop();
            if (outsideCardEvents != null) {
                java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(outsideCardEvents);
                outsideCardEvents = null;
            }
        }

        private void cancelDrag() {
            boolean moving = drag == Drag.MOVE;
            drag = Drag.NONE;
            stopWatching();
            if (moving) host.dragFinished(null); // discard outline, never commit
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            Point p = e.getPoint();
            pressScreen = e.getLocationOnScreen();
            lastScreen = pressScreen;
            moved = false;
            int button = buttonAt(p);
            if (button == GraphCardRenderer.CLOSE_BUTTON) {
                drag = Drag.NONE;
                host.close();
            } else if (button == GraphCardRenderer.RESET_BUTTON) {
                drag = Drag.NONE;
                resetView();
            } else if (GraphCardRenderer.grip(getWidth(), getHeight()).contains(p)) {
                drag = Drag.RESIZE;
            } else if (p.y < GraphCardRenderer.headerHeight(getWidth())) {
                drag = Drag.MOVE;
                host.dragStarted();
                watchCrossComponentRelease();
            } else {
                drag = Drag.PLOT;
                hoverEnded();   // no readout flapping around mid drag
            }
        }

        private void updateDrag(Point now) {
            if (drag == Drag.NONE || lastScreen == null) return;
            double dx = now.x - lastScreen.x;
            double dy = now.y - lastScreen.y;
            if (dx == 0 && dy == 0) return;
            if (!moved && pressScreen.distance(now) < CLICK_SLOP) return;
            moved = true;
            lastScreen = now;
            switch (drag) {
                case MOVE:
                    host.moveBy(dx, dy);
                    host.dragMoved(now);
                    break;
                case RESIZE:
                    host.resizeTo(Math.max(host.minimumWidth(), getWidth() + dx),
                            Math.max(host.minimumHeight(), getHeight() + dy));
                    break;
                default:
                    plotDragged(dx, dy);
                    break;
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) { updateDrag(e.getLocationOnScreen()); }

        private void finishDrag(Point releasedAt, Point local) {
            Drag was = drag;
            drag = Drag.NONE;
            stopWatching();
            if (was == Drag.NONE) return;
            if (moved) {
                host.viewChanged();
                if (was == Drag.MOVE) host.dragFinished(releasedAt);
            } else if (was == Drag.PLOT) {
                if (local != null) plotClicked(local);
            }
            if (was == Drag.PLOT && local != null) hover(local);
        }

        @Override
        public void mouseReleased(MouseEvent e) { finishDrag(e.getLocationOnScreen(), e.getPoint()); }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && plot().contains(e.getPoint())) {
                resetView();
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            Point p = e.getPoint();
            int hot = buttonAt(p);
            if (hot != hotButton) {
                hotButton = hot;
                repaint();
            }
            cursorFor(p);
            hover(p);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            if (hotButton != GraphCardRenderer.NO_BUTTON) {
                hotButton = GraphCardRenderer.NO_BUTTON;
                repaint();
            }
            if (drag == Drag.NONE) {
                hoverEnded();
            }
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            if (!hasContent() || !plot().contains(e.getPoint())) {
                return;
            }
            wheel(e, Math.pow(WHEEL_STEP, e.getPreciseWheelRotation()));
            hover(e.getPoint());
            host.viewChanged();
        }
    }
}
