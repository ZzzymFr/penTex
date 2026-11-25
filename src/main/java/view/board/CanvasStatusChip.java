package view.board;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The canvas's name and save state, top left, like a macOS document title: document icon, name in
 * bold with a ⌄ (it opens Open / Rename), and a status line under it with its own small glyph.
 * Same glass as the History / Save / Open bar beside it. In a narrow window it folds into a round
 * icon with a status badge instead of disappearing. Only presentation and click forwarding here.
 */
final class CanvasStatusChip extends JComponent {
    static final int HEIGHT = 44;
    private static final Color MUTED = new Color(0, 0, 0, 128);
    private static final Color EDITED = new Color(255, 149, 0);   // system orange, like the unsaved dot
    private static final Color SAVED = new Color(52, 199, 89);    // system green, quiet check
    private static final int PAD_L = 12;
    private static final int ICON_W = 18;
    private static final int GAP = 8;
    private static final int PAD_R = 12;
    private static final int MAX_W = 230;

    private SaveMenuState state = SaveMenuState.newCanvas();
    private SaveMenuState previous;           // status line we're fading out of
    private boolean hovered;
    private boolean pressed;
    private boolean compact;
    private long lastSavedAt = System.currentTimeMillis();
    private Runnable renameAction = () -> {};
    /** Uses the very same backdrop source as the adjacent History/Save/Open glass. */
    private Consumer<Graphics2D> backdrop;
    private LongSupplier backdropVersion;
    private BufferedImage glassImage;
    private long paintedVersion = Long.MIN_VALUE;
    private int paintedX = Integer.MIN_VALUE, paintedY = Integer.MIN_VALUE;

    // status line cross-fades when the state changes (Edited -> Saved), instead of snapping
    private final Spring fade = new Spring(0.004f, true);
    private final Motion.Clock fadeClock = new Motion.Clock();
    private final Timer fadeTimer = new Timer(Motion.FRAME_MS, e -> stepFade());
    // "Saved · 3 min ago" stays current
    private final Timer clock = new Timer(30_000, e -> { updateTooltip(); repaint(); });

