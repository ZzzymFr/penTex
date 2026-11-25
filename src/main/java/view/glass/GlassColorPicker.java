package view.glass;

import view.board.BoardTheme;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Pen colour picker in the macOS style, shown in a glass sheet instead of Swing's JColorChooser:
 * Apple's system colours to tap, then a saturation / brightness square and a hue strip for any
 * other colour, with a live preview. Same on Windows and macOS.
 */
public final class GlassColorPicker extends JPanel {
    private static final Color[] SYSTEM = {
        new Color(0x1C1C1E), new Color(0x8E8E93), new Color(255, 59, 48), new Color(255, 149, 0),
        new Color(255, 204, 0), new Color(52, 199, 89), new Color(0, 199, 190), new Color(48, 176, 199),
        new Color(0, 122, 255), new Color(88, 86, 214), new Color(175, 82, 222), new Color(255, 45, 85),
    };

    private float hue;
    private float sat;
    private float bri;
    private final JLabel hex = new JLabel();
    private final Preview preview = new Preview();
    private final Field field = new Field();
    private final Strip strip = new Strip();

    /** Asks for a colour; null when cancelled. */
    public static Color pick(Component parent, String title, Color initial) {
        GlassColorPicker picker = new GlassColorPicker(initial == null ? Color.BLACK : initial);
        return GlassDialogs.choose(parent, title, picker, "Choose") ? picker.color() : null;
    }

    public GlassColorPicker(Color initial) {
        super(new BorderLayout(0, 12));
        setOpaque(false);
        set(initial);

        JPanel swatches = new JPanel(new GridLayout(1, SYSTEM.length, 6, 0));
        swatches.setOpaque(false);
        for (Color c : SYSTEM) {
            swatches.add(new Swatch(c));
        }
        add(swatches, BorderLayout.NORTH);

        JPanel mixer = new JPanel(new BorderLayout(0, 10));
        mixer.setOpaque(false);
        mixer.add(field, BorderLayout.CENTER);
        mixer.add(strip, BorderLayout.SOUTH);
        add(mixer, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setOpaque(false);
        bottom.add(preview, BorderLayout.WEST);
        hex.setFont(BoardTheme.font(13f));
        hex.setForeground(BoardTheme.LABEL_SECONDARY);
        bottom.add(hex, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        refresh();
    }

    public Color color() {
        return Color.getHSBColor(hue, sat, bri);
    }

    private void set(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        if (hsb[1] > 0.01f && hsb[2] > 0.01f) {
            hue = hsb[0];  // greys have no hue: keep the old one so the strip doesn't jump
        }
        sat = hsb[1];
        bri = hsb[2];
    }

    private void refresh() {
        Color c = color();
        hex.setText(String.format(Locale.ROOT, "#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue()));
        repaint();
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private final class Swatch extends JComponent {
        private final Color color;
        private boolean over;

        Swatch(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(24, 24));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { set(color); refresh(); }
                @Override public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { over = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            AppleLook.smooth(g2);
            double d = Math.min(getWidth(), getHeight()) - 4;
            double x = (getWidth() - d) / 2;
            double y = (getHeight() - d) / 2;
            boolean chosen = color.getRGB() == color().getRGB();
            if (chosen || over) {
                g2.setColor(chosen ? new Color(0, 0, 0, 90) : new Color(0, 0, 0, 30));
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new Ellipse2D.Double(x - 1, y - 1, d + 2, d + 2));
                x += 2.5;
                y += 2.5;
                d -= 5;
            }
            g2.setColor(color);
            g2.fill(new Ellipse2D.Double(x, y, d, d));
            g2.dispose();
        }
    }

    /** Saturation across, brightness up, for the current hue. */
    private final class Field extends JComponent {
        private BufferedImage cache;
        private float cachedHue = -1;

        Field() {
            setPreferredSize(new Dimension(300, 150));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { pick(e); }
                @Override public void mouseDragged(MouseEvent e) { pick(e); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private void pick(MouseEvent e) {
            sat = clamp(e.getX() / (float) Math.max(1, getWidth() - 1));
            bri = clamp(1f - e.getY() / (float) Math.max(1, getHeight() - 1));
            refresh();
        }

        @Override protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (cache == null || cache.getWidth() != w || cache.getHeight() != h || cachedHue != hue) {
                cache = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        cache.setRGB(x, y, Color.HSBtoRGB(hue, x / (float) Math.max(1, w - 1),
                                1f - y / (float) Math.max(1, h - 1)));
                    }
                }
                cachedHue = hue;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            AppleLook.smooth(g2);
            RoundRectangle2D shape = new RoundRectangle2D.Double(0, 0, w, h, 12, 12);
            g2.clip(shape);
            g2.drawImage(cache, 0, 0, null);
            g2.setClip(null);
            g2.setColor(new Color(0, 0, 0, 30));
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 12, 12));
            double cx = sat * (w - 1);
            double cy = (1 - bri) * (h - 1);
            knob(g2, cx, cy, color());
            g2.dispose();
        }
    }

    /** Rainbow strip for the hue. */
    private final class Strip extends JComponent {
        Strip() {
            setPreferredSize(new Dimension(300, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { pick(e); }
                @Override public void mouseDragged(MouseEvent e) { pick(e); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private void pick(MouseEvent e) {
            hue = clamp(e.getX() / (float) Math.max(1, getWidth() - 1));
            if (sat < 0.05f) sat = 1f;  // picking a hue from grey: show it
            if (bri < 0.05f) bri = 1f;
            refresh();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            AppleLook.smooth(g2);
            int w = getWidth();
            int h = getHeight();
            RoundRectangle2D shape = new RoundRectangle2D.Double(0, 2, w, h - 4, h - 4, h - 4);
            g2.clip(shape);
            for (int x = 0; x < w; x++) {
                g2.setColor(Color.getHSBColor(x / (float) Math.max(1, w - 1), 1f, 1f));
                g2.fillRect(x, 0, 1, h);
            }
            g2.setClip(null);
            knob(g2, hue * (w - 1), h / 2.0, Color.getHSBColor(hue, 1f, 1f));
            g2.dispose();
        }
    }

    private static void knob(Graphics2D g2, double cx, double cy, Color fill) {
        double r = 8;
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fill(new Ellipse2D.Double(cx - r - 1, cy - r, 2 * r + 2, 2 * r + 2));
        g2.setColor(Color.WHITE);
        g2.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        g2.setColor(fill);
        g2.fill(new Ellipse2D.Double(cx - r + 3, cy - r + 3, 2 * r - 6, 2 * r - 6));
    }

    private final class Preview extends JComponent {
        Preview() {
            setPreferredSize(new Dimension(44, 26));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            AppleLook.smooth(g2);
            RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.setPaint(new GradientPaint(0, 0, color(), 0, getHeight(), color()));
            g2.fill(shape);
            g2.setColor(new Color(0, 0, 0, 40));
            g2.draw(shape);
            g2.dispose();
        }
    }
}
