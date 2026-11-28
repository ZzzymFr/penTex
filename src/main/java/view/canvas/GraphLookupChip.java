package view.canvas;

import interface_adapter.graph.GraphLookup;
import view.graph.GraphCard;
import view.graph.PlotCard;
import view.stack.Fonts;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Find a value on a graph, as a see-through tag on the plot's top-right corner: {@code ⌖ x → y}, or what
 * the last find gave, e.g. {@code x = 2 → y = 4}. It floats over the plot like the range tag.
 *
 * <p>Clicking it opens {@link #panel()}: a box per axis. Type some, press Find (or Enter) and the blank
 * ones are worked out: a single answer fills its box, every match is listed below and marked on the
 * graph. Typing in a box clears the old answers; at most {@code maxGiven} boxes keep what was typed,
 * the oldest gives way. Clear takes the marks off; Esc or a click elsewhere closes it.
 */
final class GraphLookupChip extends JComponent {
    /** Where the boxes go: the graph's use case, which answers through its view model. */
    interface Asker {
        // one entry per box, blank = work it out
        void find(List<String> typed);

        void clear();
    }

    private static final Color INK = new Color(51, 65, 85);
    private static final Color FAINT = new Color(148, 163, 184);
    private static final Color HINT = new Color(100, 116, 139);
    private static final Color BAD = new Color(220, 38, 38);
    private static final Color LINE = new Color(226, 232, 240);
    private static final Color TYPED = new Color(15, 23, 42);
    private static final float MIN_CHIP_SCALE = 0.6f;
    private static final int MAX_SUMMARY = 40;
    private static final int MAX_LINES = 6;

    private final Asker asker;
    private final Panel panel;
    private List<String> names = List.of("x", "y");
    private int maxGiven = 1;
    private String hint = "";
    private GraphLookup result;
    private List<String> lastQuery;   // last find sent, null = none since the graph was new / cleared
    private float chipScale = 1f;
    private boolean hovering;

    GraphLookupChip(Asker asker) {
        this.asker = asker;
        panel = new Panel();
        setOpaque(false);
        setVisible(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Find a value: type x, get y");
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

    /**
     * The boxes this graph has: names per axis, how many can be typed at once, and a line on how to
     * use them. Changing the names starts the boxes over.
     */
    void setAxes(List<String> names, int maxGiven, String hint) {
        this.hint = hint == null ? "" : hint;
        this.maxGiven = Math.max(1, maxGiven);
        if (!names.equals(this.names)) {
            this.names = List.copyOf(names);
            panel.rebuild();
            lastQuery = null;
            result = null;
        }
        setToolTipText(names.size() == 3 ? "Find a value: type two of x, y, z, get the third"
                : "Find a value: type " + names.get(0) + ", get " + names.get(1));
        revalidateTag();
    }

    List<String> names() {
        return names;
    }

    /** A brand new graph: empty boxes, nothing found. */
    void reset() {
        lastQuery = null;
        result = null;
        panel.clearBoxes();
        revalidateTag();
    }

    /** What the last Find sent (to ask again after a redraw), null if there's nothing to ask. */
    List<String> lastQuery() {
        return lastQuery;
    }

    /** The answer came back (null = taken off): fill the boxes, list it, sum it up on the tag. */
    void showResult(GraphLookup lookup) {
        result = lookup;
        panel.show(lookup);
        revalidateTag();
    }

    // what the tag reads
    String tagText() {
        String summary = result == null ? null : result.getSummary();
        if (summary != null) {
            return summary.length() > MAX_SUMMARY ? summary.substring(0, MAX_SUMMARY - 1) + "…" : summary;
        }
        if (names.size() == 3) {
            return names.get(0) + " " + names.get(1) + " → " + names.get(2);
        }
        return names.get(0) + " → " + names.get(1);
    }

    // ---- placing

    /** Puts the tag on the plot's top-right corner and the open panel just under it. */
    void placeOn(Rectangle card, PlotCard plotCard) {
        float scale = Math.max(MIN_CHIP_SCALE, plotCard.headerScale());
        if (scale != chipScale) {
            chipScale = scale;
            revalidateTag();
        }
        Rectangle plot = plotCard.plotBounds();
        Dimension d = getPreferredSize();
        int inset = Math.round(8 * chipScale);
        setBounds(card.x + plot.x + plot.width - inset - d.width, card.y + plot.y + inset, d.width, d.height);
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

    // keeps the right edge where it is, the text can grow leftwards
    private void revalidateTag() {
        invalidate();
        if (isVisible()) {
            Rectangle r = getBounds();
            Dimension d = getPreferredSize();
            setBounds(r.x + r.width - d.width, r.y, d.width, d.height);
            if (getParent() != null) {
                Rectangle dirty = r.union(getBounds());
                getParent().repaint(dirty.x, dirty.y, dirty.width, dirty.height);
            }
            if (panel.isVisible()) {
                panel.placeNear(this);
            }
        }
        repaint();
    }

    // ---- the tag

    private Font tagFont() {
        return Fonts.ui(Font.PLAIN, 11.5f * chipScale);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics m = getFontMetrics(tagFont());
        double w = 9 * chipScale + 11 * chipScale + 7 * chipScale + m.stringWidth(tagText()) + 10 * chipScale;
        return new Dimension((int) Math.ceil(w), Math.round(24 * chipScale));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            hints(g);
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
            crosshair(g, x, cy, 11 * chipScale);
            x += 11 * chipScale + 7 * chipScale;
            Font font = tagFont();
            FontMetrics m = g.getFontMetrics(font);
            g.setFont(font);
            g.setColor(result != null && result.getSummary() != null ? GraphRangeChip.ACCENT : INK);
            g.drawString(tagText(), (float) x, (float) (cy + (m.getAscent() - m.getDescent()) / 2.0));
        } finally {
            g.dispose();
        }
    }

    // ring with four ticks: "point at a spot"
    private void crosshair(Graphics2D g, double x, double cy, double size) {
        g.setColor(FAINT);
        g.setStroke(new BasicStroke(1.2f * chipScale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double cx = x + size / 2;
        double r = size * 0.3;
        g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
        double in = size * 0.18;
        double out = size / 2;
        g.draw(new Line2D.Double(cx, cy - out, cx, cy - in));
        g.draw(new Line2D.Double(cx, cy + in, cx, cy + out));
        g.draw(new Line2D.Double(cx - out, cy, cx - in, cy));
        g.draw(new Line2D.Double(cx + in, cy, cx + out, cy));
    }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static Font axisFont(float size) {
        return new Font(Font.SERIF, Font.ITALIC, 1).deriveFont(size);
    }

    // words onto lines no wider than width
    static List<String> wrap(String text, FontMetrics m, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && m.stringWidth(next) > width) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    // ---- the panel

    /** The popover: title + hint, a box per axis, what was found, then Clear / Find. */
    final class Panel extends JComponent {
        private static final int PAD = 14;
        private static final int WIDTH = 284;
        private static final int ROW = 30;
        private static final int ROW_GAP = 8;
        private static final int LABEL = 26;
        private static final int TEXT_LINE = 19;
        private static final int FOOT = 34;
        private final List<Row> rows = new ArrayList<>();
        private final RangeChipButton clear = new RangeChipButton("Clear", false, this::clearClicked);
        private final RangeChipButton find = new RangeChipButton("Find", true, this::find);
        private List<GraphLookup.Line> lines = List.of();
        private String message;
        private boolean filling;      // we're writing answers in, not the user typing
        private long clock;
        private AWTEventListener outside;

        Panel() {
            setLayout(null);
            setOpaque(false);
            setVisible(false);
            addMouseListener(new MouseAdapter() { });   // clicks on the panel stay off the canvas
            add(clear);
            add(find);
            rebuild();
        }

        void rebuild() {
            for (Row r : rows) {
                remove(r.box);
            }
            rows.clear();
            for (String name : names) {
                Row r = new Row(name);
                add(r.box);
                rows.add(r);
            }
            lines = List.of();
            message = null;
            relayout();
        }

        void open() {
            setVisible(true);
            placeNear(GraphLookupChip.this);
            GraphLookupChip.this.repaint();
            outside = event -> {
                if (event.getID() == MouseEvent.MOUSE_PRESSED && event.getSource() instanceof Component c
                        && !SwingUtilities.isDescendingFrom(c, this)
                        && !SwingUtilities.isDescendingFrom(c, GraphLookupChip.this)) {
                    SwingUtilities.invokeLater(this::close);
                }
            };
            Toolkit.getDefaultToolkit().addAWTEventListener(outside, AWTEvent.MOUSE_EVENT_MASK);
            Row first = rows.stream().filter(r -> !r.answer && !r.box.getText().isBlank()).findFirst()
                    .orElse(rows.isEmpty() ? null : rows.get(0));
            if (first != null) {
                SwingUtilities.invokeLater(() -> {
                    first.box.requestFocusInWindow();
                    first.box.selectAll();
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
                GraphLookupChip.this.repaint();
            }
        }

        // under the tag, right edges lined up; above it if there's no room below; kept inside the overlay
        void placeNear(JComponent tag) {
            Dimension d = getPreferredSize();
            Container parent = getParent();
            int gap = 6;
            int x = tag.getX() + tag.getWidth() - d.width;
            int y = tag.getY() + tag.getHeight() + gap;
            if (parent != null) {
                if (y + d.height > parent.getHeight() - 4 && tag.getY() - gap - d.height >= 4) {
                    y = tag.getY() - gap - d.height;
                }
                x = Math.max(4, Math.min(x, parent.getWidth() - d.width - 4));
            }
            Rectangle was = getBounds();
            setBounds(x, y, d.width, d.height);
            layoutBoxes();
            if (parent != null) {
                Rectangle dirty = was.union(getBounds());
                parent.repaint(dirty.x, dirty.y, dirty.width, dirty.height);
            }
        }

        // Find / Enter: what's typed (answers count as blank) goes to the use case
        void find() {
            List<String> typed = new ArrayList<>();
            for (Row r : rows) {
                typed.add(r.answer ? "" : r.box.getText().trim());
            }
            lastQuery = List.copyOf(typed);
            asker.find(typed);
        }

        private void clearClicked() {
            clearBoxes();
            lastQuery = null;
            result = null;
            asker.clear();
            revalidateTag();
        }

        void clearBoxes() {
            filling = true;
            for (Row r : rows) {
                r.box.setText("");
                r.markAnswer(false);
            }
            filling = false;
            lines = List.of();
            message = null;
            relayout();
        }

        void show(GraphLookup lookup) {
            filling = true;
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                String answer = lookup == null || lookup.isGiven(i) ? null : lookup.getAnswer(i);
                if (answer != null) {
                    r.box.setText(answer);
                    r.markAnswer(true);
                } else if (r.answer) {
                    r.box.setText("");
                    r.markAnswer(false);
                }
            }
            filling = false;
            lines = lookup == null ? List.of() : lookup.getLines();
            message = lookup == null ? null : lookup.getMessage();
            relayout();
        }

        // the user typed in r: old answers go, and only so many boxes keep what was typed
        private void edited(Row r) {
            if (filling) {
                return;
            }
            r.markAnswer(false);
            r.editedAt = ++clock;
            filling = true;
            for (Row other : rows) {
                if (other != r && other.answer) {
                    other.box.setText("");
                    other.markAnswer(false);
                }
            }
            while (true) {
                List<Row> given = rows.stream().filter(o -> !o.answer && !o.box.getText().isBlank()).toList();
                if (given.size() <= maxGiven) {
                    break;
                }
                Row oldest = given.stream().filter(o -> o != r)
                        .min((a, b) -> Long.compare(a.editedAt, b.editedAt)).orElse(null);
                if (oldest == null) {
                    break;
                }
                oldest.box.setText("");
                oldest.box.repaint();
            }
            filling = false;
        }

        private float scale() {
            return Math.max(0.9f, Math.min(1.25f, chipScale));
        }

        private int s(double v) {
            return (int) Math.round(v * scale());
        }

        private Font titleFont() {
            return Fonts.ui(Font.BOLD, 13f * scale());
        }

        private Font hintFont() {
            return Fonts.ui(Font.PLAIN, 11.5f * scale());
        }

        private Font lineFont() {
            return Fonts.ui(Font.PLAIN, 12.5f * scale());
        }

        private int textWidth() {
            return s(WIDTH) - 2 * s(PAD);
        }

        private List<String> hintLines() {
            return wrap(hint, getFontMetrics(hintFont()), textWidth());
        }

        // what goes under the boxes, already cut to fit
        private List<String> resultLines() {
            List<String> out = new ArrayList<>();
            if (message != null) {
                out.addAll(wrap(message, getFontMetrics(hintFont()), textWidth()));
                return out;
            }
            FontMetrics m = getFontMetrics(lineFont());
            int shown = Math.min(lines.size(), MAX_LINES);
            for (int i = 0; i < shown; i++) {
                out.add(fit(lines.get(i).text(), m, textWidth() - s(14)));
            }
            if (lines.size() > shown) {
                out.add("… and " + (lines.size() - shown) + " more, marked on the graph");
            }
            return out;
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

        private int headHeight() {
            return s(PAD) + getFontMetrics(titleFont()).getHeight() + hintLines().size() * s(16) + s(8);
        }

        @Override
        public Dimension getPreferredSize() {
            int rowsH = rows.size() * s(ROW) + Math.max(0, rows.size() - 1) * s(ROW_GAP);
            List<String> results = resultLines();
            int resultsH = results.isEmpty() ? 0 : s(12) + 1 + s(8) + results.size() * s(TEXT_LINE);
            int height = headHeight() + rowsH + resultsH + s(12) + 1 + s(10) + s(FOOT) + s(10);
            return new Dimension(s(WIDTH), height);
        }

        private void relayout() {
            if (isVisible()) {
                placeNear(GraphLookupChip.this);
            }
            repaint();
        }

        @Override
        public void doLayout() {
            layoutBoxes();
        }

        private void layoutBoxes() {
            int y = headHeight();
            Font f = Fonts.ui(Font.PLAIN, 13.5f * scale());
            for (Row r : rows) {
                r.top = y;
                r.box.setFont(f);
                r.box.setBounds(s(PAD) + s(LABEL), y, getWidth() - 2 * s(PAD) - s(LABEL), s(ROW));
                y += s(ROW) + s(ROW_GAP);
            }
            int footY = getHeight() - s(10) - s(FOOT);
            clear.setFont(Fonts.ui(Font.PLAIN, 13f * scale()));
            find.setFont(Fonts.ui(Font.BOLD, 13f * scale()));
            Dimension cd = clear.getPreferredSize();
            clear.setBounds(s(PAD) - s(2), footY + (s(FOOT) - cd.height) / 2, cd.width, cd.height);
            int findW = s(64);
            find.setBounds(getWidth() - s(PAD) - findW, footY, findW, s(FOOT));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            try {
                hints(g);
                double w = getWidth();
                GraphRangeChip.paintPopover(g, w, getHeight(), s(16));
                // title + how to use it
                Font title = titleFont();
                FontMetrics tm = g.getFontMetrics(title);
                g.setFont(title);
                g.setColor(INK);
                int y = s(PAD) + tm.getAscent();
                g.drawString("Find a value", s(PAD), y);
                y = s(PAD) + tm.getHeight();
                Font hf = hintFont();
                FontMetrics hm = g.getFontMetrics(hf);
                g.setFont(hf);
                g.setColor(HINT);
                for (String line : hintLines()) {
                    g.drawString(line, s(PAD), y + hm.getAscent());
                    y += s(16);
                }
                for (Row r : rows) {
                    r.paintLabel(g);
                }
                // what was found
                int below = rows.isEmpty() ? headHeight()
                        : rows.get(rows.size() - 1).top + s(ROW);
                List<String> results = resultLines();
                if (!results.isEmpty()) {
                    below += s(12);
                    g.setColor(LINE);
                    g.draw(new Line2D.Double(s(PAD), below + 0.5, w - s(PAD), below + 0.5));
                    below += 1 + s(8);
                    boolean problem = message != null;
                    Font lf = problem ? hf : lineFont();
                    FontMetrics lm = g.getFontMetrics(lf);
                    g.setFont(lf);
                    for (int i = 0; i < results.size(); i++) {
                        int base = below + (s(TEXT_LINE) + lm.getAscent() - lm.getDescent()) / 2;
                        int x = s(PAD);
                        int layer = !problem && i < lines.size() && i < MAX_LINES ? lines.get(i).layer() : -1;
                        if (layer >= 0) {
                            double r = s(4);
                            g.setColor(GraphCard.functionColor(layer));
                            g.fill(new Ellipse2D.Double(x, below + s(TEXT_LINE) / 2.0 - r, 2 * r, 2 * r));
                            x += s(14);
                        }
                        g.setColor(problem ? BAD : (i < Math.min(lines.size(), MAX_LINES) ? INK : HINT));
                        g.drawString(results.get(i), x, base);
                        below += s(TEXT_LINE);
                    }
                }
                int sep = getHeight() - s(10) - s(FOOT) - s(10) - 1;
                g.setColor(LINE);
                g.draw(new Line2D.Double(s(PAD), sep + 0.5, w - s(PAD), sep + 0.5));
            } finally {
                g.dispose();
            }
        }

        // ---- for tests

        Row row(String name) {
            return rows.stream().filter(r -> r.name.equals(name)).findFirst().orElse(null);
        }

        int rowCount() {
            return rows.size();
        }

        List<String> shownLines() {
            return resultLines();
        }

        String message() {
            return message;
        }

        void clickFind() {
            find();
        }

        void clickClear() {
            clearClicked();
        }

        /** One axis: its name, then a box. */
        final class Row {
            final String name;
            final Box box = new Box();
            boolean answer;      // the box holds what was worked out, not what was typed
            long editedAt;
            int top;

            Row(String name) {
                this.name = name;
                box.setToolTipText(name + ": type a number, or leave it blank to work it out");
                box.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        edited(Row.this);
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        edited(Row.this);
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                    }
                });
            }

            void markAnswer(boolean worked) {
                answer = worked;
                box.setForeground(worked ? GraphRangeChip.ACCENT : TYPED);
                box.repaint();
            }

            void paintLabel(Graphics2D g) {
                double cy = top + s(ROW) / 2.0;
                Font f = axisFont(15f * scale());
                FontMetrics m = g.getFontMetrics(f);
                g.setFont(f);
                g.setColor(INK);
                g.drawString(name, s(PAD) + s(2), (float) (cy + (m.getAscent() - m.getDescent()) / 2.0));
            }

            /** Grey rounded box like the range boxes; a worked-out answer shows in blue on a blue tint. */
            final class Box extends JTextField {
                Box() {
                    setOpaque(false);
                    setHorizontalAlignment(SwingConstants.RIGHT);
                    setForeground(TYPED);
                    setCaretColor(GraphRangeChip.ACCENT);
                    setSelectionColor(new Color(0, 122, 255, 60));
                    setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 10));
                    addActionListener(e -> find());
                    addKeyListener(new KeyAdapter() {
                        @Override
                        public void keyPressed(KeyEvent e) {
                            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                                close();
                            }
                        }
                    });
                    addFocusListener(new FocusAdapter() {
                        @Override
                        public void focusGained(FocusEvent e) {
                            repaint();
                        }

                        @Override
                        public void focusLost(FocusEvent e) {
                            repaint();
                        }
                    });
                }

                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    double arc = s(9);
                    RoundRectangle2D shape = new RoundRectangle2D.Double(0.75, 0.75, getWidth() - 1.5,
                            getHeight() - 1.5, arc, arc);
                    boolean focused = isFocusOwner();
                    g.setColor(focused ? Color.WHITE : answer ? new Color(0, 122, 255, 22) : new Color(241, 243, 246));
                    g.fill(shape);
                    if (focused) {
                        g.setColor(GraphRangeChip.ACCENT);
                        g.setStroke(new BasicStroke(1.5f));
                        g.draw(shape);
                    }
                    if (getText().isEmpty() && !focused) {
                        Font f = getFont();
                        FontMetrics m = g.getFontMetrics(f);
                        g.setFont(f);
                        g.setColor(FAINT);
                        String blank = "?";
                        g.drawString(blank, getWidth() - 10 - m.stringWidth(blank),
                                (getHeight() + m.getAscent() - m.getDescent()) / 2);
                    }
                    g.dispose();
                    super.paintComponent(g0);
                }
            }
        }
    }
}
