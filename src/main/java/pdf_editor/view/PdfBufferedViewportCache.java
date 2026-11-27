package pdf_editor.view;

import java.awt.AlphaComposite;
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
    private BufferedImage scratch;
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
            Rectangle damage = key != null && key.equals(new Key(next.area(), key.state(),
                    sx, sy, phaseX, phaseY, width, height))
                    ? PdfPageDamage.between(key.state(), state, visible) : new Rectangle(visible);
            key = null;
            if (!damage.isEmpty()) {
                boolean fractional = sx != Math.rint(sx) || sy != Math.rint(sy)
                        || phaseX != 0 || phaseY != 0;
                if (fractional && !damage.equals(visible)) {
                    // Java2D can round the outermost antialiased pixels differently
                    // when a fractional-DPI image is clipped. Render a guarded area
                    // on a reusable backing and copy only its interior pixels.
                    if (scratch == null) scratch = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    Rectangle guarded = new Rectangle(damage);
                    guarded.grow(8, 8);
                    guarded = guarded.intersection(visible);
                    render(scratch, target, visible, guarded, phaseX, phaseY, sx, sy, painter);
                    copyDamage(damage, visible, phaseX, phaseY, sx, sy);
                } else {
                    render(image, target, visible, damage, phaseX, phaseY, sx, sy, painter);
                }
            }
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

    private static void render(BufferedImage backing, Graphics2D target, Rectangle visible, Rectangle clip,
                               double phaseX, double phaseY, double sx, double sy,
                               Consumer<Graphics2D> painter) {
        Graphics2D g = backing.createGraphics();
        try {
            g.setRenderingHints(target.getRenderingHints());
            g.translate(phaseX, phaseY);
            g.scale(sx, sy);
            g.translate(-visible.x, -visible.y);
            g.setClip(clip);
            painter.accept(g);
        } finally { g.dispose(); }
    }

    private void copyDamage(Rectangle damage, Rectangle visible, double phaseX, double phaseY,
                            double sx, double sy) {
        int x1 = Math.max(0, (int)Math.floor(phaseX + (damage.x - visible.x) * sx));
        int y1 = Math.max(0, (int)Math.floor(phaseY + (damage.y - visible.y) * sy));
        int x2 = Math.min(image.getWidth(), (int)Math.ceil(phaseX + (damage.getMaxX() - visible.x) * sx));
        int y2 = Math.min(image.getHeight(), (int)Math.ceil(phaseY + (damage.getMaxY() - visible.y) * sy));
        Graphics2D copy = image.createGraphics();
        try {
            copy.setComposite(AlphaComposite.Src);
            copy.drawImage(scratch, x1, y1, x2, y2, x1, y1, x2, y2, null);
        } finally { copy.dispose(); }
    }

    void clear() {
        if (image != null) image.flush();
        if (scratch != null) scratch.flush();
        image = null; scratch = null; key = null;
    }
}
