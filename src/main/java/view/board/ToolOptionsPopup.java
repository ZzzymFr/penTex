package view.board;

import view.DrawingCanvas;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.function.Consumer;

/** Presentation of tool settings. Ink operations remain owned by DrawingCanvas. */
final class ToolOptionsPopup extends JPopupMenu {
    private static final Color BLUE = BoardTheme.ACCENT;
    private static final Color INK = new Color(0x26282D);
    private static final Color MUTED = new Color(0x747781);
    private static final Color TRACK = new Color(0xF0F0F4);
    private static final Color SELECTED = new Color(0xEAF2FF);
    private static final int WIDTH = 352;
    // GlassBar reserves MARGIN around the visual capsule. Anchor to its visible
    // edge, not the outer component bounds, to avoid an exaggerated empty gap.
    static final int TOOLBAR_GAP = 5;
    private final DrawingCanvas canvas;
    private final Consumer<Color> colorChanged;
    // A native/heavyweight macOS popup can paint an opaque rectangular backing even
    // when JPopupMenu and the rounded content are non-opaque.  Use the root's
    // existing layered pane on macOS instead of allocating a native popup window.
    private Overlay macOverlay;
    private JLayeredPane macLayer;
    private AWTEventListener outsidePress;

    /** Grows out of the tool that opened it and shrinks back into it; same path in and out. */
    private static final class Overlay extends JPanel {
        private final Spring appear = new Spring(0.004f, true);
        private final Motion.Clock clock = new Motion.Clock();
        private final javax.swing.Timer timer = new FrameTimer(e -> step());
        private float target;
        private Point origin = new Point();   // the tool, in this panel's coords
        private Runnable gone = () -> { };
        boolean closing;

        Overlay() {
            super(new java.awt.BorderLayout());
            setOpaque(false);
            setBackground(new Color(0, 0, 0, 0));
        }

        void open(Point from) {
            origin = from;
            closing = false;
            target = 1f;
            if (!timer.isRunning() && appear.x >= 1f) appear.snap(0f);  // fresh open, not a re-grab
            timer.start();
        }

        void close(Runnable after) {
            closing = true;
            target = 0f;
            gone = after;
            timer.start();
        }

        private void step() {
            boolean still = appear.step(target, Motion.ENTER, clock.tick());
            repaint();
            if (still || (target == 0f && appear.x < 0.02f)) {
                timer.stop();
                clock.reset();
                if (target == 0f) {
                    appear.snap(0f);
                    gone.run();
                } else {
                    appear.snap(1f);
                }
            }
        }

        // on its way out it's just a picture, clicks go to what's under it
        @Override public boolean contains(int x, int y) {
            return !closing && super.contains(x, y);
        }

        @Override public void paint(Graphics g) {
            float a = Math.max(0f, Math.min(1f, appear.x));
            if (a >= 0.999f) {
                super.paint(g);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(a));
            // Keep the visual and input coordinates identical during the fade.
            super.paint(g2);
            g2.dispose();
        }
    }

    ToolOptionsPopup(DrawingCanvas canvas, Consumer<Color> colorChanged) {
        this.canvas = canvas;
        this.colorChanged = colorChanged;
        setUI(new javax.swing.plaf.basic.BasicPopupMenuUI());
        setBorder(BorderFactory.createEmptyBorder());
        setBackground(new Color(0, 0, 0, 0));
        setOpaque(false);
        setLightWeightPopupEnabled(true);
        setFocusable(false);
    }

