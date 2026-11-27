package view.board;

import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Animation frame timer. A plain javax.swing.Timer(16) measured ~52fps on macOS with uneven gaps
 * (each wait drifts a few ms), so motion stepped unevenly. This one aims every tick at the next
 * 1/60 s mark instead: steady 60fps. Same API as the Timer it replaces.
 */
public final class FrameTimer extends Timer {
    private static final long PERIOD = 1_000_000_000L / 60;
    private long next;

    public FrameTimer(ActionListener listener) {
        super(Motion.FRAME_MS, listener);
    }

    @Override
    protected void fireActionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        // just started or fell behind: re-anchor on now, never burst to catch up
        next = next == 0 || now - next > PERIOD ? now + PERIOD : next + PERIOD;
        int ms = (int) Math.max(1, (next - now + 500_000) / 1_000_000);
        setInitialDelay(ms);
        restart();  // before the listeners, so one that stops the timer still wins
        super.fireActionPerformed(e);
    }
}
