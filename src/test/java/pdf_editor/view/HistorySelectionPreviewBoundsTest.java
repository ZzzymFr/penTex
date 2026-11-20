package pdf_editor.view;

import org.junit.jupiter.api.Test;
import view.DrawingCanvas;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression: History artwork and selection handles must preview the same drag geometry. */
class HistorySelectionPreviewBoundsTest {
    @Test
    void aDraggedHistoryPictureKeepsItsOutlineUntilMouseRelease() throws Exception {
        DrawingCanvas canvas = canvas();
        add(canvas, 100, 100);
        Rectangle before = bounds(canvas);

        mouse(canvas, MouseEvent.MOUSE_PRESSED, 130, 115);
        mouse(canvas, MouseEvent.MOUSE_DRAGGED, 170, 145);
        Rectangle moving = bounds(canvas);

        assertEquals(before.x + 40, moving.x);
        assertEquals(before.y + 30, moving.y);
        assertEquals(before.width, moving.width);
        assertEquals(before.height, moving.height);

        mouse(canvas, MouseEvent.MOUSE_RELEASED, 170, 145);
        assertEquals(moving, bounds(canvas), "The selection must not jump when dropped");
    }

    @Test
    void aGroupOfHistoryPicturesKeepsOneMovingOutline() throws Exception {
        DrawingCanvas canvas = canvas();
        add(canvas, 100, 100);
        add(canvas, 240, 125);
        drag(canvas, 75, 75, 390, 240); // Select both imported objects
        assertEquals(2, canvas.getSelectedPictureCount());
        Rectangle before = bounds(canvas);

        mouse(canvas, MouseEvent.MOUSE_PRESSED, 195, 115);
        mouse(canvas, MouseEvent.MOUSE_DRAGGED, 215, 135);
        Rectangle moving = bounds(canvas);

        assertEquals(before.x + 20, moving.x);
        assertEquals(before.y + 20, moving.y);
        assertEquals(before.width, moving.width);
        assertEquals(before.height, moving.height);

        mouse(canvas, MouseEvent.MOUSE_RELEASED, 215, 135);
        assertEquals(moving, bounds(canvas));
    }

    @Test
    void historyOutlineTracksResizePreviewAndCommittedGeometry() throws Exception {
        DrawingCanvas canvas = canvas();
        add(canvas, 100, 100);
        Rectangle before = bounds(canvas);
        int right = before.x + before.width;
        int bottom = before.y + before.height;

        mouse(canvas, MouseEvent.MOUSE_PRESSED, right, bottom);
        mouse(canvas, MouseEvent.MOUSE_DRAGGED, right + 30, bottom + 20);
        Rectangle resizing = bounds(canvas);

        assertTrue(resizing.width > before.width);
        assertTrue(resizing.height > before.height);
        mouse(canvas, MouseEvent.MOUSE_RELEASED, right + 30, bottom + 20);
        assertEquals(resizing, bounds(canvas), "The resize handles must not jump on release");
    }

    private static DrawingCanvas canvas() {
        DrawingCanvas canvas = new DrawingCanvas();
        canvas.setSize(800, 600);
        canvas.setTool(DrawingCanvas.Tool.RECTANGLE);
        return canvas;
    }

    private static void add(DrawingCanvas canvas, int x, int y) {
        canvas.addHistoryAsset(new BufferedImage(160, 80, BufferedImage.TYPE_INT_ARGB),
                "f(x)", "2", new Point(x, y));
    }

    private static Rectangle bounds(DrawingCanvas canvas) throws Exception {
        Method method = DrawingCanvas.class.getDeclaredMethod("selectionBounds");
        method.setAccessible(true);
        return (Rectangle) method.invoke(canvas);
    }

    private static void drag(DrawingCanvas canvas, int x1, int y1, int x2, int y2) {
        mouse(canvas, MouseEvent.MOUSE_PRESSED, x1, y1);
        mouse(canvas, MouseEvent.MOUSE_DRAGGED, x2, y2);
        mouse(canvas, MouseEvent.MOUSE_RELEASED, x2, y2);
    }

    private static void mouse(DrawingCanvas canvas, int id, int x, int y) {
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        canvas.dispatchEvent(new MouseEvent(canvas, id, System.currentTimeMillis(),
                0, x, y, 1, false, button));
    }
}