    CanvasStatusChip() {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fade.snap(1f);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovered = false; pressed = false; repaint(); }
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) { pressed = true; repaint(); }  // feedback on the way down
            }
            @Override public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) renameAction.run();
            }
        });
        updateTooltip();
    }

    void setGlassBackdrop(Consumer<Graphics2D> paint, LongSupplier version) {
        backdrop = Objects.requireNonNull(paint);
        backdropVersion = Objects.requireNonNull(version);
        glassImage = null;
        repaint();
    }

    void setRenameAction(Runnable action) { renameAction = Objects.requireNonNull(action); }

    /** Narrow window: just the round document icon with a badge; name and status go to the tooltip. */
    void setCompact(boolean compact) {
        if (this.compact != compact) {
            this.compact = compact;
            updateTooltip();
            revalidate();
            repaint();
        }
    }

    boolean isCompact() { return compact; }

    void setState(SaveMenuState next) {
        Objects.requireNonNull(next);
        if (next.phase() == SaveMenuState.Phase.SAVED && state.phase() != SaveMenuState.Phase.SAVED)
            lastSavedAt = System.currentTimeMillis();
        if (next.phase() != state.phase()) {
            previous = state;
            if (isShowing() && !Motion.reduced()) {
                fade.snap(0f);
                fadeClock.reset();
                fadeTimer.start();
            } else {
                fade.snap(1f);
            }
        }
        state = next;
        updateTooltip();
        revalidate();
        repaint();
    }

    SaveMenuState state() { return state; }

    @Override public void addNotify() { super.addNotify(); clock.start(); }
    @Override public void removeNotify() { clock.stop(); fadeTimer.stop(); super.removeNotify(); }

    private void stepFade() {
        if (fade.step(1f, Motion.ENTER, fadeClock.tick())) {
            fadeTimer.stop();
            previous = null;
        }
        repaint();
    }

    private void updateTooltip() {
        setToolTipText(compact ? state.name() + " — " + subtitle(state) + " (click to open or rename)"
                : "Open saved canvases or rename this one");
    }

    @Override public Dimension getPreferredSize() {
        if (compact) return new Dimension(HEIGHT, HEIGHT);
        FontMetrics title = getFontMetrics(BoardTheme.semibold(13.5f));
        FontMetrics small = getFontMetrics(BoardTheme.font(11f));
        int text = Math.max(title.stringWidth(fittedName()) + 16, small.stringWidth(subtitle(state)) + 14);
        int width = PAD_L + ICON_W + GAP + text + PAD_R;
        return new Dimension(Math.max(150, Math.min(MAX_W, width)), HEIGHT);
    }

    private String fittedName() {
        String name = state.name();
        return name.codePointCount(0, name.length()) <= 20 ? name : state.compactName();
    }

    private String subtitle(SaveMenuState s) {
        return switch (s.phase()) {
            case NEW -> "Not saved yet";
            case MODIFIED -> "Edited";
            case SAVING -> "Saving…";
            case SAVED -> {
                long minutes = Math.max(0, (System.currentTimeMillis() - lastSavedAt) / 60000L);
                yield minutes == 0 ? "Saved just now" : minutes < 60
                        ? "Saved " + minutes + " min ago" : "Saved " + (minutes / 60) + " hr ago";
            }
        };
    }

    // what's behind, blurred, so it reads as the same glass as the bar next to it
    private BufferedImage glassBackdrop(int w, int h) {
        if (backdrop == null || backdropVersion == null) return null;
        long version = backdropVersion.getAsLong();
        if (glassImage != null && glassImage.getWidth() == w && glassImage.getHeight() == h
                && paintedX == getX() && paintedY == getY() && paintedVersion == version)
            return glassImage;
        BufferedImage snapshot = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D source = snapshot.createGraphics();
        try {
            source.setColor(BoardTheme.CANVAS);
            source.fillRect(0, 0, w, h);
            source.translate(-getX(), -getY());
            backdrop.accept(source);
        } finally { source.dispose(); }
        // Small static blur, cached per actual backdrop change, never per clock tick.
        float[] kernel = new float[25];
        java.util.Arrays.fill(kernel, 1f / kernel.length);
        glassImage = new ConvolveOp(new Kernel(5, 5, kernel), ConvolveOp.EDGE_NO_OP, null)
                .filter(snapshot, null);
        paintedVersion = version;
        paintedX = getX(); paintedY = getY();
        return glassImage;
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int w = getWidth(), h = getHeight();
            if (w < 4 || h < 4) return;
            RoundRectangle2D body = new RoundRectangle2D.Double(1, 1, w - 2.0, h - 3.0, h - 3, h - 3);
            paintGlass(g, body, w, h);
            if (compact) {
                paintCompact(g, body);
            } else {
                paintFull(g, w);
            }
        } finally { g.dispose(); }
    }

    // frosted capsule: blurred scene, white veil, top gloss, bright rim; corners stay transparent
    private void paintGlass(Graphics2D g, RoundRectangle2D body, int w, int h) {
        for (int i = 1; i <= 2; i++) {  // soft lift, not a hard drop shadow
            g.setColor(new Color(0, 0, 0, 7));
            g.fill(new RoundRectangle2D.Double(body.getX(), body.getY() + i, body.getWidth(), body.getHeight(),
                    body.getArcWidth(), body.getArcHeight()));
        }
        java.awt.Shape prior = g.getClip();
        g.clip(body);
        BufferedImage scene = glassBackdrop(w, h);
        if (scene != null) g.drawImage(scene, 0, 0, null);
        int veil = pressed ? 150 : hovered ? 196 : 172;
        g.setColor(new Color(255, 255, 255, veil));
        g.fill(body);
        if (pressed) {
            g.setColor(new Color(0, 0, 0, 12));
            g.fill(body);
        }
        g.setPaint(new GradientPaint(0, (float) body.getY(), new Color(255, 255, 255, BoardTheme.GLOSS),
                0, (float) (body.getY() + body.getHeight() * 0.55), new Color(255, 255, 255, 0)));
        g.fill(body);
        g.setClip(prior);
        g.setStroke(new BasicStroke(1f));
        g.setColor(BoardTheme.EDGE);
        g.draw(body);
        g.setPaint(new GradientPaint(0, (float) body.getY(), new Color(255, 255, 255, BoardTheme.RIM_TOP),
                0, (float) (body.getY() + body.getHeight()), new Color(255, 255, 255, BoardTheme.RIM_BOTTOM)));
        g.draw(new RoundRectangle2D.Double(body.getX() + 1, body.getY() + 1, body.getWidth() - 2,
                body.getHeight() - 2, body.getArcWidth() - 2, body.getArcHeight() - 2));
    }

    private void paintFull(Graphics2D g, int w) {
        int mid = (HEIGHT - 2) / 2;
        // document icon, left, centred on the capsule
        Graphics2D icon = (Graphics2D) g.create();
        icon.translate(PAD_L + ICON_W / 2.0, mid);
        icon.scale(0.9, 0.9);
        icon.setColor(new Color(0, 0, 0, 170));
        Icons.document(icon);
        icon.dispose();

        int x = PAD_L + ICON_W + GAP;
        int textRight = w - PAD_R;
        // name: the emphasis is weight, the status underneath stays quiet
        g.setFont(BoardTheme.semibold(13.5f));
        FontMetrics title = g.getFontMetrics();
        String name = fittedName();
        int maxName = textRight - x - 14;
        while (title.stringWidth(name) > maxName && name.length() > 2) {
            name = name.substring(0, name.length() - 2) + "…";
        }
        g.setColor(BoardTheme.LABEL);
        int baseline = 19;
        g.drawString(name, x, baseline);
        // ⌄ right after the name: this opens Open / Rename, like a macOS title menu
        double cx = x + title.stringWidth(name) + 9;
        double cy = baseline - 4.5;
        g.setColor(hovered ? new Color(0, 0, 0, 190) : new Color(0, 0, 0, 105));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D chevron = new Path2D.Double();
        chevron.moveTo(cx - 3.2, cy - 1.6);
        chevron.lineTo(cx, cy + 1.6);
        chevron.lineTo(cx + 3.2, cy - 1.6);
        g.draw(chevron);

        // status line, cross-fading from the previous one
        float in = Math.max(0f, Math.min(1f, fade.x));
        if (previous != null && in < 1f) {
            statusLine(g, previous, x, 1f - in);
        }
        statusLine(g, state, x, in);
    }

    private void statusLine(Graphics2D g0, SaveMenuState s, int x, float alpha) {
        if (alpha <= 0.01f) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setComposite(java.awt.AlphaComposite.SrcOver.derive(alpha));
        int baseline = 34;
        double gy = baseline - 4;
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (s.phase()) {
            case NEW -> {
                g.setColor(new Color(0, 0, 0, 90));  // dashed ring: nothing on disk yet
                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                        new float[]{1.6f, 2.2f}, 0f));
                g.draw(new Ellipse2D.Double(x, gy - 3.5, 7, 7));
            }
            case MODIFIED -> {
                g.setColor(EDITED);
                g.fill(new Ellipse2D.Double(x + 0.5, gy - 3, 6, 6));
            }
            case SAVING -> {
                g.setColor(new Color(0, 0, 0, 120));
                g.draw(new java.awt.geom.Arc2D.Double(x, gy - 3.5, 7, 7, 90, -270, java.awt.geom.Arc2D.OPEN));
            }
            case SAVED -> {
                g.setColor(SAVED);
                Path2D check = new Path2D.Double();
                check.moveTo(x, gy);
                check.lineTo(x + 2.6, gy + 2.6);
                check.lineTo(x + 7.5, gy - 3);
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(check);
            }
        }
        g.setFont(BoardTheme.font(11f));
        g.setColor(MUTED);
        g.drawString(subtitle(s), x + 12, baseline);
        g.dispose();
    }

    // folded: document icon in the round glass, with the status as a small badge
    private void paintCompact(Graphics2D g, RoundRectangle2D body) {
        double cx = body.getCenterX();
        double cy = body.getCenterY();
        Graphics2D icon = (Graphics2D) g.create();
        icon.translate(cx, cy);
        icon.setColor(new Color(0, 0, 0, 170));
        Icons.document(icon);
        icon.dispose();
        double bx = cx + 7;
        double by = cy - 9;
        switch (state.phase()) {
            case MODIFIED -> badge(g, bx, by, EDITED);
            case SAVING -> badge(g, bx, by, new Color(142, 142, 147));
            case NEW -> { }
            case SAVED -> { }
        }
    }

    private static void badge(Graphics2D g, double x, double y, Color color) {
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(x - 5, y - 5, 10, 10));
        g.setColor(color);
        g.fill(new Ellipse2D.Double(x - 3.5, y - 3.5, 7, 7));
    }
}
