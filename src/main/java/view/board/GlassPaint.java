package view.board;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/** Stateless drawing helpers of the glass bars: easing, rendering hints, rims and colour swatches. */
final class GlassPaint {
    private GlassPaint() {
    }

    /** Frame-rate independent ease: the share of the way to go covered this frame. */
    static float ease(float k, float f) {
        return 1f - (float) Math.pow(1f - k, f);
    }

    static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    static Paint rimPaint(RoundRectangle2D.Float r, int top, int bottom) {
        return new LinearGradientPaint(r.x, r.y, r.x + r.width * 0.35f, r.y + r.height * 2.2f,
                new float[]{0f, 0.35f, 0.7f, 1f},
                new Color[]{new Color(255, 255, 255, top), new Color(255, 255, 255, 0),
                    new Color(255, 255, 255, 0), new Color(255, 255, 255, bottom)});
    }

    static RoundRectangle2D.Float inset(RoundRectangle2D.Float r, float d) {
        return new RoundRectangle2D.Float(r.x + d, r.y + d, r.width - 2 * d, r.height - 2 * d,
                r.arcwidth - 2 * d, r.archeight - 2 * d);
    }

    /** A colour dot (or the rainbow custom-colour dot), ringed when it is the selected colour. */
    static void paintSwatch(Graphics2D g2, GlassBar.Item it, double cx, double cy, float scale, boolean selected) {
        double d = Math.min(22, it.w - 8) * scale;  // dot shrinks with a squeezed bar
        Ellipse2D dot = new Ellipse2D.Double(cx - d / 2, cy - d / 2, d, d);
        if (it.custom) {
            for (int i = 0; i < 24; i++) {
                g2.setColor(Color.getHSBColor(i / 24f, 0.75f, 0.95f));
                g2.fill(new Arc2D.Double(dot.getBounds2D(), 90 - i * 15, -15.5, Arc2D.PIE));
            }
            double di = d - 7 * scale;
            g2.setColor(it.color != null ? it.color : BoardTheme.CANVAS);
            g2.fill(new Ellipse2D.Double(cx - di / 2, cy - di / 2, di, di));
        } else {
            g2.setColor(it.color);
            g2.fill(dot);
        }
        g2.setColor(BoardTheme.EDGE);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(dot);
        if (selected) {
            double dr = d + 8 * scale;
            g2.setColor(BoardTheme.LABEL_SELECTED);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new Ellipse2D.Double(cx - dr / 2, cy - dr / 2, dr, dr));
        }
    }
}
