package view.board;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.GradientPaint;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Objects;

/** Compact, Apple-style two-line status capsule. Only presentation and click forwarding live here. */
final class CanvasStatusChip extends JComponent {
    private static final Color INK = new Color(35, 38, 45);
    private static final Color MUTED = new Color(114, 118, 128);
    private SaveMenuState state = SaveMenuState.newCanvas();
    private boolean hovered;
    private long lastSavedAt = System.currentTimeMillis();
    private Runnable renameAction = () -> {};
    /** Uses the very same backdrop source as the adjacent History/Save/Open glass. */
    private Consumer<Graphics2D> backdrop;
    private LongSupplier backdropVersion;
    private BufferedImage glassImage;
    private long paintedVersion = Long.MIN_VALUE;
    private int paintedX = Integer.MIN_VALUE, paintedY = Integer.MIN_VALUE;

    private int spinnerAngle;
    private final Timer clock = new Timer(1000, e -> {
        if (state.phase() == SaveMenuState.Phase.SAVING) spinnerAngle = (spinnerAngle + 40) % 360;
        repaint();
    });

    CanvasStatusChip() {
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Open saved canvases to rename");
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
            @Override public void mouseClicked(MouseEvent e) {
                if (javax.swing.SwingUtilities.isLeftMouseButton(e)) renameAction.run();
            }
        });
    }

    void setGlassBackdrop(Consumer<Graphics2D> paint, LongSupplier version) {
        backdrop = Objects.requireNonNull(paint);
        backdropVersion = Objects.requireNonNull(version);
        glassImage = null;
        repaint();
    }
    void setRenameAction(Runnable action) { renameAction = Objects.requireNonNull(action); }

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
        // Small static blur is cached per actual backdrop change, never per status-clock tick.
        float[] kernel = new float[25];
        java.util.Arrays.fill(kernel, 1f / kernel.length);
        glassImage = new ConvolveOp(new Kernel(5, 5, kernel), ConvolveOp.EDGE_NO_OP, null)
                .filter(snapshot, null);
        paintedVersion = version;
        paintedX = getX(); paintedY = getY();
        return glassImage;
    }
    void setState(SaveMenuState next) {
        Objects.requireNonNull(next);
        if (next.phase() == SaveMenuState.Phase.SAVED && state.phase() != SaveMenuState.Phase.SAVED)
            lastSavedAt = System.currentTimeMillis();
        state = next;
        revalidate();
        repaint();
    }
    SaveMenuState state() { return state; }
    @Override public void addNotify() { super.addNotify(); clock.start(); }
    @Override public void removeNotify() { clock.stop(); super.removeNotify(); }

    @Override public Dimension getPreferredSize() {
        String name = fittedName();
        FontMetrics metrics = getFontMetrics(BoardTheme.font(13.5f));
        int width = Math.max(134, metrics.stringWidth(name) + 46);
        return new Dimension(Math.min(205, width), 44);
    }
    private String fittedName() {
        String name = state.name();
        return name.codePointCount(0, name.length()) <= 20 ? name : state.compactName();
    }
    private String subtitle() {
        return switch (state.phase()) {
            case NEW -> "Not saved yet";
            case MODIFIED -> "Edited";
            case SAVING -> "Saving…";
            case SAVED -> {
                long minutes = Math.max(0, (System.currentTimeMillis() - lastSavedAt) / 60000L);
                yield minutes == 0 ? "Saved · just now" : minutes < 60
                        ? "Saved · " + minutes + " min ago" : "Saved · " + (minutes / 60) + " hr ago";
            }
        };
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (w < 4 || h < 4) return;
            RoundRectangle2D body = new RoundRectangle2D.Double(.5, .5, w - 2.0, h - 2.0, h - 2, h - 2);
            // Clip the refracted/blurred board image to the capsule; corners remain truly transparent.
            java.awt.Shape prior = g.getClip();
            g.clip(body);
            BufferedImage scene = glassBackdrop(w, h);
            if (scene != null) g.drawImage(scene, 0, 0, null);
            g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 184),
                    0, h, new Color(247, 249, 255, 165)));
            g.fill(body);
            g.setClip(prior);
            g.setStroke(new BasicStroke(1.0f));
            g.setColor(new Color(255, 255, 255, 218)); g.draw(body);
            g.setStroke(new BasicStroke(.8f));
            g.setColor(new Color(120, 135, 166, 64));
            g.draw(new RoundRectangle2D.Double(1.5, 1.5, w - 4, h - 4, h - 4, h - 4));
            g.setFont(BoardTheme.font(13.5f)); g.setColor(INK);
            FontMetrics title = g.getFontMetrics();
            String name = fittedName();
            g.drawString(name, 13, 18);
            if (hovered) {
                int x = Math.min(w - 18, 17 + title.stringWidth(name) + 8);
                g.setColor(MUTED); g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x, 16, x+8, 8);g.drawLine(x+2, 18, x+10, 10);g.drawLine(x, 16, x+2, 18);
            }
            g.setFont(BoardTheme.font(10.8f));
            g.setColor(MUTED);
            int x = 13;
            if (state.phase() == SaveMenuState.Phase.MODIFIED) {
                g.setColor(new Color(255, 149, 0)); g.fillOval(x, 27, 6, 6); x += 11;
            } else if (state.phase() == SaveMenuState.Phase.SAVING) {
                g.setColor(new Color(92, 105, 132));
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawArc(x, 26, 7, 7, spinnerAngle, 260); x += 12;
            }
            g.setColor(MUTED);
            g.drawString(subtitle(), x, 34);
        } finally { g.dispose(); }
    }
}
