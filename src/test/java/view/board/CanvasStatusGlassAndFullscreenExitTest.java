package view.board;

import interface_adapter.history.HistoryViewModel;
import org.junit.jupiter.api.Test;
import view.DrawingCanvas;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class CanvasStatusGlassAndFullscreenExitTest {
    @Test void statusGlassSamplesBackdropWithoutWhiteOpaquePlate() {
        var chip = new CanvasStatusChip();
        chip.setSize(165, 44);
        chip.setGlassBackdrop(g -> {
            g.setColor(new Color(30, 70, 120));
            g.fillRect(0, 0, 300, 100);
        }, () -> 1L);
        var image = new BufferedImage(165, 44, BufferedImage.TYPE_INT_ARGB);
        chip.paint(image.getGraphics());
        assertEquals(0, (image.getRGB(0, 0) >>> 24) & 0xff);
        int center = image.getRGB(120, 21);
        assertTrue(((center >>> 16) & 0xff) < 240, "Glass must show the scene, not paint a white plate");
    }

    @Test void fullscreenShowsTopRightGlassExitAndHidesItAfterExit() {
        var pdf = new JPanel();
        var board = new BoardWindow(new JPanel(), new DrawingCanvas(),
                host -> new HistoryPanel(new HistoryViewModel(), host), pdf);
        board.setSize(1150, 700);
        board.setPdfFullscreen(true);
        board.doLayout();
        GlassBar exit = null;
        for (var component : board.getComponents()) {
            if (component instanceof GlassBar glass &&
                    "Exit PDF fullscreen (Esc)".equals(glass.getToolTipText())) exit = glass;
        }
        assertNotNull(exit);
        assertTrue(exit.isVisible());
        assertTrue(exit.getX() > 900);
        assertTrue(exit.getY() < 50);
        board.setPdfFullscreen(false);
        board.doLayout();
        assertFalse(exit.isVisible());
    }
}