    /**
     * Opens beside the tool, on the side of the bar that faces the canvas: above a bottom bar,
     * below a top one, out to the side of an upright one. capsule = the bar's visible capsule and
     * toolAt = the clicked tool's centre, both in the bar's coords.
     */
    void showFor(DrawingCanvas.Tool tool, Component bar, ToolbarSpot spot,
                 java.awt.Rectangle capsule, Point toolAt) {
        rebuild(tool);
        JRootPane root = bar.isShowing() ? SwingUtilities.getRootPane(bar) : null;
        if (root != null) {
            // All painting belongs to the existing window's transparent Swing layer (same on
            // Windows and macOS): no heavyweight native popup with a square backing.
            JLayeredPane layer = root.getLayeredPane();
            if (macOverlay == null) {
                macOverlay = new Overlay();
            }
            Component content = getComponentCount() > 0 ? getComponent(0) : null;
            if (content != null) {
                remove(content);
                macOverlay.removeAll();
                macOverlay.add(content, java.awt.BorderLayout.CENTER);
            }
            if (macLayer != null && macLayer != layer) macLayer.remove(macOverlay);
            macLayer = layer;
            RoundedSurface card = macOverlay.getComponentCount() > 0
                    && macOverlay.getComponent(0) instanceof RoundedSurface r ? r : null;
            if (card != null) card.pointTo(spot);
            Dimension size = macOverlay.getPreferredSize();
            java.awt.Rectangle cap = SwingUtilities.convertRectangle(bar, capsule, layer);
            Point at = SwingUtilities.convertPoint(bar, toolAt, layer);
            int left;
            int top;
            switch (spot) {
                case TOP -> {
                    left = at.x - size.width / 2;
                    top = cap.y + cap.height + TOOLBAR_GAP;
                }
                case LEFT -> {
                    left = cap.x + cap.width + TOOLBAR_GAP;
                    top = at.y - size.height / 2;
                }
                case RIGHT -> {
                    left = cap.x - size.width - TOOLBAR_GAP;
                    top = at.y - size.height / 2;
                }
                default -> {
                    left = at.x - size.width / 2;
                    top = cap.y - size.height - TOOLBAR_GAP;
                }
            }
            left = Math.max(0, Math.min(left, layer.getWidth() - size.width));
            top = Math.max(0, Math.min(top, layer.getHeight() - size.height));
            macOverlay.setBounds(left, top, size.width, size.height);
            if (card != null) {
                card.tipAt(spot.isVertical() ? at.y - top : at.x - left);
            }
            if (macOverlay.getParent() != layer) layer.add(macOverlay, JLayeredPane.POPUP_LAYER);
            macOverlay.setVisible(true);
            // the edge point nearest the tool: where it grows out of
            Point origin = new Point(Math.max(0, Math.min(at.x - left, size.width)),
                    Math.max(0, Math.min(at.y - top, size.height)));
            macOverlay.open(origin);
            macOverlay.revalidate();
            macOverlay.repaint();
            listenForOutsidePress();
            return;
        }
        show(bar, toolAt.x - getPreferredSize().width / 2, capsule.y - getPreferredSize().height - TOOLBAR_GAP);
    }

    private void listenForOutsidePress() {
        if (outsidePress != null) return;
        outsidePress = event -> {
            if (event instanceof MouseEvent mouse && mouse.getID() == MouseEvent.MOUSE_PRESSED
                    && mouse.getSource() instanceof Component source
                    && macOverlay != null && !SwingUtilities.isDescendingFrom(source, macOverlay)) {
                setVisible(false);
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(outsidePress, AWTEvent.MOUSE_EVENT_MASK);
    }

    @Override public boolean isVisible() {
        return super.isVisible() || (macOverlay != null && macOverlay.isVisible()
                && macOverlay.getParent() != null && !macOverlay.closing);
    }

    @Override public void setVisible(boolean visible) {
        if (!visible) {
            if (outsidePress != null) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(outsidePress);
                outsidePress = null;
            }
            if (macOverlay != null && macLayer != null && macOverlay.getParent() == macLayer
                    && !macOverlay.closing) {
                Overlay leaving = macOverlay;
                JLayeredPane layer = macLayer;
                // shrinks back into its tool, then leaves the layer
                leaving.close(() -> {
                    if (leaving.closing) {
                        leaving.setVisible(false);
                        layer.remove(leaving);
                        layer.repaint();
                        if (macLayer == layer && !isVisible()) macLayer = null;
                    }
                });
            }
        }
        super.setVisible(visible);
    }

    private void rebuild(DrawingCanvas.Tool tool) {
        removeAll();
        RoundedSurface surface = new RoundedSurface();
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(18, 19, 17, 19));
        switch (tool) {
            case PEN -> penOptions(body);
            case ERASER -> eraserOptions(body);
            case LASSO, RECTANGLE -> selectionOptions(body, tool);
        }
        surface.add(body);
        if (macLayer != null && macOverlay != null) {
            macOverlay.removeAll();
            macOverlay.add(surface, java.awt.BorderLayout.CENTER);
            macOverlay.revalidate();
            macOverlay.repaint();
        } else {
            add(surface);
            revalidate();
            repaint();
        }
    }

