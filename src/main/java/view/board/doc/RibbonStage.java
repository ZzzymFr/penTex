package view.board.doc;

import view.board.Motion;
import view.board.Spring;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.image.BufferedImage;

/**
 * Holds the current ribbon row. Switching tabs slides the old row out and the new one in, toward the
 * tab that was picked, instead of swapping them in one frame. The capsule around it stays put.
 */
final class RibbonStage extends JPanel {
    private static final int SLIDE_OUT = 18;
    private static final int SLIDE_IN = 8;  // small: the real buttons are already where they'll be clicked

    private final Spring progress = new Spring(0.003f, true);
    private final Motion.Clock clock = new Motion.Clock();
    private final Timer timer = new view.board.FrameTimer(e -> step());
    private BufferedImage leaving;   // old row as it last looked on screen
    private int leavingW;
    private int leavingH;
    private int direction;            // +1 = new tab is to the right

    RibbonStage() {
        super(new BorderLayout());
        setOpaque(false);
    }

    void show(JComponent ribbon, int direction) {
        boolean animate = getComponentCount() > 0 && isShowing() && getWidth() > 0 && getHeight() > 0
                && direction != 0;
        // picks up mid-slide frames too, so a fast second click doesn't jump
        leaving = animate ? picture() : null;
        leavingW = getWidth();
        leavingH = getHeight();
        this.direction = Integer.signum(direction);
        removeAll();
        add(ribbon, BorderLayout.CENTER);
        revalidate();
        repaint();
        if (animate) {
            progress.snap(0f);
            clock.reset();
            timer.start();
        } else {
            finish();
        }
    }

    private void step() {
        boolean done = progress.step(1f, Motion.ENTER, clock.tick());
        if (done) finish();
        repaint();
    }

    private void finish() {
        timer.stop();
        clock.reset();
        progress.snap(1f);
        leaving = null;
    }

    @Override public void removeNotify() {
        finish();
        super.removeNotify();
    }

    private BufferedImage picture() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        double scale = gc == null ? 1 : Math.max(1, gc.getDefaultTransform().getScaleX());
        int iw = (int) Math.ceil(getWidth() * scale);
        int ih = (int) Math.ceil(getHeight() * scale);
        BufferedImage img = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.scale(iw / (double) getWidth(), ih / (double) getHeight());
            paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    private float amount() {
        return Math.max(0f, Math.min(1f, progress.x));
    }

    // old row: a picture sliding off; new row: real buttons, fading in from a few px over
    @Override protected void paintChildren(Graphics g) {
        if (leaving == null) {
            super.paintChildren(g);
            return;
        }
        float p = amount();
        boolean slide = !Motion.reduced();
        Graphics2D old = (Graphics2D) g.create();
        try {
            old.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, 1f - p * 1.6f)));
            int dx = slide ? Math.round(-direction * SLIDE_OUT * p) : 0;
            old.drawImage(leaving, dx, 0, leavingW, leavingH, null);
        } finally {
            old.dispose();
        }
        Graphics2D now = (Graphics2D) g.create();
        try {
            now.setComposite(AlphaComposite.SrcOver.derive(p));
            if (slide) now.translate(Math.round(direction * SLIDE_IN * (1f - p)), 0);
            super.paintChildren(now);
        } finally {
            now.dispose();
        }
    }
}
