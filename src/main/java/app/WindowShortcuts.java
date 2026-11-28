package app;

import view.DrawingCanvas;

import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;

/** The window-wide keyboard shortcuts, the same keys on macOS (Cmd) and Windows (Ctrl). */
final class WindowShortcuts {
    private WindowShortcuts() {
    }

    /**
     * @param pdfPanel        while focus is inside it, Undo / Redo belong to the PDF editor
     * @param save            Cmd/Ctrl+S
     * @param saveAs          Cmd/Ctrl+Shift+S
     * @param leaveFullscreen Escape
     */
    static void install(JRootPane root, DrawingCanvas drawing, Component pdfPanel,
                        Runnable save, Runnable saveAs, Runnable leaveFullscreen) {
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        // Canvas undo/redo only. The PDF workspace keeps its own Undo/Redo, and a text field
        // being typed in must never silently undo canvas ink instead.
        BooleanSupplier focusOutsideCanvas = () -> {
            Component focused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            return focused instanceof javax.swing.text.JTextComponent
                    || (focused != null && SwingUtilities.isDescendingFrom(focused, pdfPanel));
        };
        java.awt.event.ActionListener canvasUndo = e -> { if (!focusOutsideCanvas.getAsBoolean()) drawing.undo(); };
        java.awt.event.ActionListener canvasRedo = e -> { if (!focusOutsideCanvas.getAsBoolean()) drawing.redo(); };
        root.registerKeyboardAction(canvasUndo, KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(canvasRedo, KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        // The platform redo shortcut (Cmd+Shift+Z on macOS, Ctrl+Shift+Z elsewhere).
        root.registerKeyboardAction(canvasRedo,
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut | InputEvent.SHIFT_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> save.run(), KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(e -> saveAs.run(),
                KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut | InputEvent.SHIFT_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        root.registerKeyboardAction(e -> leaveFullscreen.run(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        // reduce motion on/off, remembered; same keys on mac + windows (cmd / ctrl)
        root.registerKeyboardAction(e -> {
            boolean on = !view.board.Motion.reduced();
            view.board.Motion.setReduced(on);
            view.glass.GlassNotices.showSuccess(root, on ? "Reduce Motion on" : "Reduce Motion off");
        }, KeyStroke.getKeyStroke(KeyEvent.VK_M, shortcut | InputEvent.SHIFT_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }
}
