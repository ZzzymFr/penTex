package view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// a live card on top of a graph slot: screen skips the hidden picture, save / pdf never do
class DrawingCanvasCoveredGraphTest {
    private static final Color PICTURE = new Color(200, 30, 30);

    private final DrawingCanvas canvas = new DrawingCanvas();
    private final Object live = new Object();
    private Rectangle2D card;

    @BeforeEach
    void showOneGraph() {
        canvas.setSize(900, 500);
        canvas.setGridVisible(false);
        BufferedImage red = new BufferedImage(400, 250, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = red.createGraphics();
        g.setColor(PICTURE);
        g.fillRect(0, 0, 400, 250);
        g.dispose();
        canvas.showGraph(red, new Rectangle(50, 200, 100, 40), live);
        card = canvas.getGraphCardOnScreen(live);
    }

    private int middle(boolean screen) {
        BufferedImage out = new BufferedImage(900, 500, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        if (screen) {
            canvas.paint(g);
        } else {
            canvas.paintContent(g);
        }
        g.dispose();
        return out.getRGB((int) card.getCenterX(), (int) card.getCenterY());
    }

    @Test
    void screenSkipsThePictureOnlyWhileCovered() {
        assertEquals(PICTURE.getRGB(), middle(true), "nothing on top yet: picture shows");
        canvas.setGraphCovered(live, true);
        assertNotEquals(PICTURE.getRGB(), middle(true), "live card on top: picture not drawn under it");
        canvas.setGraphCovered(live, false);
        assertEquals(PICTURE.getRGB(), middle(true), "card gone: picture back");
    }

    @Test
    void savingAndPdfAlwaysGetThePicture() {
        canvas.setGraphCovered(live, true);
        assertEquals(PICTURE.getRGB(), middle(false));
    }

    @Test
    void onlyThatGraphIsSkipped() {
        canvas.setGraphCovered(new Object(), true);
        canvas.setGraphCovered(null, true);   // restored pictures have no tag, never covered
        assertEquals(PICTURE.getRGB(), middle(true));
        assertTrue(canvas.isGraphCovered(new Object()) == false);
        canvas.setGraphCovered(live, true);
        assertTrue(canvas.isGraphCovered(live));
        canvas.removeGraph(live);
        canvas.setGraphCovered(live, false);
        assertFalse(canvas.isGraphCovered(live));
    }
}
