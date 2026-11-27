package view.canvas;

import view.board.Motion;
import view.board.Spring;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

// faint ring at a formula's end while the recognizer reads it, so the wait isn't dead air
final class ReadingHint extends JComponent {
    static final int SHOW_AFTER_MILLIS = 150;  // quick reads never flash it
    private static final Color INK = new Color(100, 116, 139);
    private static final float ALPHA = 0.55f;
    private static final double TURNS_PER_SEC = 1.1;

    private final Spring fade = new Spring(0.004f, true);
    private final Motion.Clock clock = new Motion.Clock();
    private final Timer frames = new Timer(Motion.FRAME_MS, e -> step());
    private final Timer soon = new Timer(SHOW_AFTER_MILLIS, e -> appear());
    private float target;
    private double angle;

    ReadingHint() {
        setOpaque(false);
        setVisible(false);
        soon.setRepeats(false);
    }

    // never takes clicks, the canvas under it keeps working
    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    void showSoon(Rectangle at) {
        if (at == null) {
            return;
        }
        setBounds(at);
        if (target == 1f) {
            return;  // already reading, just follow the formula
        }
        soon.restart();
    }

    void hideNow() {
        soon.stop();
        if (target == 0f && !isVisible()) {
            return;
        }
        target = 0f;
        frames.start();
    }

    boolean isReading() {
        return target == 1f;
    }

    private void appear() {
        target = 1f;
        setVisible(true);
        frames.start();
    }

    private void step() {
        float dt = clock.tick();
        if (!Motion.reduced()) {
            angle = (angle + dt * TURNS_PER_SEC * 360) % 360;
        }
        boolean still = fade.step(target, Motion.ENTER, dt);
        if (target == 0f && (still || fade.x < 0.02f)) {
            fade.snap(0f);
            frames.stop();
            clock.reset();
            setVisible(false);
        }
        if (target == 1f && still && Motion.reduced()) {
            frames.stop();
            clock.reset();
        }
        repaint();
    }

    @Override public void removeNotify() {
        soon.stop();
        frames.stop();
        clock.reset();
        target = 0f;
        fade.snap(0f);
        setVisible(false);
        super.removeNotify();
    }

    @Override
    protected void paintComponent(Graphics g) {
        float a = Math.max(0f, Math.min(1f, fade.x));
        if (a <= 0f) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ALPHA * a));
        double d = Math.min(getWidth(), getHeight()) - 4;
        float pen = (float) Math.max(1.5, d / 11);
        g2.setStroke(new BasicStroke(pen, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(INK.getRed(), INK.getGreen(), INK.getBlue(), 60));
        g2.draw(new Ellipse2D.Double(2, 2, d, d));
        g2.setColor(INK);
        // reduce motion: a still quarter arc, status without the spin
        g2.draw(new Arc2D.Double(2, 2, d, d, -angle + 90, 90, Arc2D.OPEN));
        g2.dispose();
    }
}
