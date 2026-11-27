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
 * back into it. The live layout is already final underneath, this just covers it for a short transition.
 */
final class FocusMorph extends JComponent {
    private static final long DURATION_NANOS = 240_000_000L;
    private static final long REDUCED_DURATION_NANOS = 120_000_000L;
    private static final long MAX_TRANSITION_PIXELS = 1_200_000L;
    private long started;
    private long duration;
    private float progress;
    private final Timer timer = new FrameTimer(e -> step());
    private BufferedImage before;
    private BufferedImage after;
    private BufferedImage frame;
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
        started = 0;
        this.before = before;
        this.after = null;
        this.from = new Rectangle(from);
        progress = 0f;
        setVisible(true);
        repaint();
    }

    void land(BufferedImage after, Rectangle to) {
        if (before == null) return;
        this.after = after;
        this.to = new Rectangle(to);
        duration = Motion.reduced() ? REDUCED_DURATION_NANOS : DURATION_NANOS;
        started = System.nanoTime();
        timer.start();
        repaint();
    }

    void cancel() {
        timer.stop();
        started = 0;
        before = null;
        after = null;
        if (frame != null) frame.flush();
        frame = null;
        setVisible(false);
        java.awt.Container parent = getParent();
        if (parent != null) {
            parent.remove(this);
            parent.repaint();
        }
    }

    private void step() {
        if (after == null) { timer.stop(); return; }
        long elapsed = System.nanoTime() - started;
        progress = progressAt(elapsed, duration);
        if (progress >= 1f) cancel();
        else repaint();
    }

    /** Critically damped, normalized to a fixed wall-clock duration. A slow frame
     * skips ahead rather than making the entire focus transition play in slow motion. */
    static float progressAt(long elapsed, long duration) {
        if (elapsed <= 0) return 0f;
        if (elapsed >= duration) return 1f;
        double t = 9.0 * elapsed / duration;
        double end = 1 - 10 * Math.exp(-9.0);
        return (float) ((1 - (1 + t) * Math.exp(-t)) / end);
    }

    private float amount() {
        return progress;
    }

    /** Cap only the temporary focus pictures, not the page, graphs or PDF export. */
    static double snapshotScale(int width, int height, double nativeScale) {
        if (width <= 0 || height <= 0) return 1;
        return Math.min(nativeScale, Math.sqrt(MAX_TRANSITION_PIXELS / ((double) width * height)));
    }

    @Override protected void paintComponent(Graphics g) {
        if (capturing || before == null) return;
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (after == null || progress == 0f) {
                g2.drawImage(before, 0, 0, w, h, null);
                return;
            }
            // Composite at the bounded snapshot resolution and scale only once to the
            // device. Four full-retina alpha blends used to monopolize the EDT.
            if (frame == null || frame.getWidth() != after.getWidth() || frame.getHeight() != after.getHeight()) {
                if (frame != null) frame.flush();
                frame = new BufferedImage(after.getWidth(), after.getHeight(), BufferedImage.TYPE_INT_RGB);
            }
            Graphics2D scene = frame.createGraphics();
            try {
                scene.scale(frame.getWidth() / (double) w, frame.getHeight() / (double) h);
                scene.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                paintScene(scene, w, h);
            } finally { scene.dispose(); }
            g2.drawImage(frame, 0, 0, w, h, null);
        } finally { g2.dispose(); }
    }

    private void paintScene(Graphics2D g, int w, int h) {
        float p = amount();
        // Same split/fullscreen cross-fade and moving PDF rectangle as the supplied design.
        g.drawImage(after, 0, 0, w, h, null);
        g.setComposite(AlphaComposite.SrcOver.derive(1f - p));
        g.drawImage(before, 0, 0, w, h, null);
        Rectangle r = Motion.reduced() ? to : lerp(from, to, p);
        g.setComposite(AlphaComposite.SrcOver);
        part(g, after, to, r, w, h);
        g.setComposite(AlphaComposite.SrcOver.derive(1f - p));
        part(g, before, from, r, w, h);
    }

    // src is in board coordinates; the bounded snapshot may have a fractional scale.
    private static void part(Graphics2D g, BufferedImage img, Rectangle src, Rectangle dst,
                             int boardWidth, int boardHeight) {
        if (src.isEmpty() || dst.isEmpty() || boardWidth <= 0 || boardHeight <= 0) return;
        double sx = img.getWidth() / (double) boardWidth;
        double sy = img.getHeight() / (double) boardHeight;
        g.drawImage(img, dst.x, dst.y, dst.x + dst.width, dst.y + dst.height,
                (int) Math.floor(src.x * sx), (int) Math.floor(src.y * sy),
                (int) Math.ceil((src.x + src.width) * sx), (int) Math.ceil((src.y + src.height) * sy), null);
    }

    private static Rectangle lerp(Rectangle a, Rectangle b, float t) {
        return new Rectangle(
                Math.round(a.x + (b.x - a.x) * t),
                Math.round(a.y + (b.y - a.y) * t),
                Math.round(a.width + (b.width - a.width) * t),
                Math.round(a.height + (b.height - a.height) * t));
    }
}
