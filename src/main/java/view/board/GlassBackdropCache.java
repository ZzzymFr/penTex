package view.board;

import javax.swing.Timer;
import java.awt.Point;
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

    void clear() {
        deferred.stop();
        texture = null;
        key = null;
    }
}
