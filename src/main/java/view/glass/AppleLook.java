package view.glass;

import view.board.BoardTheme;
import view.stack.Fonts;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The plain Swing controls (scroll bars, sliders, pop-up buttons, tooltips, lists, fields) drawn
 * the Apple way, and drawn the same on macOS and Windows instead of each platform's own look.
 * Call {@link #install()} once after the look and feel is set.
 */
public final class AppleLook {
    static final Color HAIRLINE = new Color(0, 0, 0, 30);
    static final Color FILL = new Color(0, 0, 0, 14);
    private static final Color THUMB = new Color(0, 0, 0, 80);
    private static final Color THUMB_HOT = new Color(0, 0, 0, 130);

    private AppleLook() {
    }

    public static void install() {
        UIManager.put("ScrollBarUI", ThinScrollBarUI.class.getName());
        UIManager.put("SliderUI", CapsuleSliderUI.class.getName());
        UIManager.put("ComboBoxUI", PopUpButtonUI.class.getName());

        Font body = new FontUIResource(Fonts.ui(Font.PLAIN, 13f));
        Font small = new FontUIResource(Fonts.ui(Font.PLAIN, 12f));
        for (String key : new String[]{"Label.font", "Button.font", "ToggleButton.font", "ComboBox.font",
                "List.font", "TextField.font", "TextArea.font", "FormattedTextField.font",
                "PasswordField.font", "CheckBox.font", "RadioButton.font", "Menu.font", "MenuItem.font",
                "PopupMenu.font"}) {
            UIManager.put(key, body);
        }
        UIManager.put("ToolTip.font", small);

        Color accent = BoardTheme.ACCENT;
        UIManager.put("ToolTip.background", new ColorUIResource(250, 250, 252));
        UIManager.put("ToolTip.foreground", new ColorUIResource(BoardTheme.LABEL));
        UIManager.put("ToolTip.border", new BorderUIResource(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 0, 0, 38)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8))));
        UIManager.put("ScrollPane.border", new BorderUIResource(BorderFactory.createEmptyBorder()));
        UIManager.put("List.selectionBackground", new ColorUIResource(accent));
        UIManager.put("List.selectionForeground", new ColorUIResource(Color.WHITE));
        for (String field : new String[]{"TextField", "TextArea", "FormattedTextField", "PasswordField", "TextPane",
                "EditorPane"}) {
            UIManager.put(field + ".selectionBackground", new ColorUIResource(179, 215, 255));
            UIManager.put(field + ".selectionForeground", new ColorUIResource(BoardTheme.LABEL));
            UIManager.put(field + ".caretForeground", new ColorUIResource(accent));
        }
        UIManager.put("ComboBox.selectionBackground", new ColorUIResource(accent));
        UIManager.put("ComboBox.selectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("ComboBox.background", new ColorUIResource(Color.WHITE));
        UIManager.put("ComboBox.foreground", new ColorUIResource(BoardTheme.LABEL));
    }

    static void smooth(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /** Overlay-style scroller: no arrows, no track, a slim rounded thumb that thickens under the mouse. */
    public static final class ThinScrollBarUI extends BasicScrollBarUI {
        private static final int THICK = 11;

        public static ComponentUI createUI(JComponent c) {
            return new ThinScrollBarUI();
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            scrollbar.setOpaque(false);
            scrollbar.setBorder(BorderFactory.createEmptyBorder());
            minimumThumbSize = new Dimension(THICK, 36);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return nothing();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return nothing();
        }

        private static JButton nothing() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            b.setFocusable(false);
            b.setBorder(BorderFactory.createEmptyBorder());
            return b;
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            return scrollbar.getOrientation() == JScrollBar.VERTICAL
                    ? new Dimension(THICK, 48) : new Dimension(48, THICK);
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            // no track: content shows to the edge
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            boolean hot = isThumbRollover() || isDragging;
            int t = hot ? 8 : 6;
            boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
            RoundRectangle2D shape = vertical
                    ? new RoundRectangle2D.Double(r.x + (r.width - t) / 2.0 + 1, r.y + 2, t, r.height - 4, t, t)
                    : new RoundRectangle2D.Double(r.x + 2, r.y + (r.height - t) / 2.0 + 1, r.width - 4, t, t, t);
            g2.setColor(hot ? THUMB_HOT : THUMB);
            g2.fill(shape);
            g2.dispose();
        }
    }

    /** Thin capsule track filled in the accent colour up to a round white knob. */
    public static final class CapsuleSliderUI extends BasicSliderUI {
        private static final int KNOB = 18;

        public CapsuleSliderUI(JSlider slider) {
            super(slider);
        }

        public static ComponentUI createUI(JComponent c) {
            return new CapsuleSliderUI((JSlider) c);
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(KNOB, KNOB);
        }

        @Override
        public void paintFocus(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(BoardTheme.ACCENT);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(focusRect.x + 1, focusRect.y + 1,
                    Math.max(0, focusRect.width - 3), Math.max(0, focusRect.height - 3), 7, 7);
            g2.dispose();
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            Rectangle t = trackRect;
            boolean horizontal = slider.getOrientation() == JSlider.HORIZONTAL;
            double thickness = 4;
            RoundRectangle2D track = horizontal
                    ? new RoundRectangle2D.Double(t.x, t.y + t.height / 2.0 - thickness / 2, t.width, thickness,
                    thickness, thickness)
                    : new RoundRectangle2D.Double(t.x + t.width / 2.0 - thickness / 2, t.y, thickness, t.height,
                    thickness, thickness);
            g2.setColor(new Color(0, 0, 0, 30));
            g2.fill(track);
            int knob = horizontal ? thumbRect.x + thumbRect.width / 2 : thumbRect.y + thumbRect.height / 2;
            RoundRectangle2D filled = horizontal
                    ? new RoundRectangle2D.Double(t.x, track.getY(), Math.max(0, knob - t.x), thickness,
                    thickness, thickness)
                    : new RoundRectangle2D.Double(track.getX(), knob, thickness, Math.max(0, t.y + t.height - knob),
                    thickness, thickness);
            g2.setColor(slider.isEnabled() ? BoardTheme.ACCENT : new Color(0, 0, 0, 60));
            g2.fill(filled);
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            double d = KNOB - 2;
            double x = thumbRect.x + (thumbRect.width - d) / 2.0;
            double y = thumbRect.y + (thumbRect.height - d) / 2.0;
            for (int i = 1; i <= 3; i++) {
                g2.setColor(new Color(0, 0, 0, 12));
                g2.fill(new Ellipse2D.Double(x - i * 0.3, y + i * 0.5, d + i * 0.6, d + i * 0.6));
            }
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(x, y, d, d));
            g2.setColor(new Color(0, 0, 0, isDragging() ? 60 : 38));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new Ellipse2D.Double(x, y, d, d));
            g2.dispose();
        }
    }

    /** macOS pop-up button: a white rounded field with a hairline and a small up/down chevron. */
    public static final class PopUpButtonUI extends BasicComboBoxUI {
        private boolean over;

        public static ComponentUI createUI(JComponent c) {
            return new PopUpButtonUI();
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            comboBox.setOpaque(false);
            comboBox.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 2));
        }

        @Override
        protected void installListeners() {
            super.installListeners();
            comboBox.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; comboBox.repaint(); }
                @Override public void mouseExited(MouseEvent e) { over = false; comboBox.repaint(); }
            });
        }

        @Override
        protected JButton createArrowButton() {
            JButton b = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    smooth(g2);
                    double cx = getWidth() / 2.0 - 1;
                    double cy = getHeight() / 2.0;
                    g2.setColor(comboBox.isEnabled() ? new Color(0, 0, 0, 150) : new Color(0, 0, 0, 60));
                    g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D up = new Path2D.Double();
                    up.moveTo(cx - 3, cy - 2);
                    up.lineTo(cx, cy - 5);
                    up.lineTo(cx + 3, cy - 2);
                    Path2D down = new Path2D.Double();
                    down.moveTo(cx - 3, cy + 2);
                    down.lineTo(cx, cy + 5);
                    down.lineTo(cx + 3, cy + 2);
                    g2.draw(up);
                    g2.draw(down);
                    g2.dispose();
                }
            };
            b.setOpaque(false);
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusable(false);
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setPreferredSize(new Dimension(20, 20));
            return b;
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, c.getWidth() - 1, c.getHeight() - 2, 8, 8);
            g2.setColor(new Color(0, 0, 0, 10));
            g2.fill(new RoundRectangle2D.Double(0.5, 1.5, c.getWidth() - 1, c.getHeight() - 2, 8, 8));
            g2.setColor(Color.WHITE);
            g2.fill(shape);
            g2.setColor(c.hasFocus() ? BoardTheme.ACCENT : over && c.isEnabled() ? new Color(0, 0, 0, 52) : HAIRLINE);
            g2.draw(shape);
            g2.dispose();
            super.paint(g, c);
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            // the rounded field painted in paint() is the background
        }

        @Override
        public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            super.paintCurrentValue(g, bounds, false);  // never a selection-blue value box
        }

        @Override
        protected Insets getInsets() {
            return new Insets(3, 9, 3, 2);
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox) {
                @Override
                protected void configureList() {
                    super.configureList();
                    list.setSelectionBackground(BoardTheme.ACCENT);
                    list.setSelectionForeground(Color.WHITE);
                    list.setFixedCellHeight(24);
                }
            };
            Border edge = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0, 0, 0, 40)),
                    BorderFactory.createEmptyBorder(4, 4, 4, 4));
            popup.setBorder(edge);
            return popup;
        }
    }

    /** The four Apple button treatments. */
    public enum Tone {
        /** Filled accent: the one main action. */
        PRIMARY,
        /** Light grey fill: ordinary actions. */
        SECONDARY,
        /** Nothing until hovered: toolbar / footer icons. */
        QUIET,
        /** Accent-tinted: an on / chosen state. */
        TINTED
    }

    /** Fills a button face; press darkens (shows on mouse-down, not on release). */
    public static void paintButton(Graphics2D g, double w, double h, double arc, Tone tone,
                                   boolean hover, boolean pressed, boolean enabled) {
        Graphics2D g2 = (Graphics2D) g.create();
        smooth(g2);
        RoundRectangle2D shape = new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, arc, arc);
        Color fill = switch (tone) {
            case PRIMARY -> enabled ? BoardTheme.ACCENT : new Color(0, 0, 0, 30);
            case SECONDARY -> new Color(0, 0, 0, hover ? 20 : 12);
            case QUIET -> hover || pressed ? new Color(0, 0, 0, 14) : null;
            case TINTED -> new Color(0, 122, 255, hover ? 40 : 28);
        };
        if (fill != null) {
            g2.setColor(fill);
            g2.fill(shape);
        }
        if (tone == Tone.PRIMARY && enabled && hover && !pressed) {
            g2.setColor(new Color(255, 255, 255, 26));
            g2.fill(shape);
        }
        if (pressed && enabled) {
            g2.setColor(new Color(0, 0, 0, tone == Tone.PRIMARY ? 40 : 18));
            g2.fill(shape);
        }
        g2.dispose();
    }

    public static Color buttonText(Tone tone, boolean enabled) {
        if (!enabled) {
            return BoardTheme.LABEL_DISABLED;
        }
        return switch (tone) {
            case PRIMARY -> Color.WHITE;
            case TINTED -> BoardTheme.ACCENT;
            default -> BoardTheme.LABEL;
        };
    }

    /** Round grey close button with an x, like a macOS panel / sheet close. */
    public static void paintClose(Graphics2D g, double w, double h, boolean hover, boolean pressed) {
        Graphics2D g2 = (Graphics2D) g.create();
        smooth(g2);
        double d = Math.min(w, h) - 4;
        double x = (w - d) / 2;
        double y = (h - d) / 2;
        g2.setColor(new Color(0, 0, 0, pressed ? 34 : hover ? 24 : 13));
        g2.fill(new Ellipse2D.Double(x, y, d, d));
        g2.setColor(new Color(0, 0, 0, hover ? 170 : 130));
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double r = d * 0.18;
        double cx = w / 2;
        double cy = h / 2;
        g2.draw(new java.awt.geom.Line2D.Double(cx - r, cy - r, cx + r, cy + r));
        g2.draw(new java.awt.geom.Line2D.Double(cx + r, cy - r, cx - r, cy + r));
        g2.dispose();
    }

    /** List cell look: rounded, inset selection like a macOS source list, not a full-width bar. */
    public static void paintRowSelection(Graphics2D g2, Component row, boolean selected, boolean focusedList) {
        if (!selected) {
            return;
        }
        Graphics2D r = (Graphics2D) g2.create();
        smooth(r);
        r.setColor(focusedList ? BoardTheme.ACCENT : new Color(0, 0, 0, 22));
        r.fill(new RoundRectangle2D.Double(4, 1, row.getWidth() - 8, row.getHeight() - 2, 10, 10));
        r.dispose();
    }
}
