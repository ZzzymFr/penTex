package interface_adapter;

/**
 * The thread that owns the user interface, as presenters and collectors need it: run work there,
 * now or later, or repeatedly. The desktop provides a Swing implementation, so adapters stay free
 * of the GUI toolkit (Dependency Inversion) and tests can supply their own.
 */
public interface UiThread {

    /** Runs the task right away when already on the UI thread, otherwise as soon as possible there. */
    void run(Runnable task);

    /** Runs the task on the UI thread after the work already queued there. */
    void later(Runnable task);

    /** A task that, once started, runs on the UI thread every {@code millis} until stopped. */
    Repeating every(int millis, Runnable task);

    /** A repeating task. */
    interface Repeating {
        void start();

        void stop();

        boolean isRunning();
    }
}
