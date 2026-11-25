package view.board;

import javax.swing.Timer;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.function.Function;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.function.Supplier;

/** One bounded, low-resolution optical texture per control, independent of animation state.
 * Only the sampled background is reduced in resolution; controls/text remain vector-painted.
 * Access is confined to the EDT in the application. */
final class GlassBackdropCache {
    static final double SCALE = .65;
    private static final int REFRESH_MS = 100;
    private static final ConvolveOp SOFTEN = new ConvolveOp(new Kernel(3, 3,
            new float[]{.0625f,.125f,.0625f,.125f,.25f,.125f,.0625f,.125f,.0625f}),
            ConvolveOp.EDGE_NO_OP, null);
    private record Key(int width, int height, boolean vertical, int x, int y, long version) { }
    private Key key;
    private BufferedImage texture;
    private long sampled;
    private final Runnable repaint;
    private final Timer deferred;

    GlassBackdropCache(Runnable repaint) {
        this.repaint = repaint;
        deferred = new Timer(REFRESH_MS, e -> this.repaint.run());
        deferred.setRepeats(false);
    }

    BufferedImage image(int width, int height, boolean vertical, Point origin, long version,
                        boolean animating, Supplier<BufferedImage> capture) {
        Key wanted = new Key(width, height, vertical, origin.x, origin.y, version);
        if (wanted.equals(key)) return texture;
        long elapsed = System.nanoTime() - sampled;
        if (texture != null && key.vertical == vertical && (animating || elapsed < REFRESH_MS * 1_000_000L)) {
            // The final frame or a single deferred repaint picks up the latest background.
            // Do not continuously restart the timer while the pointer moves.
            if (!deferred.isRunning()) deferred.start();
            return texture;
        }
        BufferedImage raw = capture.get();
        texture = raw.getWidth() >= 3 && raw.getHeight() >= 3 ? SOFTEN.filter(raw, null) : raw;
        key = wanted;
        sampled = System.nanoTime();
        deferred.stop();
        return texture;
    }

    /** Sample-space mapping is independent of the changing component size. */
    record Sample(BufferedImage image, int offsetX, int offsetY) {
        void paint(Graphics2D g) {
            g.drawImage(image, new AffineTransform(1 / SCALE, 0, 0, 1 / SCALE,
                    offsetX, offsetY), null);
        }
    }

    /** Overscan the hover-label expansion and small dock movements. A reused
     * texture is translated/cropped, never stretched to the new capsule size. */
    Sample region(int width, int height, boolean vertical, Point origin, long version,
                  boolean animating, Function<Rectangle, BufferedImage> capture) {
        Rectangle visible = new Rectangle(origin.x, origin.y, vertical ? height : width,
                vertical ? width : height);
        Rectangle cached = key == null ? null : new Rectangle(key.x, key.y,
                key.vertical ? key.height : key.width, key.vertical ? key.width : key.height);
        boolean covers = texture != null && key.vertical == vertical && cached.contains(visible);
        if (!covers) {
            clear();
            // Capture extra space on both edges; most hover-label growth is to the right.
            int pad = 48, extra = 160;
            Rectangle bounds = new Rectangle(visible.x - pad, visible.y - pad,
                    visible.width + 2 * pad + (vertical ? 0 : extra),
                    visible.height + 2 * pad + (vertical ? extra : 0));
            image(vertical ? bounds.height : bounds.width, vertical ? bounds.width : bounds.height,
                    vertical, bounds.getLocation(), version, false, () -> capture.apply(bounds));
        } else if (version != key.version) {
            // Reuse a stable region during ordinary animation, but refresh even if a
            // long-running animation overlaps real background edits.
            boolean hold = animating && System.nanoTime() - sampled < 250_000_000L;
            Rectangle bounds = cached;
            image(key.width, key.height, vertical, bounds.getLocation(), version, hold,
                    () -> capture.apply(bounds));
        }
        int dx = key.x - origin.x, dy = key.y - origin.y;
        return new Sample(texture, vertical ? dy : dx, vertical ? dx : dy);
    }

    void clear() {
        deferred.stop();
        texture = null;
        key = null;
    }
}
