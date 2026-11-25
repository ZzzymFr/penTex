package pdf_editor.view;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * PDF-only screen host for an interactive graph. The child retains its full logical
 * bounds and receives native Swing input. A large zoomed card must not allocate a
 * full-device-resolution graph raster on every ancestor repaint (including glass
 * animations and external drag ghosts). The export/snapshot API is not involved.
 */
final class PdfGraphPaintHost extends JComponent {
    static final int MAX_FRAME_PIXELS = 1_250_000;
    private static final int MAX_STABLE_FRAME_PIXELS = 3_000_000;
    private static final int MAX_FRAME_SIDE = 2048;
    private final JComponent card;
    private final BooleanSupplier synchronousFrame;
    private BufferedImage frame;
    private boolean frameDirty = true;
    private double frameScale;
    private long lastInputNanos;
    private long nextSurfaceFrameNanos;
    private final Timer deferredSurfacePaint = new Timer(1, event -> repaint());

    PdfGraphPaintHost(JComponent card, BooleanSupplier synchronousFrame) {
        this.card = Objects.requireNonNull(card);
        this.synchronousFrame = Objects.requireNonNull(synchronousFrame);
        setLayout(null);
        setOpaque(false);
        add(card);
        setVisible(false);
        deferredSurfacePaint.setRepeats(false);
        // The real card still owns all input. A synchronous plot can reuse its
        // last raster on unrelated ancestor paints, but never after input.
        MouseAdapter input = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { inputChanged(); }
            @Override public void mouseExited(MouseEvent e) { inputChanged(); }
            @Override public void mouseMoved(MouseEvent e) { inputChanged(); }
            @Override public void mouseDragged(MouseEvent e) { inputChanged(); }
            @Override public void mousePressed(MouseEvent e) { inputChanged(); }
            @Override public void mouseReleased(MouseEvent e) { inputChanged(); }
            @Override public void mouseClicked(MouseEvent e) { inputChanged(); }
            @Override public void mouseWheelMoved(MouseWheelEvent e) { inputChanged(); }
        };
        card.addMouseListener(input);
        card.addMouseMotionListener(input);
        card.addMouseWheelListener(input);
    }

    void setActive(boolean active) {
        if (card.isVisible() != active) { card.setVisible(active); invalidateFrame(); }
        if (isVisible() != active) setVisible(active);
        if (!active) clearFrame();
    }

    @Override public void setBounds(int x, int y, int width, int height) {
        boolean resized = width != getWidth() || height != getHeight();
        super.setBounds(x, y, width, height);
        if (resized) {
            card.setBounds(0, 0, width, height);
            clearFrame();
        }
    }

    /** A repaint requested by the real card must pass through the same bounded
     * painter, not bypass it via Swing's dirty-component fast path. */
    @Override protected boolean isPaintingOrigin() { return true; }

    @Override public void paintImmediately(int x, int y, int width, int height) {
        // Swing redirects direct child paint requests here. Surface plots also
        // deliver frames asynchronously and are deliberately never cached as stable.
        invalidateFrame();
        super.paintImmediately(x, y, width, height);
    }

    void invalidateFrame() { frameDirty = true; }

    private void inputChanged() {
        lastInputNanos = System.nanoTime();
        long activeDeadline = lastInputNanos + 25_000_000L;
        if (nextSurfaceFrameNanos > activeDeadline) {
            nextSurfaceFrameNanos = activeDeadline;
            if (deferredSurfacePaint.isRunning()) {
                deferredSurfacePaint.stop();
                scheduleSurfacePaint(25_000_000L);
            }
        }
        invalidateFrame();
    }

    private void scheduleSurfacePaint(long remainingNanos) {
        if (deferredSurfacePaint.isRunning()) return;
        int delay = (int) Math.max(1, (remainingNanos + 999_999) / 1_000_000);
        deferredSurfacePaint.setInitialDelay(delay);
        deferredSurfacePaint.start();
    }

    @Override protected void paintChildren(Graphics graphics) {
        if (!card.isVisible() || getWidth() <= 0 || getHeight() <= 0) return;
        if (!(graphics instanceof Graphics2D target) || isPaintingForPrint()) {
            super.paintChildren(graphics);
            return;
        }
        int width = getWidth(), height = getHeight();
        if (!target.hitClip(0, 0, width, height)) return;
        AffineTransform tx = target.getTransform();
        double device = Math.max(Math.abs(tx.getScaleX()), Math.abs(tx.getScaleY()));
        if (!Double.isFinite(device) || device <= 0 || tx.getShearX() != 0 || tx.getShearY() != 0) {
            super.paintChildren(graphics);
            return;
        }
        boolean synchronous = synchronousFrame.getAsBoolean();
        int budget = synchronous ? MAX_STABLE_FRAME_PIXELS : MAX_FRAME_PIXELS;
        double scale = Math.min(device, Math.min(MAX_FRAME_SIDE / (double) Math.max(width, height),
                Math.sqrt(budget / ((double) width * height))));
        // At normal sizes use the original native painter, exactly as on Canvas.
        if (scale == device && (long) width * height * device * device <= MAX_FRAME_PIXELS) {
            clearFrame();
            super.paintChildren(graphics);
            return;
        }
        int pixelWidth = Math.max(1, (int) Math.floor(width * scale));
        int pixelHeight = Math.max(1, (int) Math.floor(height * scale));
        if (frame == null || frame.getWidth() != pixelWidth || frame.getHeight() != pixelHeight) {
            clearFrame();
            frame = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB_PRE);
            frameDirty = true;
        }
        long now = System.nanoTime();
        boolean changedSize = frameScale != scale;
        // A 3D card can asynchronously ask Swing for a new frame. Never treat
        // it as permanently stable. If its repaint was merged into an ancestor
        // repaint, the one-shot below still guarantees a subsequent refresh.
        // There is no repeating idle timer and no dependency on engine internals.
        boolean throttled = !synchronous && !changedSize && nextSurfaceFrameNanos > now;
        if (throttled) {
            scheduleSurfacePaint(nextSurfaceFrameNanos - now);
        } else if (frameDirty || !synchronous || changedSize) {
            deferredSurfacePaint.stop();
            Graphics2D raster = frame.createGraphics();
            try {
                raster.setComposite(AlphaComposite.Clear);
                raster.fillRect(0, 0, pixelWidth, pixelHeight);
                raster.setComposite(AlphaComposite.SrcOver);
                raster.setRenderingHints(target.getRenderingHints());
                // One uniform transform keeps GraphCard's own device-pixel rendering
                // on its efficient path, with no changes to its layout or hit testing.
                raster.scale(scale, scale);
                card.print(raster);
            } finally { raster.dispose(); }
            frameDirty = false;
            frameScale = scale;
            boolean activeInput = now - lastInputNanos < 250_000_000L;
            nextSurfaceFrameNanos = synchronous ? 0 : System.nanoTime()
                    + (activeInput ? 25_000_000L : 120_000_000L);
        }
        Graphics2D output = (Graphics2D) target.create();
        try {
            output.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            output.drawImage(frame, 0, 0, width, height, null);
        } finally { output.dispose(); }
    }

    void clearFrame() {
        if (frame != null) frame.flush();
        frame = null;
        frameDirty = true;
        frameScale = 0;
        nextSurfaceFrameNanos = 0;
        deferredSurfacePaint.stop();
    }

    @Override public void removeNotify() {
        clearFrame();
        super.removeNotify();
    }
}
