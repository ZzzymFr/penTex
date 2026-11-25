package view.board;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.function.Consumer;

/** Local optical layer for the Apple-style hover droplet. The original magnification,
 * edge bending and RGB dispersion are retained without a full-bar HiDPI raster. */
final class GlassLensRaster {
    private static final int PIXEL_BUDGET = 24_000;
    private static final int SOURCE_PAD = 16;
    private static final double EDGE_BAND = 11;
    private static final double EDGE_STRENGTH = 9;
    private static final double DISPERSION = .45;

    private record Key(GlassBackdropCache.Sample sample, long scene, int x, int y,
                       int width, int height, double scale, float magnification) { }
    private Key key;
    private BufferedImage image;
    private BufferedImage source;
    private int builds;

    void paint(Graphics2D g, RoundRectangle2D.Float lens, GlassBackdropCache.Sample sample,
               long scene, double deviceScale, float magnification, Consumer<Graphics2D> painter) {
        if (lens.width <= 0 || lens.height <= 0) return;
        int x = Math.round(lens.x * 2), y = Math.round(lens.y * 2);
        int w = Math.max(2, Math.round(lens.width * 2)), h = Math.max(2, Math.round(lens.height * 2));
        double logicalWidth = w / 2.0 + 2 * SOURCE_PAD, logicalHeight = h / 2.0 + 2 * SOURCE_PAD;
        // At Retina scale only the droplet texture has a bounded optical resolution.
        // The normal labels, icons and rim remain at the display's native resolution.
        double scale = Math.min(Math.min(1.5, Math.max(1.0, deviceScale)),
                Math.sqrt(PIXEL_BUDGET / (logicalWidth * logicalHeight)));
        Key wanted = new Key(sample, scene, x, y, w, h, scale, magnification);
        if (!wanted.equals(key)) rebuild(wanted, painter);
        Graphics2D draw = (Graphics2D) g.create();
        draw.clip(lens);
        draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        draw.drawImage(image, new AffineTransform(1 / scale, 0, 0, 1 / scale, x / 2.0, y / 2.0), null);
        draw.dispose();
    }

    private void rebuild(Key k, Consumer<Graphics2D> painter) {
        double s = k.scale, width = k.width / 2.0, height = k.height / 2.0;
        int pad = (int)Math.ceil(SOURCE_PAD * s);
        int w = Math.max(1, (int)Math.ceil(width * s)), h = Math.max(1, (int)Math.ceil(height * s));
        source = reuse(source, w + 2 * pad, h + 2 * pad);
        Graphics2D local = source.createGraphics();
        local.setComposite(java.awt.AlphaComposite.Src);
        local.setColor(BoardTheme.CANVAS);
        local.fillRect(0, 0, source.getWidth(), source.getHeight());
        local.setComposite(java.awt.AlphaComposite.SrcOver);
        GlassBar.hints(local);
        local.translate(pad, pad);
        local.scale(s, s);
        local.translate(-k.x / 2.0, -k.y / 2.0);
        painter.accept(local);
        local.dispose();
        image = reuse(image, w, h);
        int[] in = ((DataBufferInt) source.getRaster().getDataBuffer()).getData();
        int[] out = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int sw = source.getWidth(), sh = source.getHeight();
        double cx = width * s / 2, cy = height * s / 2, radius = cy;
        double x0 = cx - (width - height) * s / 2, x1 = cx + (width - height) * s / 2;
        double band = EDGE_BAND * s, strength = EDGE_STRENGTH * s;
        for (int y = 0; y < h; y++) {
            double py = y + .5, dy = py - cy;
            for (int x = 0; x < w; x++) {
                double px = x + .5, dx = px - Math.max(x0, Math.min(x1, px));
                double len = Math.sqrt(dx * dx + dy * dy), d = len - radius;
                if (d > .5) { out[y * w + x] = 0; continue; }
                double bx = pad + cx + (px - cx) / k.magnification - .5;
                double by = pad + cy + dy / k.magnification - .5;
                double ox = 0, oy = 0;
                if (-d < band && len > 1e-6) {
                    double t = 1 + d / band, shift = t * t * strength / len;
                    ox = dx * shift; oy = dy * shift;
                }
                int rgb;
                if (ox == 0 && oy == 0) rgb = interpolate(in, sw, sh, bx, by, 16) << 16
                        | interpolate(in, sw, sh, bx, by, 8) << 8 | interpolate(in, sw, sh, bx, by, 0);
                else rgb = interpolate(in, sw, sh, bx + ox*(1+DISPERSION), by + oy*(1+DISPERSION), 16) << 16
                        | interpolate(in, sw, sh, bx + ox, by + oy, 8) << 8
                        | interpolate(in, sw, sh, bx + ox*(1-DISPERSION), by + oy*(1-DISPERSION), 0);
                int alpha = (int)Math.round(255 * Math.min(1, .5-d));
                out[y*w+x] = alpha << 24 | rgb;
            }
        }
        key = k;
        builds++;
    }

    private static BufferedImage reuse(BufferedImage existing, int w, int h) {
        return existing != null && existing.getWidth() == w && existing.getHeight() == h ? existing
                : new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    private static int interpolate(int[] pixels, int w, int h, double x, double y, int shift) {
        x = Math.max(0, Math.min(w-1, x)); y = Math.max(0, Math.min(h-1, y));
        int ix = (int)x, iy = (int)y, nx = Math.min(w-1, ix+1), ny = Math.min(h-1, iy+1);
        double fx = x-ix, fy = y-iy;
        int a = pixels[iy*w+ix] >>> shift & 255, b = pixels[iy*w+nx] >>> shift & 255;
        int c = pixels[ny*w+ix] >>> shift & 255, d = pixels[ny*w+nx] >>> shift & 255;
        return (int)Math.round((a+(b-a)*fx)*(1-fy)+(c+(d-c)*fx)*fy);
    }

    int buildCount() { return builds; }
    void clear() { key = null; image = null; source = null; }
}
