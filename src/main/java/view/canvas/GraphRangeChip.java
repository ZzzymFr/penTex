package view.canvas;

import interface_adapter.graph.GraphNumbers;
import view.graph.PlotCard;
import view.stack.Fonts;

import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.BorderFactory;
import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A graph's x / y (and z for 3D) range, shrunk to a see-through tag on the plot's bottom-left corner:
 * {@code ⤢ x −10 – 10  y auto}. It floats over the plot, so the card loses no height to it.
 *
 * <p>Clicking the tag opens {@link #panel()}: one row per axis with a min box, "to", a max box and an
 * Auto switch (Auto hides the boxes and lets the graph pick that axis). "Reset to default" puts every
 * axis back on Auto and redraws; Done (or Enter) checks the boxes and redraws; Esc or a click
 * elsewhere closes it unchanged.
 */
final class GraphRangeChip extends JComponent {
    static final Color ACCENT = new Color(0, 122, 255);
    private static final Color INK = new Color(51, 65, 85);
    private static final Color FAINT = new Color(148, 163, 184);
    private static final Color BAD = new Color(220, 38, 38);
    private static final Color LINE = new Color(226, 232, 240);
    private static final String DASH = " – ";
    private static final float MIN_CHIP_SCALE = 0.6f;

    /** One axis: Auto, or a fixed {min, max}. */
    static final class Axis {
        final String name;
        boolean auto = true;
        boolean keepAuto;                 // Auto chosen in the panel: a redraw doesn't fill it in
        double min = Double.NaN;          // last numbers, also shown when switching Auto off
        double max = Double.NaN;

        Axis(String name) {
            this.name = name;
        }

        boolean known() {
            return Double.isFinite(min) && Double.isFinite(max);
        }
    }

    private final Map<String, Axis> axes = new LinkedHashMap<>();
    private final Runnable onApply;
    private final Panel panel;
    private boolean showsZ;
    private float chipScale = 1f;
    private boolean hovering;

    GraphRangeChip(Runnable onApply) {
        this.onApply = onApply;
        axes.put("x", new Axis("x"));
        axes.put("y", new Axis("y"));
        axes.put("z", new Axis("z"));
        panel = new Panel();   // after the axes: it builds a row for each
        setOpaque(false);
        setVisible(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Change the x / y range");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (panel.isVisible()) {
                        panel.close();
                    } else {
                        panel.open();
                    }
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hovering = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovering = false;
                repaint();
            }
        });
    }

    /** The popover; it goes on the same overlay layer as the tag, above it. */
    JComponent panel() {
        return panel;
    }

    // ---- state

    /** A 3D graph gets a z row; 2D graphs and slope fields don't. */
    void setShowsZ(boolean showsZ) {
        if (this.showsZ != showsZ) {
            this.showsZ = showsZ;
            panel.rebuild();
            revalidateTag();
        }
    }

    List<Axis> shownAxes() {
        List<Axis> shown = new ArrayList<>();
        shown.add(axes.get("x"));
        shown.add(axes.get("y"));
        if (showsZ) {
            shown.add(axes.get("z"));
        }
        return shown;
    }

    Axis axis(String name) {
        return axes.get(name);
    }

    /** {min, max} to draw with, or null for Auto (or an axis this graph doesn't have). */
    double[] range(String name) {
        Axis a = axes.get(name);
        if (a == null || a.auto || ("z".equals(name) && !showsZ)) {
            return null;
        }
        return new double[]{a.min, a.max};
    }

    /** Old-style "lo, hi" text (blank or "auto" = Auto), for restoring a graph before it is drawn. */
    void setRangeText(String x, String y) {
        setFromText(axes.get("x"), x);
        setFromText(axes.get("y"), y);
        revalidateTag();
    }

    private static void setFromText(Axis a, String text) {
        double[] r;
        try {
            r = interface_adapter.graph.AxisRangeParser.parse(text, a.name);
        } catch (IllegalArgumentException e) {
            r = null;
        }
        a.auto = r == null;
        a.keepAuto = false;
        if (r != null) {
            a.min = r[0];
            a.max = r[1];
        }
    }

    /**
     * What the graph is showing now. Axes that follow the window (x, and y unless 3D) take its numbers
     * and leave Auto, except one put on Auto in the panel when this is just a redraw; a pan or zoom
     * ({@code moved}) always does. Other axes only remember the numbers, to start from if Auto goes off.
     */
    void showWindow(double[] x, double[] y, double[] z, boolean followsY, boolean moved) {
        if (panel.isVisible()) {
            return;   // don't stomp on typing
        }
        take(axes.get("x"), x, true, moved);
        take(axes.get("y"), y, followsY, moved);
        take(axes.get("z"), z, false, moved);
        revalidateTag();
    }

    private static void take(Axis a, double[] window, boolean follows, boolean moved) {
        if (window == null || !Double.isFinite(window[0]) || !Double.isFinite(window[1])) {
            return;
        }
        if (follows && (moved || !a.keepAuto)) {
            a.auto = false;
            a.keepAuto = false;
        }
        if (follows || a.auto) {
            a.min = window[0];
            a.max = window[1];
        }
    }

    // Reset to default: every axis on Auto, the graph picks again
    void resetToDefault() {
        for (Axis a : axes.values()) {
            a.auto = true;
            a.keepAuto = false;
        }
        revalidateTag();
    }

    // ---- placing

    /**
     * Puts the tag on the plot's bottom-left corner and the open panel just above it.
     *
     * @param card where the card is, in the overlay's coordinates
     */
    void placeOn(Rectangle card, PlotCard plotCard) {
        float scale = Math.max(MIN_CHIP_SCALE, plotCard.headerScale());
        if (scale != chipScale) {
            chipScale = scale;
            revalidateTag();
        }
        Rectangle plot = plotCard.plotBounds();
        Dimension d = getPreferredSize();
        int inset = Math.round(8 * chipScale);
        setBounds(card.x + plot.x + inset, card.y + plot.y + plot.height - inset - d.height, d.width, d.height);
        setVisible(true);
        if (panel.isVisible()) {
            panel.placeNear(this);
        }
    }

    void hideAll() {
        panel.close();
        if (isVisible()) {
            setVisible(false);
        }
    }

    private void revalidateTag() {
        invalidate();
        if (isVisible()) {
            Rectangle r = getBounds();
            Dimension d = getPreferredSize();
            setBounds(r.x, r.y + r.height - d.height, d.width, d.height);
            if (getParent() != null) {
                Rectangle dirty = r.union(getBounds());
                getParent().repaint(dirty.x, dirty.y, dirty.width, dirty.height);
            }
        }
        repaint();
    }

    // ---- the tag

    private Font axisFont(float size) {
        return new Font(Font.SERIF, Font.ITALIC, 1).deriveFont(size);
    }

    private static String number(double v) {
        return GraphNumbers.format(v).replace('-', '−');
    }

    static String rangeText(Axis a) {
        return a.auto || !a.known() ? "auto" : number(a.min) + DASH + number(a.max);
    }

    // what the tag reads, for tests
    String tagText() {
        StringBuilder s = new StringBuilder();
        for (Axis a : shownAxes()) {
            if (s.length() > 0) {
                s.append("  ");
            }
            s.append(a.name).append(' ').append(rangeText(a));
        }
        return s.toString();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics name = getFontMetrics(axisFont(12.5f * chipScale));
        FontMetrics num = getFontMetrics(Fonts.ui(Font.PLAIN, 11f * chipScale));
        double w = 9 * chipScale + 10 * chipScale + 7 * chipScale;   // pad + icon + gap
        boolean first = true;
        for (Axis a : shownAxes()) {
            if (!first) {
                w += 9 * chipScale;
            }
            first = false;
            w += name.stringWidth(a.name) + 4 * chipScale + num.stringWidth(rangeText(a));
        }
        w += 10 * chipScale;
        return new Dimension((int) Math.ceil(w), Math.round(24 * chipScale));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            double w = getWidth() - 1;
            double h = getHeight() - 1;
            RoundRectangle2D pill = new RoundRectangle2D.Double(0.5, 0.5, w, h, h, h);
            boolean lit = hovering || panel.isVisible();
            g.setColor(new Color(255, 255, 255, lit ? 245 : 205));
            g.fill(pill);
            g.setColor(new Color(15, 23, 42, lit ? 40 : 22));
            g.setStroke(new BasicStroke(1f));
            g.draw(pill);

            double cy = getHeight() / 2.0;
            double x = 9 * chipScale;
            expandIcon(g, x, cy, 10 * chipScale);
            x += 10 * chipScale + 7 * chipScale;

            Font nameFont = axisFont(12.5f * chipScale);
            Font numFont = Fonts.ui(Font.PLAIN, 11f * chipScale);
            FontMetrics nm = g.getFontMetrics(nameFont);
            FontMetrics mm = g.getFontMetrics(numFont);
            boolean first = true;
            for (Axis a : shownAxes()) {
                if (!first) {
                    x += 9 * chipScale;
                }
                first = false;
                g.setFont(nameFont);
                g.setColor(INK);
                g.drawString(a.name, (float) x, (float) (cy + (nm.getAscent() - nm.getDescent()) / 2.0));
                x += nm.stringWidth(a.name) + 4 * chipScale;
                String text = rangeText(a);
                g.setFont(numFont);
                g.setColor(INK);
                g.drawString(text, (float) x, (float) (cy + (mm.getAscent() - mm.getDescent()) / 2.0));
                x += mm.stringWidth(text);
            }
        } finally {
            g.dispose();
        }
    }

    // four corner brackets: "this is a window you can change"
    private void expandIcon(Graphics2D g, double x, double cy, double size) {
        g.setColor(FAINT);
        g.setStroke(new BasicStroke(1.2f * chipScale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double top = cy - size / 2;
        double k = size * 0.32;
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x, top + k);
        p.lineTo(x, top);
        p.lineTo(x + k, top);
        p.moveTo(x + size - k, top);
        p.lineTo(x + size, top);
        p.lineTo(x + size, top + k);
        p.moveTo(x + size, top + size - k);
        p.lineTo(x + size, top + size);
        p.lineTo(x + size - k, top + size);
        p.moveTo(x + k, top + size);
        p.lineTo(x, top + size);
        p.lineTo(x, top + size - k);
        g.draw(p);
    }

    // ---- the panel

    /** The popover: a row per axis, then Reset to default / Done. */
    final class Panel extends JComponent {
        private static final int PAD = 14;
        private static final int ROW = 30;
        private static final int ROW_GAP = 8;
        private static final int FIELD = 70;
        private static final int AUTO_W = 46;
        private static final int AUTO_H = 24;
        private static final int FOOT = 34;
        private final List<Row> rows = new ArrayList<>();
        private final Button reset = new Button("Reset to default", false, this::resetClicked);
        private final Button done = new Button("Done", true, this::apply);
        private String error;
        private AWTEventListener outside;

        Panel() {
            setLayout(null);
            setOpaque(false);
            setVisible(false);
            addMouseListener(new MouseAdapter() { });   // clicks on the panel stay off the canvas
            add(reset);
            add(done);
            rebuild();
        }

        // the rows for the axes this graph has
        void rebuild() {
            for (Row r : rows) {
                r.removeFrom(this);
            }
            rows.clear();
            for (Axis a : shownAxes()) {
                Row r = new Row(a);
                r.addTo(this);
                rows.add(r);
            }
            revalidate();
            repaint();
        }

        void open() {
            error = null;
            for (Row r : rows) {
                r.load();
            }
            setVisible(true);
            placeNear(GraphRangeChip.this);
            layoutRows();
            GraphRangeChip.this.repaint();
            outside = event -> {
                if (event.getID() == MouseEvent.MOUSE_PRESSED && event.getSource() instanceof Component c
                        && !SwingUtilities.isDescendingFrom(c, this)
                        && !SwingUtilities.isDescendingFrom(c, GraphRangeChip.this)) {
                    SwingUtilities.invokeLater(this::close);
                }
            };
            Toolkit.getDefaultToolkit().addAWTEventListener(outside, AWTEvent.MOUSE_EVENT_MASK);
            Row first = rows.stream().filter(r -> !r.auto).findFirst().orElse(null);
            if (first != null) {
                SwingUtilities.invokeLater(() -> {
                    first.min.requestFocusInWindow();
                    first.min.selectAll();
                });
            }
        }

        void close() {
            if (outside != null) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(outside);
                outside = null;
            }
            if (isVisible()) {
                Container parent = getParent();
                Rectangle was = getBounds();
                setVisible(false);
                if (parent != null) {
                    parent.repaint(was.x, was.y, was.width, was.height);
                }
                GraphRangeChip.this.repaint();
            }
        }

        // above the tag, left edges lined up; below it if there's no room above; kept inside the overlay
        void placeNear(JComponent tag) {
            Dimension d = getPreferredSize();
            Container parent = getParent();
            int gap = 6;
            int x = tag.getX();
            int y = tag.getY() - gap - d.height;
            if (parent != null) {
                if (y < 4) {
                    y = tag.getY() + tag.getHeight() + gap;
                }
                x = Math.max(4, Math.min(x, parent.getWidth() - d.width - 4));
            }
            Rectangle was = getBounds();
            setBounds(x, y, d.width, d.height);
            layoutRows();
            if (parent != null) {
                Rectangle dirty = was.union(getBounds());
                parent.repaint(dirty.x, dirty.y, dirty.width, dirty.height);
            }
        }

        // Done / Enter: check every box that isn't on Auto, then redraw
        void apply() {
            error = null;
            List<double[]> values = new ArrayList<>();
            for (Row r : rows) {
                r.clearError();
                if (r.auto) {
                    values.add(null);
                    continue;
                }
                double[] v = r.read();
                if (v == null) {
                    error = r.problem;
                    layoutRows();
                    repaint();
                    return;
                }
                values.add(v);
            }
            boolean changed = false;
            for (int i = 0; i < rows.size(); i++) {
                Axis a = rows.get(i).axis;
                double[] v = values.get(i);
                if (v == null) {
                    changed |= !a.auto;
                    a.keepAuto = true;
                    a.auto = true;
                } else {
                    changed |= a.auto || a.min != v[0] || a.max != v[1];
                    a.auto = false;
                    a.keepAuto = false;
                    a.min = v[0];
                    a.max = v[1];
                }
            }
            close();
            revalidateTag();
            if (changed) {
                onApply.run();
            }
        }

        private void resetClicked() {
            close();
            resetToDefault();
            onApply.run();
        }

        private float scale() {
            return Math.max(0.9f, Math.min(1.25f, chipScale));
        }

        private int s(double v) {
            return (int) Math.round(v * scale());
        }

        @Override
        public Dimension getPreferredSize() {
            int labelW = s(18);
            int toW = s(30);
            int width = s(PAD) + labelW + s(FIELD) + toW + s(FIELD) + s(16) + s(AUTO_W) + s(PAD);
            int height = s(PAD) + rows.size() * s(ROW) + (rows.size() - 1) * s(ROW_GAP)
                    + (error == null ? 0 : s(20)) + s(12) + 1 + s(10) + s(FOOT) + s(10);
            return new Dimension(width, height);
        }

        @Override
        public void doLayout() {
            layoutRows();
        }

        private void layoutRows() {
            int y = s(PAD);
            for (Row r : rows) {
                r.layout(y);
                y += s(ROW) + s(ROW_GAP);
            }
            y -= s(ROW_GAP);
            if (error != null) {
                y += s(20);
            }
            y += s(12) + 1 + s(10);
            reset.setFont(Fonts.ui(Font.PLAIN, 13f * scale()));
            done.setFont(Fonts.ui(Font.BOLD, 13f * scale()));
            Dimension rd = reset.getPreferredSize();
            reset.setBounds(s(PAD) - s(2), y + (s(FOOT) - rd.height) / 2, rd.width, rd.height);
            int doneW = s(60);
            done.setBounds(getWidth() - s(PAD) - doneW, y, doneW, s(FOOT));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                double w = getWidth();
                double h = getHeight();
                double arc = s(16);
                // soft shadow, a few faint rings
                for (int i = 4; i >= 1; i--) {
                    g.setColor(new Color(15, 23, 42, 7));
                    g.fill(new RoundRectangle2D.Double(i * 0.5, i * 0.9, w - i, h - i * 0.9, arc + i, arc + i));
                }
                RoundRectangle2D body = new RoundRectangle2D.Double(2, 1, w - 4, h - 4, arc, arc);
                g.setColor(Color.WHITE);
                g.fill(body);
                g.setColor(new Color(15, 23, 42, 22));
                g.setStroke(new BasicStroke(1f));
                g.draw(body);

                int y = s(PAD) + rows.size() * s(ROW) + (rows.size() - 1) * s(ROW_GAP);
                if (error != null) {
                    g.setFont(Fonts.ui(Font.PLAIN, 11.5f * scale()));
                    g.setColor(BAD);
                    FontMetrics m = g.getFontMetrics();
                    g.drawString(fit(error, m, (int) w - 2 * s(PAD)), s(PAD), y + s(4) + m.getAscent());
                    y += s(20);
                }
                y += s(12);
                g.setColor(LINE);
                g.draw(new Line2D.Double(s(PAD), y + 0.5, w - s(PAD), y + 0.5));

                for (Row r : rows) {
                    r.paintLabels(g);
                }
            } finally {
                g.dispose();
            }
        }

        private String fit(String text, FontMetrics m, int width) {
            if (m.stringWidth(text) <= width) {
                return text;
            }
            String cut = text;
            while (cut.length() > 1 && m.stringWidth(cut + "…") > width) {
                cut = cut.substring(0, cut.length() - 1);
            }
            return cut + "…";
        }

        // what the panel says is wrong, for tests
        String error() {
            return error;
        }

        Row row(String name) {
            return rows.stream().filter(r -> r.axis.name.equals(name)).findFirst().orElse(null);
        }

        int rowCount() {
            return rows.size();
        }

        void clickDone() {
            apply();
        }

        void clickReset() {
            resetClicked();
        }

        /** One axis: label, min box, "to", max box, Auto. */
        final class Row {
            final Axis axis;
            final NumberBox min = new NumberBox();
            final NumberBox max = new NumberBox();
            final Button autoSwitch;
            boolean auto;
            String problem;
            private int top;

            Row(Axis axis) {
                this.axis = axis;
                this.autoSwitch = new Button("Auto", false, this::toggleAuto);
                autoSwitch.pill = true;
                min.setToolTipText("smallest " + axis.name);
                max.setToolTipText("largest " + axis.name);
            }

            void addTo(Container c) {
                c.add(min);
                c.add(max);
                c.add(autoSwitch);
            }

            void removeFrom(Container c) {
                c.remove(min);
                c.remove(max);
                c.remove(autoSwitch);
            }

            // the axis as it is now -> the boxes
            void load() {
                auto = axis.auto;
                min.setText(axis.known() ? GraphNumbers.format(axis.min) : "");
                max.setText(axis.known() ? GraphNumbers.format(axis.max) : "");
                clearError();
                sync();
            }

            void toggleAuto() {
                auto = !auto;
                clearError();
                sync();
                if (!auto) {
                    min.requestFocusInWindow();
                    min.selectAll();
                }
                Panel.this.repaint();
            }

            private void sync() {
                autoSwitch.on = auto;
                min.setVisible(!auto);
                max.setVisible(!auto);
                autoSwitch.repaint();
            }

            void clearError() {
                problem = null;
                min.bad = false;
                max.bad = false;
                min.repaint();
                max.repaint();
            }

            // {min, max} or null with problem set and the bad box marked
            double[] read() {
                Double lo = number(min.getText());
                Double hi = number(max.getText());
                if (lo == null || hi == null) {
                    min.bad = lo == null;
                    max.bad = hi == null;
                    problem = "Type a number in both " + axis.name + " boxes, or switch on Auto.";
                } else if (!(lo < hi)) {
                    min.bad = true;
                    max.bad = true;
                    problem = "The smallest " + axis.name + " has to be less than the largest.";
                } else {
                    return new double[]{lo, hi};
                }
                min.repaint();
                max.repaint();
                (min.bad ? min : max).requestFocusInWindow();
                return null;
            }

            private Double number(String text) {
                String t = text == null ? "" : text.trim().replace('−', '-').replace('–', '-');
                try {
                    double v = Double.parseDouble(t);
                    return Double.isFinite(v) ? v : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }

            void layout(int y) {
                top = y;
                int x = s(PAD) + s(18);
                Font f = Fonts.ui(Font.PLAIN, 13.5f * scale());
                min.setFont(f);
                max.setFont(f);
                min.setBounds(x, y, s(FIELD), s(ROW));
                max.setBounds(x + s(FIELD) + s(30), y, s(FIELD), s(ROW));
                autoSwitch.setFont(Fonts.ui(Font.PLAIN, 11.5f * scale()));
                int ax = getWidth() - s(PAD) - s(AUTO_W);
                autoSwitch.setBounds(ax, y + (s(ROW) - s(AUTO_H)) / 2, s(AUTO_W), s(AUTO_H));
            }

            void paintLabels(Graphics2D g) {
                double cy = top + s(ROW) / 2.0;
                Font name = axisFont(15f * scale());
                FontMetrics nm = g.getFontMetrics(name);
                g.setFont(name);
                g.setColor(INK);
                g.drawString(axis.name, s(PAD) + s(2), (float) (cy + (nm.getAscent() - nm.getDescent()) / 2.0));
                Font small = Fonts.ui(Font.PLAIN, 12f * scale());
                FontMetrics sm = g.getFontMetrics(small);
                g.setFont(small);
                g.setColor(FAINT);
                float baseline = (float) (cy + (sm.getAscent() - sm.getDescent()) / 2.0);
                int fieldsX = s(PAD) + s(18);
                if (auto) {
                    g.drawString("follows the graph", fieldsX + s(8), baseline);
                } else {
                    int toX = fieldsX + s(FIELD) + (s(30) - sm.stringWidth("to")) / 2;
                    g.drawString("to", toX, baseline);
                }
            }
        }

        /** A grey rounded number box, text on the right like a calculator. */
        final class NumberBox extends JTextField {
            boolean bad;

            NumberBox() {
                setOpaque(false);
                setHorizontalAlignment(SwingConstants.RIGHT);
                setForeground(new Color(15, 23, 42));
                setCaretColor(ACCENT);
                setSelectionColor(new Color(0, 122, 255, 60));
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 10));
                addActionListener(e -> apply());
                addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                            close();
                        }
                    }
                });
                addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusGained(java.awt.event.FocusEvent e) {
                        repaint();
                    }

                    @Override
                    public void focusLost(java.awt.event.FocusEvent e) {
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double arc = s(9);
                RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, getWidth() - 1.5, getHeight() - 1.5,
                        arc, arc);
                boolean focused = isFocusOwner();
                g.setColor(focused ? Color.WHITE : new Color(241, 243, 246));
                g.fill(box);
                if (bad || focused) {
                    g.setColor(bad ? BAD : ACCENT);
                    g.setStroke(new BasicStroke(1.5f));
                    g.draw(box);
                }
                g.dispose();
                super.paintComponent(g0);
            }
        }
    }

    /** Text button: a blue link, a filled blue pill (Done), or an outlined pill that lights up (Auto). */
    static final class Button extends JComponent {
        private final String text;
        private final boolean filled;
        private final Runnable action;
        boolean pill;
        boolean on;
        private boolean hover;

        Button(String text, boolean filled, Runnable action) {
            this.text = text;
            this.filled = filled;
            this.action = action;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
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
                    g.setColor(hover ? new Color(0, 105, 225) : ACCENT);
                    g.fill(shape);
                    text = Color.WHITE;
                } else if (pill) {
                    g.setColor(on ? new Color(0, 122, 255, 28) : hover ? new Color(248, 250, 252) : Color.WHITE);
                    g.fill(shape);
                    g.setColor(on ? ACCENT : new Color(214, 218, 225));
                    g.setStroke(new BasicStroke(1f));
                    g.draw(shape);
                    text = on ? ACCENT : INK;
                } else {
                    text = hover ? new Color(0, 95, 210) : ACCENT;
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
}
