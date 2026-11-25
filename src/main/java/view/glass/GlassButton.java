package view.glass;

import view.board.Motion;
import view.board.Spring;
import view.stack.Fonts;

import javax.swing.JButton;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A push button made of liquid glass: a translucent capsule that brightens under the mouse and
 * sinks slightly when pressed, with smooth transitions. Works like any JButton (addActionListener).
 */
public class GlassButton extends JButton {
    private static final int HEIGHT = 30;
    private static final int PADDING = 16;

    private final int height;
    private final Spring hover = new Spring(0.004f, true);
    private final Spring press = new Spring(0.004f);
    private boolean hovering;
    private final Motion.Clock clock = new Motion.Clock();
    private final Timer animator = new Timer(Motion.FRAME_MS, e -> step());

    public GlassButton(String text) {
        this(text, HEIGHT);
    }

    /** A glass button of the given height (the capsule; its shadow adds a little around it). */
    public GlassButton(String text, int height) {
        super(text);
        this.height = height;
        setFont(Fonts.ui(Font.PLAIN, 13f));
        setForeground(Glass.TEXT);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        // reachable with Tab (focus ring), but a mouse click doesn't grab focus
        setFocusable(true);
        setRequestFocusEnabled(false);
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { repaint(); }
        });
        setOpaque(false);
        setRolloverEnabled(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovering = true;
                animator.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovering = false;
                animator.start();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // feedback on the way down, not on release: jump most of the way this frame
                press.x = Math.max(press.x, 0.6f);
                repaint();
                animator.start();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                animator.start();
            }
        });
    }

    /** Springs hover and press towards their targets; stops once both arrive. */
    private void step() {
        float dt = clock.tick();
        boolean still = hover.step(hovering && isEnabled() ? 1f : 0f, Motion.HOVER, dt);
        still &= press.step(getModel().isArmed() && getModel().isPressed() ? 1f : 0f, Motion.PRESS, dt);
        if (still) {
            animator.stop();
            clock.reset();
        }
        repaint();
    }

    @Override public void removeNotify() {
        animator.stop(); clock.reset(); hovering = false;
        hover.snap(0); press.snap(0);
        super.removeNotify();
    }

    /** 0..1, for subclasses that paint their own face. */
    protected double hoverAmount() {
        return Math.max(0, Math.min(1, hover.x));
    }

    protected double pressAmount() {
        return Math.max(0, Math.min(1, press.x));
    }

    /** Keyboard focus ring around a capsule, only when focus came from the keyboard. */
    protected void paintFocusRing(Graphics2D g2, java.awt.geom.RoundRectangle2D shape) {
        if (!isFocusOwner()) {
            return;
        }
        Graphics2D r = (Graphics2D) g2.create();
        Glass.setUp(r);
        r.setStroke(new java.awt.BasicStroke(2.5f));
        r.setColor(Glass.withAlpha(Glass.ACCENT, 150));
        r.draw(new java.awt.geom.RoundRectangle2D.Double(shape.getX() - 1.5, shape.getY() - 1.5,
                shape.getWidth() + 3, shape.getHeight() + 3, shape.getArcWidth() + 3, shape.getArcHeight() + 3));
        r.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int padding = PADDING + (height - HEIGHT) / 2;  // taller buttons get a little more side room
        return new Dimension(fm.stringWidth(getText()) + 2 * padding + 2 * Glass.SHADOW,
                height + 2 * Glass.SHADOW);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Glass.setUp(g2);
        double p = pressAmount();
        double sink = p * 0.6;  // the glass dips a little when pressed
        double s = Glass.SHADOW;
        double w = getWidth() - 2 * s - 2 * sink;
        double h = getHeight() - 2 * s - 2 * sink;
        java.awt.geom.RoundRectangle2D cap = Glass.capsule(s + sink, s + sink * 0.5, w, h);
        Glass.paint(g2, cap, null, 0, hoverAmount(), p, 0);
        paintFocusRing(g2, cap);

        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int textY = (int) Math.round((getHeight() + fm.getAscent() - fm.getDescent()) / 2.0 + sink * 0.5);
        Glass.drawText(g2, getText(), getWidth() / 2.0, textY,
                isEnabled() ? getForeground() : Glass.withAlpha(Glass.TEXT_MUTED, 110));
        g2.dispose();
    }
}
