package view.board.doc;

import javax.swing.*;
import java.awt.*;

/**
 * macOS segmented control for the ribbon tabs: a grey track, and a white raised pill under the
 * chosen tab that slides (spring) to the next one instead of jumping.
 */
final class SegmentTrack extends JPanel {
    private final view.board.Spring pillX = new view.board.Spring();
    private final view.board.Spring pillW = new view.board.Spring();
    private final view.board.Motion.Clock clock = new view.board.Motion.Clock();
    private final javax.swing.Timer timer = new javax.swing.Timer(view.board.Motion.FRAME_MS, e -> step());
    private Component chosen;
    private boolean placed;

    SegmentTrack() {
        super(new FlowLayout(FlowLayout.CENTER, 0, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
    }

    void choose(Component tab) {
        chosen = tab;
        if (!placed || !isShowing()) {
            placed = false;  // snap on the next layout
        }
        if (isShowing()) timer.start();
        repaint();
    }

    @Override public void removeNotify() {
        timer.stop();
        clock.reset();
        placed = false;
        super.removeNotify();
    }

    @Override public void doLayout() {
        super.doLayout();
        if (chosen != null && (!placed || !timer.isRunning())) {
            pillX.snap(chosen.getX());
            pillW.snap(chosen.getWidth());
            placed = chosen.getWidth() > 0;
        }
    }

    private void step() {
        if (chosen == null) {
            timer.stop();
            return;
        }
        float dt = clock.tick();
        boolean still = pillX.step(chosen.getX(), view.board.Motion.SELECT, dt);
        still &= pillW.step(chosen.getWidth(), view.board.Motion.SELECT, dt);
        if (still) {
            timer.stop();
            clock.reset();
        }
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int h = getHeight();
        g2.setColor(new Color(0, 0, 0, 16));
        g2.fillRoundRect(0, 0, getWidth(), h, h, h);
        if (chosen != null && pillW.x > 0) {
            int y = 3;
            int ph = h - 6;
            for (int i = 1; i <= 2; i++) {
                g2.setColor(new Color(0, 0, 0, 10));
                g2.fillRoundRect(Math.round(pillX.x), y + i, Math.round(pillW.x), ph, ph, ph);
            }
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(Math.round(pillX.x), y, Math.round(pillW.x), ph, ph, ph);
            g2.setColor(new Color(0, 0, 0, 14));
            g2.drawRoundRect(Math.round(pillX.x), y, Math.round(pillW.x), ph, ph, ph);
        }
        g2.dispose();
    }
}