    private void penOptions(JPanel body) {
        body.add(new StrokePreview(canvas.getPenWidth(), canvas.getInkColor()));
        body.add(space(14));
        body.add(heading("Thickness", formatPt(canvas.getPenWidth())));
        body.add(space(7));
        JPanel sizes = controlTrack();
        for (int width : new int[]{1, 2, 4, 7, 10}) {
            JButton b = dotSize(width, Math.round(canvas.getPenWidth()) == width, false);
            b.setToolTipText(width + " pt");
            b.addActionListener(e -> { canvas.setPenWidth(width); refresh(DrawingCanvas.Tool.PEN); });
            sizes.add(b);
        }
        body.add(sizes);
        body.add(space(17));
        body.add(heading("Color", ""));
        body.add(space(9));
        JPanel colors = new JPanel(new GridLayout(1, 7, 8, 0));
        colors.setOpaque(false);
        colors.setAlignmentX(Component.LEFT_ALIGNMENT);
        colors.setPreferredSize(new Dimension(WIDTH - 38, 39));
        colors.setMaximumSize(new Dimension(WIDTH, 39));
        for (Color c : new Color[]{new Color(0x1C1C1E), new Color(0xFF3B30), BLUE,
                new Color(0x34C759), new Color(0xFF9500), new Color(0xAF52DE)}) {
            JButton b = colorDot(c, sameSwatch(c, canvas.getInkColor()), false);
            b.addActionListener(e -> { colorChanged.accept(c); setVisible(false); });
            colors.add(b);
        }
        JButton wheel = colorDot(null, false, true);
        wheel.addActionListener(e -> {
            setVisible(false);  // the sheet replaces the popover
            Color selected = view.glass.GlassColorPicker.pick(canvas, "Pen Color", canvas.getInkColor());
            if (selected != null) { colorChanged.accept(selected); setVisible(false); }
        });
        colors.add(wheel);
        body.add(colors);
    }

    private void eraserOptions(JPanel body) {
        body.add(heading("Eraser", "mode · size"));
        body.add(space(9));
        JPanel modes = twoCards();
        modes.add(modeCard("Stroke", "Removes a whole", "line in one touch",
                canvas.getEraserMode() == DrawingCanvas.EraserMode.STROKE, false,
                () -> { canvas.setEraserMode(DrawingCanvas.EraserMode.STROKE); refresh(DrawingCanvas.Tool.ERASER); }));
        modes.add(modeCard("Precise", "Erases only what", "it passes over",
                canvas.getEraserMode() == DrawingCanvas.EraserMode.PRECISE, false,
                () -> { canvas.setEraserMode(DrawingCanvas.EraserMode.PRECISE); refresh(DrawingCanvas.Tool.ERASER); }));
        body.add(modes);
        body.add(space(18));
        body.add(heading("Size", canvas.getEraserSize() + " pt"));
        body.add(space(8));
        JPanel sizes = controlTrack();
        for (int value : new int[]{4, 8, 16, 24, 32}) {
            JButton b = dotSize(value, canvas.getEraserSize() == value, true);
            b.setToolTipText(value + " pt");
            b.addActionListener(e -> { canvas.setEraserSize(value); refresh(DrawingCanvas.Tool.ERASER); });
            sizes.add(b);
        }
        body.add(sizes);
    }

    private void selectionOptions(JPanel body, DrawingCanvas.Tool tool) {
        body.add(heading("Lasso", "freeform or box"));
        body.add(space(10));
        JPanel choices = twoCards();
        choices.add(modeCard("Freeform", "Draw around what", "you want",
                tool == DrawingCanvas.Tool.LASSO, true,
                () -> { canvas.setTool(DrawingCanvas.Tool.LASSO); refresh(DrawingCanvas.Tool.LASSO); }));
        choices.add(modeCard("Box", "Drag a rectangle", "to select",
                tool == DrawingCanvas.Tool.RECTANGLE, true,
                () -> { canvas.setTool(DrawingCanvas.Tool.RECTANGLE); refresh(DrawingCanvas.Tool.RECTANGLE); }));
        body.add(choices);
    }

    /** Rebuild the visible controls after a setting changes, leaving the settings panel open. */
    private void refresh(DrawingCanvas.Tool tool) {
        rebuild(tool);
    }

    private static JPanel twoCards() {
        JPanel row = new JPanel(new GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(WIDTH, 145));
        row.setPreferredSize(new Dimension(WIDTH - 38, 145));
        return row;
    }

