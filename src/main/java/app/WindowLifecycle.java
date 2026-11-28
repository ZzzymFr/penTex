package app;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.util.function.BooleanSupplier;

/** Closing and quitting the main window go through the unsaved-changes check. */
final class WindowLifecycle {
    private WindowLifecycle() {
    }

    /**
     * Closing asks about unsaved ink first. EXIT_ON_CLOSE quit before windowClosed ran, so unsaved
     * work was lost and the PDF editor and model were never closed.
     *
     * @param confirmClose false keeps the window open
     */
    static void installCloseConfirmation(JFrame frame, BooleanSupplier confirmClose) {
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (!confirmClose.getAsBoolean()) return;
                frame.dispose();
                // Queued after windowClosed, so every close listener runs before the JVM exits.
                SwingUtilities.invokeLater(() -> System.exit(0));
            }
        });
        if (!java.awt.GraphicsEnvironment.isHeadless() && java.awt.Desktop.isDesktopSupported()
                && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.APP_QUIT_HANDLER)) {
            // Cmd+Q would otherwise exit immediately; route it through the same unsaved-changes check.
            java.awt.Desktop.getDesktop().setQuitHandler((event, response) -> {
                response.cancelQuit();
                SwingUtilities.invokeLater(() -> frame.dispatchEvent(
                        new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING)));
            });
        }
    }

    /** Runs once the window is first shown. */
    static void onOpened(JFrame frame, Runnable action) {
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent e) { action.run(); }
        });
    }

    /** Runs after the window was closed. */
    static void onClosed(JFrame frame, Runnable action) {
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { action.run(); }
        });
    }
}
