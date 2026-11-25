package pdf_editor.view;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.Consumer;

/** Stable device-pixel backing for software Swing buffers and lost volatile surfaces.
 * The transform, including fractional DPI pixel phase, is part of the cache key. */
final class PdfBufferedViewportCache {
    private record Key(Rectangle area, Object state, double sx, double sy,
                       double phaseX, double phaseY, int width, int height) { }
    private BufferedImage image;
    private Key key;

    boolean paint(Graphics2D target, Rectangle visible, Object state,
                  Consumer<Graphics2D> painter, long maxPixels) {
        AffineTransform tx = target.getTransform();
        double sx = tx.getScaleX(), sy = tx.getScaleY();
        double left = tx.getTranslateX() + visible.x * sx;
        double top = tx.getTranslateY() + visible.y * sy;
        double pixelLeft = Math.floor(left), pixelTop = Math.floor(top);
        double phaseX = left - pixelLeft, phaseY = top - pixelTop;
        double wide = Math.ceil(phaseX + visible.width * sx);
        double high = Math.ceil(phaseY + visible.height * sy);
        if (!Double.isFinite(wide) || !Double.isFinite(high) || wide < 1 || high < 1
                || wide * high > maxPixels || wide > Integer.MAX_VALUE || high > Integer.MAX_VALUE) {
            clear(); return false;
        }
        int width = (int) wide, height = (int) high;
        Key next = new Key(new Rectangle(visible), state, sx, sy, phaseX, phaseY, width, height);
        if (image == null || image.getWidth() != width || image.getHeight() != height) {
            clear();
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }
        if (!Objects.equals(key, next)) {
            key = null;
            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHints(target.getRenderingHints());
                g.translate(phaseX, phaseY);
                g.scale(sx, sy);
                g.translate(-visible.x, -visible.y);
                g.setClip(visible);
                painter.accept(g);
            } finally { g.dispose(); }
            key = next;
        }
        Graphics2D output = (Graphics2D) target.create();
        try {
            output.clip(visible);
            // A 1:1 device-pixel copy, not a second HiDPI scaling operation.
            AffineTransform imageToUser = AffineTransform.getTranslateInstance(
                    visible.x - phaseX / sx, visible.y - phaseY / sy);
            imageToUser.scale(1 / sx, 1 / sy);
            output.drawImage(image, imageToUser, null);
        } finally { output.dispose(); }
        return true;
    }

    void clear() {
        if (image != null) image.flush();
        image = null; key = null;
    }
}
