package view.drawing;

import entity.InkPoint;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The lasso and the box: draw a loop (or drag a rectangle) to pick ink, answers and pictures;
 * drag the picked items to move them, drag a corner handle to resize them, or drop handwriting
 * onto a graph card.
 */
public final class SelectionTool implements CanvasTool {
    private static final int[] HANDLE_CURSORS = {Cursor.NW_RESIZE_CURSOR, Cursor.NE_RESIZE_CURSOR,
        Cursor.SE_RESIZE_CURSOR, Cursor.SW_RESIZE_CURSOR};

    /** What the selection tool needs from the canvas. */
    public interface Target {
        Point2D toWorld(Point screen);

        InkPoint worldPoint(MouseEvent e);

        /** True for the box tool, false for the free lasso. */
        boolean isRectangle();

        /** Space is held, so a left drag pans instead. */
        boolean panKeyDown();

        /** The selection's outline in world coordinates (a drag in progress included), or null. */
        Rectangle selectionBounds();

        /** The selection corner handle under this screen point, or -1. */
        int handleAt(Point screen);

        void clearSelection();

        /** Picks the topmost picture at this world point for moving; false if there is none. */
        boolean pickPictureAt(Point2D world);

        /** The picked items are being dragged over this screen point (a graph card may light up). */
        void draggedOver(Point screen);

        /** The graph card the picked ink is over, or null. */
        Object dropTarget();

        /** Lets go of the graph card under the drag, telling whoever lit it up. */
        void clearDropTarget();

        /** The picked ink was dropped onto this graph card. */
        void droppedOnGraph(Object target);

        /** Moves the picked items, as one undo step. */
        void moveSelection(int dx, int dy);

        /** Resizes the picked items around the anchor, as one undo step. */
        void resizeSelection(Point2D anchor, double factor);

        /** Picks everything inside the loop (world coordinates). */
        void pickInside(List<InkPoint> loop);

        /** A fresh pick is complete: tell whoever listens if it holds ink. */
        void announcePick();

        void setCursor(int cursorType);

        void repaint();
    }

    private final Target canvas;
    private final SelectionDrag drag;
    private List<InkPoint> lassoPath;  // the loop being drawn (world), or null
    private Point2D rectangleStart;

    /**
     * @param drag the move / resize state the canvas also draws from
     */
    public SelectionTool(Target canvas, SelectionDrag drag) {
        this.canvas = Objects.requireNonNull(canvas, "canvas");
        this.drag = Objects.requireNonNull(drag, "drag");
    }

    @Override
    public void press(MouseEvent e) {
        Rectangle bounds = canvas.selectionBounds();
        Point2D p = canvas.toWorld(e.getPoint());
        int handle = canvas.handleAt(e.getPoint());
        if (handle >= 0) {
            drag.startResize(handle, CanvasPainter.corner(bounds, handle), CanvasPainter.corner(bounds, (handle + 2) % 4));
            return;
        }
        if (bounds != null && bounds.contains(p)) {
            drag.startMove(p);
            return;
        }
        canvas.clearSelection();
        // A click on a previously imported History object selects it for move/resize.
        if (canvas.pickPictureAt(p)) {
            drag.startMove(p);
            return;
        }
        lassoPath = new ArrayList<>();
        lassoPath.add(canvas.worldPoint(e));
        rectangleStart = canvas.isRectangle() ? p : null;
    }

    @Override
    public void drag(MouseEvent e) {
        if (drag.isResizing()) {
            drag.dragTo(canvas.toWorld(e.getPoint()));
        } else if (drag.isMoving()) {
            drag.dragTo(canvas.toWorld(e.getPoint()));
            canvas.draggedOver(e.getPoint());
        } else if (lassoPath != null) {
            if (canvas.isRectangle() && rectangleStart != null) {
                Point2D end = canvas.toWorld(e.getPoint());
                int left = (int) Math.min(rectangleStart.getX(), end.getX());
                int right = (int) Math.max(rectangleStart.getX(), end.getX());
                int top = (int) Math.min(rectangleStart.getY(), end.getY());
                int bottom = (int) Math.max(rectangleStart.getY(), end.getY());
                long when = System.currentTimeMillis();
                lassoPath = new ArrayList<>(List.of(
                        new InkPoint(left, top, when), new InkPoint(right, top, when),
                        new InkPoint(right, bottom, when), new InkPoint(left, bottom, when)));
            } else {
                lassoPath.add(canvas.worldPoint(e));
            }
        }
        canvas.repaint();
    }

    @Override
    public void release(MouseEvent e) {
        if (drag.isResizing()) {
            if (drag.factor() != 1) {
                canvas.resizeSelection(drag.anchor(), drag.factor());
            }
            drag.endResize();
        } else if (drag.isMoving() && canvas.dropTarget() != null) {
            // dragged onto a graph card: the formula goes into that graph, the ink stays where it was
            Object target = canvas.dropTarget();
            canvas.clearDropTarget();
            drag.endMove();
            canvas.droppedOnGraph(target);
        } else if (drag.isMoving()) {
            if (drag.dx() != 0 || drag.dy() != 0) {
                canvas.moveSelection(drag.dx(), drag.dy());
            }
            drag.endMove();
        } else if (lassoPath != null) {
            canvas.pickInside(lassoPath);
            lassoPath = null;
            rectangleStart = null;
            canvas.announcePick();
        }
        canvas.repaint();
    }

    @Override
    public void hover(MouseEvent e) {
        if (canvas.panKeyDown()) {
            return;
        }
        int handle = canvas.handleAt(e.getPoint());
        Rectangle bounds = canvas.selectionBounds();
        boolean overSelection = bounds != null && bounds.contains(canvas.toWorld(e.getPoint()));
        int cursor = handle >= 0 ? HANDLE_CURSORS[handle]
                : overSelection ? Cursor.MOVE_CURSOR : Cursor.CROSSHAIR_CURSOR;
        canvas.setCursor(cursor);
    }

    /** Another tool was chosen: drop the loop and any move or resize still being dragged. */
    @Override
    public void reset() {
        lassoPath = null;
        rectangleStart = null;
        drag.endMove();
        drag.endResize();
    }

    /** The loop being drawn (world coordinates), or null. */
    public List<InkPoint> lassoPath() {
        return lassoPath;
    }
}