    private static JButton modeCard(String title, String description1, String description2,
                                    boolean chosen, boolean selection, Runnable action) {
        JButton button = new JButton() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = graphics2(graphics);
                g.setColor(chosen ? SELECTED : TRACK);
                g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 17, 17);
                if (chosen) {
                    g.setColor(BLUE);
                    g.setStroke(new BasicStroke(1.8f));
                    g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 17, 17);
                }
                g.setColor(chosen ? BLUE : INK);
                g.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (selection) {
                    if ("Box".equals(title)) {
                        float[] dash = {5, 4};
                        g.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, dash, 0));
                        g.drawRect(20, 19, 24, 24);
                    } else {
                        g.drawOval(19, 20, 27, 15);
                        g.drawLine(28, 34, 31, 43);
                    }
                } else {
                    Path2D p = new Path2D.Double();
                    p.moveTo(19, 31); p.curveTo(29, 17, 37, 42, 52, 29);
                    if ("Precise".equals(title)) {
                        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                                1f, new float[]{5, 5}, 0));
                    }
                    g.draw(p);
                }
                drawText(g, title, 18, 76, 15, Font.BOLD, INK);
                drawText(g, description1, 18, 101, 11.5f, Font.PLAIN, MUTED);
                drawText(g, description2, 18, 117, 11.5f, Font.PLAIN, MUTED);
                g.dispose();
            }
        };
        button.setPreferredSize(new Dimension(145, 145));
        button.setToolTipText(title);
        bare(button);
        button.addActionListener(e -> action.run());
        return button;
    }

    private static JPanel controlTrack() {
        JPanel row = new JPanel(new GridLayout(1, 5, 3, 0)) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = graphics2(graphics);
                g.setColor(TRACK);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        row.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setPreferredSize(new Dimension(WIDTH - 38, 55));
        row.setMaximumSize(new Dimension(WIDTH, 55));
        return row;
    }

    private static JButton dotSize(int size, boolean selected, boolean outline) {
        JButton button = new JButton() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = graphics2(graphics);
                if (selected) {
                    g.setColor(Color.WHITE);
                    g.fillRoundRect(1, 1, getWidth()-2, getHeight()-2, 13, 13);
                    g.setColor(new Color(0,0,0,15));
                    g.drawRoundRect(1, 1, getWidth()-2, getHeight()-2, 13, 13);
                }
                int diameter = outline ? Math.min(30, 5 + size * 2 / 3) : Math.min(25, 3 + size * 2);
                int cx = getWidth()/2, cy = getHeight()/2;
                g.setColor(outline && selected ? BLUE : INK);
                if (outline) {
                    g.setStroke(new BasicStroke(1.7f));
                    g.drawOval(cx-diameter/2,cy-diameter/2,diameter,diameter);
                } else g.fillOval(cx-diameter/2,cy-diameter/2,diameter,diameter);
                g.dispose();
            }
        };
        bare(button);
        return button;
    }

    private static boolean sameSwatch(Color swatch, Color current) {
        return swatch.equals(current) || (swatch.getRed() < 40 && swatch.getGreen() < 40
                && swatch.getBlue() < 40 && current.getRed() < 40
                && current.getGreen() < 40 && current.getBlue() < 40);
    }

    private static JButton colorDot(Color color, boolean selected, boolean wheel) {
        JButton button = new JButton() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = graphics2(graphics);
                int d = Math.min(31, Math.min(getWidth()-8, getHeight()-8));
                int x=(getWidth()-d)/2,y=(getHeight()-d)/2;
                if (wheel) {
                    for (int h=0;h<360;h+=3) {
                        g.setColor(Color.getHSBColor(h/360f,1,1));
                        g.fillArc(x,y,d,d,h,4);
                    }
                    g.setColor(new Color(255,255,255,70));g.fillOval(x+d/4,y+d/4,d/2,d/2);
                } else {
                    g.setColor(color);g.fillOval(x,y,d,d);
                }
                if (selected) {
                    g.setStroke(new BasicStroke(2.3f));g.setColor(BLUE);
                    g.drawOval(x-3,y-3,d+6,d+6);
                }
                g.dispose();
            }
        };
        bare(button);
        return button;
    }

    private static JPanel heading(String left, String right) {
        JPanel row = new JPanel(new java.awt.BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(WIDTH, 24));
        JLabel label = new JLabel(left);
        label.setForeground(MUTED);label.setFont(BoardTheme.font(12f).deriveFont(Font.BOLD));
        JLabel count = new JLabel(right, SwingConstants.RIGHT);
        count.setForeground(MUTED);count.setFont(BoardTheme.font(12f));
        row.add(label, java.awt.BorderLayout.WEST);
        row.add(count, java.awt.BorderLayout.EAST);
        return row;
    }

    private static Component space(int n) { return Box.createRigidArea(new Dimension(0,n)); }
    private static String formatPt(float pt) { return (int)pt == pt ? (int)pt + " pt" : pt + " pt"; }
    private static void bare(JButton b) {
        b.setContentAreaFilled(false);b.setBorderPainted(false);b.setFocusPainted(false);b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder());
    }
    private static Graphics2D graphics2(Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }
    private static void drawText(Graphics2D g,String text,int x,int y,float size,int style,Color color) {
        g.setFont(BoardTheme.font(size).deriveFont(style));g.setColor(color);g.drawString(text,x,y);
    }

    /** One self-painted rounded card, avoiding the native white menu-item surface. */
    static final class RoundedSurface extends JPanel {
        private static final int TIP = 17;
        // which edge of the card the tip sits on (the side facing the toolbar), and where along it
        private ToolbarSpot facing = ToolbarSpot.BOTTOM;
        private int tipAlong = -1;   // -1 = middle

        RoundedSurface() {
            super(new java.awt.BorderLayout());
            setOpaque(false);
            pointTo(ToolbarSpot.BOTTOM);
        }

        /** The toolbar is on this side of the window: the card opens away from it, tip toward it. */
        void pointTo(ToolbarSpot toolbar) {
            facing = switch (toolbar) {
                case TOP -> ToolbarSpot.TOP;
                case LEFT -> ToolbarSpot.LEFT;
                case RIGHT -> ToolbarSpot.RIGHT;
                default -> ToolbarSpot.BOTTOM;
            };
            int t = 10, l = 10, b = 10, r = 10;
            switch (facing) {
                case TOP -> t += TIP - 3;
                case LEFT -> l += TIP - 3;
                case RIGHT -> r += TIP - 3;
                default -> b += TIP - 3;
            }
            setBorder(BorderFactory.createEmptyBorder(t, l, b, r));
        }

        void tipAt(int along) {
            tipAlong = along;
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g=graphics2(graphics);
            int x = 1, y = 1, w = getWidth() - 2, h = getHeight() - 2;
            switch (facing) {
                case TOP -> { y += TIP; h -= TIP; }
                case LEFT -> { x += TIP; w -= TIP; }
                case RIGHT -> w -= TIP;
                default -> h -= TIP;
            }
            boolean across = facing == ToolbarSpot.TOP || facing == ToolbarSpot.BOTTOM;
            int length = across ? getWidth() : getHeight();
            int at = tipAlong < 0 ? length / 2 : Math.max(26, Math.min(tipAlong, length - 26));
            Path2D tip = new Path2D.Double();
            switch (facing) {
                case TOP -> { tip.moveTo(at - 12, y + 1); tip.lineTo(at, y - 12); tip.lineTo(at + 12, y + 1); }
                case LEFT -> { tip.moveTo(x + 1, at - 12); tip.lineTo(x - 12, at); tip.lineTo(x + 1, at + 12); }
                case RIGHT -> { tip.moveTo(x + w - 1, at - 12); tip.lineTo(x + w + 12, at); tip.lineTo(x + w - 1, at + 12); }
                default -> { tip.moveTo(at - 12, y + h - 1); tip.lineTo(at, y + h + 12); tip.lineTo(at + 12, y + h - 1); }
            }
            tip.closePath();
            // No artificial drop shadow or dark backing rectangle. Retain the
            // rounded card and the small white tip, including on Retina/Aqua.
            g.setColor(Color.WHITE);
            g.fillRoundRect(x, y, w, h, 23, 23);
            g.fill(tip);
            g.setColor(new Color(0, 0, 0, 26));
            g.setStroke(new BasicStroke(1));
            g.drawRoundRect(x, y, w - 1, h - 1, 23, 23);
            g.dispose();
        }
    }

    private final class StrokePreview extends JComponent {
        private final float width;
        private final Color ink;
        StrokePreview(float width,Color ink) {
            this.width=width;this.ink=ink;
            setPreferredSize(new Dimension(ToolOptionsPopup.WIDTH-38,63));
            setMaximumSize(new Dimension(ToolOptionsPopup.WIDTH,63));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g=graphics2(graphics);
            g.setColor(TRACK);
            g.fillRoundRect(0,0,getWidth(),getHeight(),15,15);
            g.setColor(ink);
            g.setStroke(new BasicStroke(width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            Path2D curve=new Path2D.Double();
            double w=getWidth();
            curve.moveTo(w*.08,37);
            curve.curveTo(w*.23,5,w*.30,55,w*.46,30);
            curve.curveTo(w*.63,-1,w*.72,55,w*.86,30);
            curve.curveTo(w*.91,24,w*.94,18,w*.95,14);
            g.draw(curve);g.dispose();
        }
    }
}
