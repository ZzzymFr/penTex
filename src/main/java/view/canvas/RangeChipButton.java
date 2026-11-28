package view.canvas;

import view.stack.Fonts;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/** Text button: a blue link, a filled blue pill (Done), or an outlined pill that lights up (Auto). */
final class RangeChipButton extends JComponent {
    private final String text;
    private final boolean filled;
    private final Runnable action;
    boolean pill;
    boolean on;
    private boolean hover;
    private boolean pressed;

    RangeChipButton(String text, boolean filled, Runnable action) {
        this.text = text;
        this.filled = filled;
        this.action = action;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // darkens the moment it's pressed, acts on release over it (drag off = cancel);
        // mouseClicked dropped clicks whenever the hand moved a pixel
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    pressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean go = pressed && contains(e.getPoint()) && SwingUtilities.isLeftMouseButton(e);
                pressed = false;
                repaint();
                if (go) {
                    action.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    void click() {
        action.run();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics m = getFontMetrics(getFont() == null ? Fonts.ui(Font.PLAIN, 13f) : getFont());
        return new Dimension(m.stringWidth(text) + 8, m.getHeight() + 6);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double w = getWidth();
            double h = getHeight();
            RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, h, h);
            Color text;
            if (filled) {
                g.setColor(hover ? new Color(0, 105, 225) : GraphRangeChip.ACCENT);
                g.fill(shape);
                text = Color.WHITE;
            } else if (pill) {
                g.setColor(on ? new Color(0, 122, 255, 28) : hover ? new Color(248, 250, 252) : Color.WHITE);
                g.fill(shape);
                g.setColor(on ? GraphRangeChip.ACCENT : new Color(214, 218, 225));
                g.setStroke(new BasicStroke(1f));
                g.draw(shape);
                text = on ? GraphRangeChip.ACCENT : GraphRangeChip.INK;
            } else {
                text = hover ? new Color(0, 95, 210) : GraphRangeChip.ACCENT;
            }
            if (pressed) {
                if (filled || pill) {
                    g.setColor(new Color(0, 0, 0, filled ? 40 : 16));
                    g.fill(shape);
                } else {
                    text = new Color(0, 80, 180);
                }
            }
            g.setFont(getFont());
            g.setColor(text);
            FontMetrics m = g.getFontMetrics();
            float x = filled || pill ? (float) ((w - m.stringWidth(this.text)) / 2) : 4f;
            g.drawString(this.text, x, (float) ((h + m.getAscent() - m.getDescent()) / 2));
        } finally {
            g.dispose();
        }
    }
}
