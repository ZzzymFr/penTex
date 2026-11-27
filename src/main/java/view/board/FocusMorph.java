package view.board;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Picture-only bridge into / out of PDF focus mode: the PDF grows out of its split spot and shrinks
 * back into it. The live layout is already final underneath, this just covers it for ~0.3s.
 */
final class FocusMorph extends JComponent {
    private static final Motion.Feel FEEL = new Motion.Feel(1f, 0.28f);

    private final Spring progress = new Spring(0.002f, true);
    private final Motion.Clock clock = new Motion.Clock();
    private final Timer timer = new FrameTimer(e -> step());
    private BufferedImage before;
    private BufferedImage after;
    private Rectangle from = new Rectangle();
    private Rectangle to = new Rectangle();
    private boolean capturing;

    FocusMorph() {
        setVisible(false);
    }

    // see-through while the board paints itself for a snapshot, else it hides what's under it
    @Override public boolean isOpaque() {
        return !capturing && before != null;
    }

    // never takes clicks, the real UI under it is already live
    @Override public boolean contains(int x, int y) {
        return false;
    }

    void setCapturing(boolean on) {
        capturing = on;
    }

    /** Where the PDF is drawn right now, so a second toggle starts from here. */
    Rectangle liveRect() {
        return after == null ? new Rectangle(from) : lerp(from, to, amount());
    }

    /** Freezes on the old look until land() has the new one. */
    void begin(BufferedImage before, Rectangle from) {
        timer.stop();
        clock.reset();
        this.before = before;
        this.after = null;
        this.from = new Rectangle(from);
        progress.snap(0f);
        setVisible(true);
        repaint();
    }

    void land(BufferedImage after, Rectangle to) {
        if (before == null) return;
        this.after = after;
        this.to = new Rectangle(to);
        clock.reset();
        timer.start();
        repaint();
    }

    void cancel() {
        timer.stop();
        clock.reset();
        before = null;
        after = null;
        setVisible(false);
        java.awt.Container parent = getParent();
        if (parent != null) {
            parent.remove(this);
            parent.repaint();
        }
    }

    private void step() {
        boolean done = progress.step(1f, FEEL, clock.tick());
        repaint();
        if (done) cancel();
    }

    private float amount() {
        return Math.max(0f, Math.min(1f, progress.x));
    }

    @Override protected void paintComponent(Graphics g) {
        if (capturing || before == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            int w = getWidth();
            int h = getHeight();
            if (after == null) {
                g2.drawImage(before, 0, 0, w, h, null);
                return;
            }
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            float p = amount();
            // the scene around it cross-fades
            g2.drawImage(after, 0, 0, w, h, null);
            g2.setComposite(AlphaComposite.SrcOver.derive(1f - p));
            g2.drawImage(before, 0, 0, w, h, null);
            // the PDF travels; reduce motion: no travel, fade only
            Rectangle r = Motion.reduced() ? to : lerp(from, to, p);
            g2.setComposite(AlphaComposite.SrcOver);
            part(g2, after, to, r, w);
            g2.setComposite(AlphaComposite.SrcOver.derive(1f - p));
            part(g2, before, from, r, w);
        } finally {
            g2.dispose();
        }
    }

    // src is in board coords; the image may be 2x on retina / 1.25x etc on windows
    private static void part(Graphics2D g, BufferedImage img, Rectangle src, Rectangle dst, int boardWidth) {
        if (src.isEmpty() || dst.isEmpty() || boardWidth <= 0) return;
        double s = img.getWidth() / (double) boardWidth;
        g.drawImage(img, dst.x, dst.y, dst.x + dst.width, dst.y + dst.height,
                (int) Math.floor(src.x * s), (int) Math.floor(src.y * s),
                (int) Math.ceil((src.x + src.width) * s), (int) Math.ceil((src.y + src.height) * s), null);
    }

    private static Rectangle lerp(Rectangle a, Rectangle b, float t) {
        return new Rectangle(
                Math.round(a.x + (b.x - a.x) * t),
                Math.round(a.y + (b.y - a.y) * t),
                Math.round(a.width + (b.width - a.width) * t),
                Math.round(a.height + (b.height - a.height) * t));
    }
}
