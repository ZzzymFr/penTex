package view;

import interface_adapter.UiThread;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** The Swing event dispatch thread as the application's {@link UiThread}. */
public final class SwingUiThread implements UiThread {
    public static final SwingUiThread INSTANCE = new SwingUiThread();

    private SwingUiThread() {
    }

    @Override
    public void run(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeLater(task);
    }

    @Override
    public void later(Runnable task) {
        SwingUtilities.invokeLater(task);
    }

    @Override
    public Repeating every(int millis, Runnable task) {
        Timer timer = new Timer(millis, e -> task.run());
        return new Repeating() {
            @Override
            public void start() {
                timer.start();
            }

            @Override
            public void stop() {
                timer.stop();
            }

            @Override
            public boolean isRunning() {
                return timer.isRunning();
            }
        };
    }
}
