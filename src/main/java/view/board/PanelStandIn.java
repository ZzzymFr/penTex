package view.board;

import javax.swing.JComponent;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.Graphics2D;
import java.awt.Transparency;
import java.awt.image.BufferedImage;

/**
 * Sits right over a side panel while it slides: one picture of the panel, moved each frame, so the
 * History rows / PDF editor aren't repainted 20 times just to change their x. Opaque, so Swing
 * skips the real panel under it. Clicks still go to the real panel.
 */
final class PanelStandIn extends JComponent {
    private BufferedImage picture;

    PanelStandIn() {
        setVisible(false);
    }

    boolean holds() {
        return picture != null;
    }

    /** Pictures the panel at its current (full, sliding) size. */
    void take(JComponent panel) {
        int w = panel.getWidth();
        int h = panel.getHeight();
        if (w <= 0 || h <= 0) return;
        panel.validate();
        GraphicsConfiguration gc = panel.getGraphicsConfiguration();
        double scale = gc == null ? 1 : Math.max(1, gc.getDefaultTransform().getScaleX());
        int iw = (int) Math.ceil(w * scale);
        int ih = (int) Math.ceil(h * scale);
        BufferedImage img = gc == null ? new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB)
                : gc.createCompatibleImage(iw, ih, Transparency.OPAQUE);
        Graphics2D g = img.createGraphics();
        try {
            g.scale(iw / (double) w, ih / (double) h);
            g.setColor(BoardTheme.CANVAS);  // History is see-through; under it is the board colour
            g.fillRect(0, 0, w, h);
            panel.paint(g);
        } finally {
            g.dispose();
        }
        picture = img;
    }

    void follow(JComponent panel) {
        if (picture == null) return;
        setBounds(panel.getBounds());
        setVisible(panel.isVisible());
    }

    void drop() {
        picture = null;
        setVisible(false);
    }

    @Override public boolean isOpaque() {
        return picture != null;
    }

    @Override public boolean contains(int x, int y) {
        return false;
    }

    @Override protected void paintComponent(Graphics g) {
        if (picture != null) g.drawImage(picture, 0, 0, getWidth(), getHeight(), null);
    }
}
