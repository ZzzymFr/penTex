package pdf_editor.view;

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.AlphaComposite;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.VolatileImage;
import java.util.Objects;
import java.util.function.Consumer;

/** EDT-owned backing surface for the visible, stable page only. No document or export data. */
final class PdfViewportPaintCache {
    private static final long MAX_DEVICE_PIXELS = 12_000_000;
    private VolatileImage image;
    private Rectangle area;
    private Object state;
    private GraphicsConfiguration configuration;
    private double scaleX, scaleY;
    private boolean valid;
    private final PdfBufferedViewportCache buffered = new PdfBufferedViewportCache();
    private boolean preferBuffered;

    /** Retains stable pixels even if the native backing is unavailable or repeatedly lost. */
    boolean paint(Graphics2D target, Rectangle visible, Object nextState,
                  Consumer<Graphics2D> painter) {
        AffineTransform transform = target.getTransform();
        double sx = transform.getScaleX(), sy = transform.getScaleY();
        GraphicsConfiguration config = target.getDeviceConfiguration();
        if (visible.isEmpty() || !Double.isFinite(sx) || !Double.isFinite(sy) || sx <= 0 || sy <= 0 || transform.getShearX() != 0
                || transform.getShearY() != 0 || config == null
                || !(target.getComposite() instanceof AlphaComposite alpha)
                || alpha.getAlpha() != 1f
                || (alpha.getRule() != AlphaComposite.SRC_OVER && alpha.getRule() != AlphaComposite.SRC)
                || Math.ceil(visible.width * sx) * Math.ceil(visible.height * sy) > MAX_DEVICE_PIXELS) {
            clear(); return false;
        }
        // A volatile image has the screen configuration's native default transform on
        // HiDPI. Never silently downsample a Retina or scaled Windows page into a 1x raster.
        AffineTransform nativeTransform = config.getDefaultTransform();
        if (preferBuffered || config.getDevice().getType() != GraphicsDevice.TYPE_RASTER_SCREEN
                || sx != nativeTransform.getScaleX() || sy != nativeTransform.getScaleY()
                || sx != Math.rint(sx) || sy != Math.rint(sy)) {
            // Stay on the reliable backing for this view until a document/zoom
            // reset; Swing can alternate native and software graphics between
            // repaint passes. Alternating backing types defeats both caches.
            preferBuffered = true;
            clearNative();
            return buffered.paint(target, visible, nextState, painter, MAX_DEVICE_PIXELS);
        }
        buffered.clear();
        boolean sameSurface = image != null && config.equals(configuration)
                && sx == scaleX && sy == scaleY
                && area.width == visible.width && area.height == visible.height;
        if (!sameSurface) {
            clearNative();
            try {
                image = config.createCompatibleVolatileImage(visible.width, visible.height, Transparency.OPAQUE);
            } catch (RuntimeException unavailable) {
                return fallback(target, visible, nextState, painter);
            }
            if (image == null) return fallback(target, visible, nextState, painter);
            configuration = config; scaleX = sx; scaleY = sy;
        }
        if (!visible.equals(area) || !Objects.equals(state, nextState)) valid = false;
        area = new Rectangle(visible);
        state = nextState;
        int status = image.validate(config);
        if (status == VolatileImage.IMAGE_INCOMPATIBLE
                || (status == VolatileImage.IMAGE_RESTORED && valid)) {
            // A previously valid but lost surface must not trigger an expensive
            // render loop whenever the operating system switches paint buffers.
            return fallback(target, visible, nextState, painter);
        }
        if (status == VolatileImage.IMAGE_RESTORED) valid = false;
        if (!valid) {
            Graphics2D g = image.createGraphics();
            boolean matching;
            try {
                matching = g.getTransform().getScaleX() == sx && g.getTransform().getScaleY() == sy;
                if (matching) {
                    g.setRenderingHints(target.getRenderingHints());
                    g.translate(-visible.x, -visible.y);
                    g.setClip(visible);
                    painter.accept(g);
                }
            } finally { g.dispose(); }
            if (!matching) return fallback(target, visible, nextState, painter);
            valid = true;
        }
        if (image.contentsLost()) return fallback(target, visible, nextState, painter);
        target.drawImage(image, visible.x, visible.y, null);
        return !image.contentsLost() || fallback(target, visible, nextState, painter);
    }

    private boolean fallback(Graphics2D target, Rectangle visible, Object nextState,
                             Consumer<Graphics2D> painter) {
        clearNative();
        preferBuffered = true;
        return buffered.paint(target, visible, nextState, painter, MAX_DEVICE_PIXELS);
    }

    private void clearNative() {
        if (image != null) image.flush();
        image = null; area = null; state = null; configuration = null; valid = false;
    }

    void clear() {
        clearNative(); buffered.clear(); preferBuffered = false;
    }
}
