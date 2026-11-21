package view.glass;

import view.board.BoardTheme;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLayeredPane;
import javax.swing.InputMap;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.RootPaneContainer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.Consumer;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * A shared, Swing-only modal presentation for message, confirmation and text-entry dialogs.
 * Callers still own the decisions: this class never modifies a document, workspace or file.
 * The native JFileChooser remains separate so file selection works on Windows and macOS.
 */
public final class GlassDialogs {
    private GlassDialogs() { }

    public enum Tone { INFO, WARNING, ERROR, QUESTION }

    public static void info(Component parent, String message) {
        present(parent, "Message", message, Tone.INFO, null, new String[]{"OK"});
    }

    public static void warning(Component parent, String message) {
        present(parent, "Notice", message, Tone.WARNING, null, new String[]{"OK"});
    }

    public static void error(Component parent, String message) {
        present(parent, "Error", message, Tone.ERROR, null, new String[]{"OK"});
    }

    /** Yes / No confirmation; closing or Esc is always treated as No. */
    public static boolean confirm(Component parent, String title, String message, String affirmative) {
        return present(parent, title, message, Tone.QUESTION, null,
                new String[]{Objects.requireNonNull(affirmative), "Cancel"}) == 0;
    }

    /** Returns the clicked option index, or -1 for Esc / dismissed. */
    public static int options(Component parent, String title, String message, String... options) {
        if (options == null || options.length == 0) throw new IllegalArgumentException("Options required");
        return present(parent, title, message, Tone.QUESTION, null, options);
    }

    /** Returns null for Cancel or Esc; an empty string is returned unchanged for validation by caller. */
    public static String input(Component parent, String title, String prompt, String initialValue) {
        return input(parent, title, prompt, initialValue, "OK");
    }

    public static String input(Component parent, String title, String prompt,
                               String initialValue, String affirmative) {
        GlassTextField field = new GlassTextField(24, "");
        field.setText(initialValue == null ? "" : initialValue);
        field.setFont(new Font(Font.DIALOG, Font.PLAIN, 15)); // logical font fallback for Unicode names
        int option = present(parent, title, prompt, Tone.QUESTION, field,
                new String[]{Objects.requireNonNull(affirmative), "Cancel"});
        return option == 0 ? field.getText() : null;
    }

    /** Opens caller-owned content (e.g. a saved-canvas list) without imposing a native JOptionPane. */
    public static boolean choose(Component parent, String title, JComponent content, String affirmative) {
        return choose(parent, title, content, affirmative, null);
    }

    /** Register an accept callback for double-click or Enter on caller-owned content. */
    public static boolean choose(Component parent, String title, JComponent content, String affirmative,
                                 Consumer<Runnable> registerAccept) {
        return present(parent, title, "", Tone.QUESTION, Objects.requireNonNull(content),
                new String[]{Objects.requireNonNull(affirmative), "Cancel"}, registerAccept) == 0;
    }

    public enum UnsavedDecision { DONT_SAVE, CANCEL, SAVE }

    /** Order is stable: red destructive choice, neutral Cancel, blue Save. */
    public static UnsavedDecision unsavedChanges(Component parent, String canvasName) {
        return unsavedChanges(parent, canvasName, "opening another canvas");
    }

    /** {@code beforeWhat} completes "Save changes to … before ___?", e.g. "closing Math Lab". */
    public static UnsavedDecision unsavedChanges(Component parent, String canvasName, String beforeWhat) {
        int choice = present(parent, "Save changes to “" + canvasName + "” before " + beforeWhat + "?",
                "If you don’t save, your latest drawing will be lost.", Tone.WARNING, null,
                new String[]{"Don’t Save", "Cancel", "Save"});
        return choice == 0 ? UnsavedDecision.DONT_SAVE :
                choice == 2 ? UnsavedDecision.SAVE : UnsavedDecision.CANCEL;
    }

    private static int present(Component parent, String title, String message, Tone tone,
                               JComponent content, String[] labels) {
        return present(parent, title, message, tone, content, labels, null);
    }

    private static int present(Component parent, String title, String message, Tone tone,
                               JComponent content, String[] labels, Consumer<Runnable> registerAccept) {
        // Swing components and nested event loops must be created/entered on the EDT.
        // A background caller retains the same synchronous return-value contract.
        if (!SwingUtilities.isEventDispatchThread()) {
            FutureTask<Integer> task = new FutureTask<>(() ->
                    present(parent, title, message, tone, content, labels, registerAccept));
            SwingUtilities.invokeLater(task);
            boolean interrupted = false;
            try {
                while (true) {
                    try {
                        return task.get();
                    } catch (InterruptedException retry) {
                        // A caller interruption must not strand a still-visible modal overlay.
                        interrupted = true;
                    } catch (ExecutionException failed) {
                        Throwable cause = failed.getCause();
                        if (cause instanceof RuntimeException runtime) throw runtime;
                        if (cause instanceof Error error) throw error;
                        throw new IllegalStateException("Unable to display glass dialog", cause);
                    }
                }
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }

        Window owner = parent instanceof Window window ? window
                : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (owner instanceof RootPaneContainer rootOwner && owner.isShowing()) {
            return new ModalOverlay(owner, rootOwner.getRootPane(), title, message, tone,
                    content, labels, registerAccept).showAndWait();
        }
        // Truly ownerless/non-showing callers cannot use an in-window overlay. Use a
        // conventional opaque, decorated dialog rather than an alpha native window.
        return presentWithoutOwner(owner, parent, title, message, tone, content, labels, registerAccept);
    }

    /** Opaque fallback only; the normal app workflow never creates another native window. */
    private static int presentWithoutOwner(Window owner, Component parent, String title, String message,
                                           Tone tone, JComponent content, String[] labels,
                                           Consumer<Runnable> registerAccept) {
        JDialog dialog = new JDialog(owner, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setTitle(title == null ? "" : title);
        dialog.setBackground(new Color(242, 242, 247));
        AtomicInteger selected = new AtomicInteger(-1);
        DialogPane pane = new DialogPane(title, message, tone, content, labels, i -> {
            selected.set(i);
            dialog.dispose();
        });
        dialog.setContentPane(pane);
        if (registerAccept != null) registerAccept.accept(() -> {
            selected.set(0);
            dialog.dispose();
        });
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-glass");
        dialog.getRootPane().getActionMap().put("close-glass", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { dialog.dispose(); }
        });
        setDefaultOption(dialog.getRootPane(), pane, labels);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        if (content instanceof JTextField field) {
            SwingUtilities.invokeLater(() -> { field.requestFocusInWindow(); field.selectAll(); });
        }
        dialog.setVisible(true);
        return selected.get();
    }

    private static void setDefaultOption(JRootPane root, DialogPane pane, String[] labels) {
        if (!pane.buttons.isEmpty()) {
            boolean destructive = labels.length == 2 && labels[0].equals("Clear All");
            int index = destructive ? 1
                    : labels.length == 3 && labels[0].contains("Save") && labels[2].equals("Save") ? 2 : 0;
            root.setDefaultButton(pane.buttons.get(index));
        }
    }

    /**
     * Owner-local modal presentation. The entire rounded card (and its shadow) is painted
     * by Swing in the existing opaque native window: no window-alpha compositing, screenshots
     * or OS-dependent clear/restore passes are involved, even during button hover.
     *
     * This is view-only. It owns neither the caller's content nor any workspace decisions.
     */
    private static final class ModalOverlay extends JPanel {
        private static final Color SCRIM = new Color(14, 23, 42, 88);
        private static final int MARGIN = 12;

        private final Window owner;
        private final JRootPane root;
        private final JLayeredPane layers;
        private final DialogPane pane;
        private final JScrollPane card;
        private final Dimension cardSize;
        private final JComponent extra;
        private final SecondaryLoop loop;
        private final KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        private final AtomicInteger selected = new AtomicInteger(-1);
        private final AtomicBoolean finished = new AtomicBoolean();
        private final Component previousFocus;
        private final javax.swing.JButton previousDefault;
        private final ComponentAdapter resizeListener = new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { updateBounds(); }
        };
        private final WindowAdapter closeListener = new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { finish(-1); }
            @Override public void windowClosed(WindowEvent event) { finish(-1); }
        };
        private final KeyEventDispatcher keys;
        private final PropertyChangeListener focusListener;

        private ModalOverlay(Window owner, JRootPane root, String title, String message,
                             Tone tone, JComponent extra, String[] labels,
                             Consumer<Runnable> registerAccept) {
            super(null);
            this.owner = owner;

            this.keys = event -> {
                if (!onTop() || !(event.getSource() instanceof Component target)) return false;
                // The normal focus traversal stays inside this dialog. Guard against existing
                // root-pane accelerators and focus unexpectedly shifting to the covered view.
                if (SwingUtilities.getWindowAncestor(target) == owner
                        && !SwingUtilities.isDescendingFrom(target, this)) {
                    event.consume();
                    return true;
                }
                return false;
            };
            this.focusListener = event -> {
                if (!onTop() || !(event.getNewValue() instanceof Component target)) return;
                if (SwingUtilities.getWindowAncestor(target) == owner
                        && !SwingUtilities.isDescendingFrom(target, this)) {
                    SwingUtilities.invokeLater(this::focusDialog);
                }
            };
            this.root = root;
            this.layers = root.getLayeredPane();
            this.extra = extra;
            this.previousFocus = focusManager.getFocusOwner();
            this.previousDefault = root.getDefaultButton();
            this.loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            setOpaque(false);
            setFocusable(true);
            setFocusCycleRoot(true);
            setName("glass-modal-overlay");
            pane = new DialogPane(title, message, tone, extra, labels, this::finish);
            // A wrapped JTextArea can base its preferred width on its current size.
            // Freeze the natural card size before mounting it in a scrolling viewport;
            // otherwise repeated viewport measurements grow the pane indefinitely.
            cardSize = pane.getPreferredSize();
            JPanel surface = new JPanel(null);
            surface.setOpaque(false);
            surface.setPreferredSize(new Dimension(cardSize));
            surface.add(pane);
            pane.setBounds(0, 0, cardSize.width, cardSize.height);
            card = new JScrollPane(surface, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            card.setBorder(BorderFactory.createEmptyBorder());
            card.setOpaque(false);
            card.getViewport().setOpaque(false);
            add(card);
            // The overlay itself intercepts all outside clicks and wheel events.
            addMouseListener(new MouseAdapter() { });
            addMouseWheelListener(event -> event.consume());
            getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-glass-overlay");
            getActionMap().put("close-glass-overlay", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) { finish(-1); }
            });
            getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-glass-overlay");
            shieldOwnerShortcuts();
            if (registerAccept != null) registerAccept.accept(() -> finish(0));
            setDefaultOption(root, pane, labels);
        }

        /**
         * Existing app-wide accelerators (Save, Undo, Redo, fullscreen Esc) are installed on
         * the owner's root pane. Intercept them at the modal ancestor BEFORE Swing searches
         * WHEN_IN_FOCUSED_WINDOW bindings, so a confirmation cannot act on the covered canvas.
         * Focused fields still receive their own Ctrl/Cmd+C/V/A key bindings first.
         */
        private void shieldOwnerShortcuts() {
            InputMap ownerKeys = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            KeyStroke[] shortcuts = ownerKeys == null ? null : ownerKeys.allKeys();
            if (shortcuts == null) return;
            final String suppress = "glass-modal-suppress-owner-shortcut";
            getActionMap().put(suppress, new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) { /* modal: ignore */ }
            });
            for (KeyStroke shortcut : shortcuts) {
                // Enter invokes the current dialog's default button; Esc is handled locally.
                if (shortcut.getKeyCode() == KeyEvent.VK_ENTER && shortcut.getModifiers() == 0) continue;
                if (shortcut.getKeyCode() == KeyEvent.VK_ESCAPE && shortcut.getModifiers() == 0) continue;
                getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(shortcut, suppress);
                getInputMap(JComponent.WHEN_FOCUSED).put(shortcut, suppress);
            }
        }

        private int showAndWait() {
            if (finished.get()) return selected.get();
            try {
                layers.add(this, JLayeredPane.MODAL_LAYER, 0);
                updateBounds();
                layers.addComponentListener(resizeListener);
                owner.addWindowListener(closeListener);
                focusManager.addKeyEventDispatcher(keys);
                focusManager.addPropertyChangeListener("focusOwner", focusListener);
                layers.revalidate();
                layers.repaint();
                SwingUtilities.invokeLater(this::focusDialog);
                // Runs a nested event pump on the EDT, matching JDialog.setVisible(true)'s
                // synchronous result contract, including nested Delete over Open Canvas.
                if (!finished.get() && !loop.enter() && !finished.get()) {
                    finish(-1);  // never leave an unreachable overlay behind
                }
                return selected.get();
            } catch (RuntimeException | Error failure) {
                finish(-1);  // remove listeners and restore the default button on failure
                throw failure;
            }
        }

        private void updateBounds() {
            setBounds(0, 0, layers.getWidth(), layers.getHeight());
            revalidate();
            repaint();
        }

        @Override public void doLayout() {
            Dimension preferred = cardSize;
            int availableW = Math.max(0, getWidth() - MARGIN * 2);
            int availableH = Math.max(0, getHeight() - MARGIN * 2);
            int width = Math.min(preferred.width, availableW);
            int height = Math.min(preferred.height, availableH);
            card.setBounds((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
            card.revalidate();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setColor(SCRIM);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }

        private boolean onTop() {
            return getParent() == layers && layers.getLayer(this) == JLayeredPane.MODAL_LAYER.intValue()
                    && layers.getPosition(this) == 0;
        }

        private void focusDialog() {
            if (!onTop()) return;
            if (extra instanceof JTextField field) {
                if (field.requestFocusInWindow()) field.selectAll();
            } else if (extra != null) {
                Component text = findTextField(extra);
                if (text != null && text.requestFocusInWindow()) return;
                requestFocusInWindow();
            } else {
                requestFocusInWindow();
            }
        }

        private static Component findTextField(java.awt.Container parent) {
            if (parent instanceof JTextField field && field.isEnabled() && field.isShowing()) return field;
            for (Component child : parent.getComponents()) {
                if (child instanceof JTextField field && field.isEnabled() && field.isShowing()) return field;
                if (child instanceof java.awt.Container container) {
                    Component found = findTextField(container);
                    if (found != null) return found;
                }
            }
            return null;
        }

        private void finish(int answer) {
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeLater(() -> finish(answer));
                return;
            }
            if (!finished.compareAndSet(false, true)) return;
            selected.set(answer);
            focusManager.removePropertyChangeListener("focusOwner", focusListener);
            focusManager.removeKeyEventDispatcher(keys);
            layers.removeComponentListener(resizeListener);
            owner.removeWindowListener(closeListener);
            if (getParent() == layers) {
                layers.remove(this);
                layers.revalidate();
                layers.repaint();
            }
            root.setDefaultButton(previousDefault);
            if (previousFocus != null && previousFocus.isShowing()
                    && SwingUtilities.getWindowAncestor(previousFocus) == owner) {
                SwingUtilities.invokeLater(previousFocus::requestFocusInWindow);
            }
            loop.exit();
        }
    }

    // The card is painted directly inside its owner's window. No native translucent
    // dialog or background capture is used in the normal application workflow.

    /** Package-visible, window-free presentation: easily tested in headless mode. */
    static final class DialogPane extends JPanel {
        private static final int SHADOW = 9;
        final List<GlassButton> buttons = new ArrayList<>();
        /** What is behind the dialog, drawn under it so the window itself can stay opaque. */
        BufferedImage backdrop;

        DialogPane(String title, String message, Tone tone, JComponent extra,
                   String[] labels, IntConsumer select) {
            super(new BorderLayout(0, 12));
            Objects.requireNonNull(tone);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
            setName("glass-dialog");

            JPanel heading = new JPanel(new BorderLayout());
            heading.setOpaque(false);
            JPanel words = new JPanel(new BorderLayout(0, 7));
            words.setOpaque(false);
            JLabel titleLabel = new JLabel(title == null ? "" : title);
            titleLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 15));
            titleLabel.setForeground(new Color(30, 41, 59));
            words.add(titleLabel, BorderLayout.NORTH);
            if (message != null && !message.isBlank()) {
                JTextArea description = new JTextArea(message);
                description.setLineWrap(true);
                description.setWrapStyleWord(true);
                description.setRows(Math.min(5, Math.max(1, message.length() / 43 + 1)));
                description.setColumns(30);
                description.setEditable(false);
                description.setFocusable(false);
                description.setOpaque(false);
                description.setFont(new Font(Font.DIALOG, Font.PLAIN, 13));
                description.setForeground(new Color(56, 65, 80));
                description.setBorder(null);
                words.add(description, BorderLayout.CENTER);
            }
            heading.add(words, BorderLayout.CENTER);
            if ((title != null && !title.isBlank()) || (message != null && !message.isBlank())) {
                add(heading, BorderLayout.NORTH);
            }
            if (extra != null) {
                JPanel inset = new JPanel(new BorderLayout());
                inset.setOpaque(false);
                inset.setBorder(BorderFactory.createEmptyBorder(2, 0, 3, 0));
                inset.add(extra);
                add(inset, BorderLayout.CENTER);
            }

            JPanel actions = new JPanel(new BorderLayout());
            actions.setOpaque(false);
            JPanel trailing = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            trailing.setOpaque(false);
            java.util.List<GlassButton> trailingButtons = new java.util.ArrayList<>();
            boolean unsaved = labels.length == 3 && labels[0].toLowerCase(java.util.Locale.ROOT)
                    .contains("save") && labels[1].equals("Cancel") && labels[2].equals("Save");
            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                Style style = labels[i].equals("Cancel") ? Style.SECONDARY :
                        (unsaved && i == 0) || (labels[i].equals("Clear All")) ? Style.DESTRUCTIVE :
                                (unsaved ? i == 2 : i == 0) ? Style.PRIMARY : Style.SECONDARY;
                GlassButton button = new ActionButton(labels[i], style);
                button.setName("glass-option-" + i);
                button.addActionListener(e -> select.accept(index));
                buttons.add(button);
                if (unsaved && i == 0) actions.add(button, BorderLayout.WEST);
                else trailingButtons.add(button);
            }
            if (unsaved) trailingButtons.forEach(trailing::add);
            else for (int i = trailingButtons.size()-1; i >= 0; i--) trailing.add(trailingButtons.get(i));
            if (extra != null && extra.getClientProperty("glass.leadingActions") instanceof List<?> leading) {
                JPanel leadingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                leadingRow.setOpaque(false);
                for (Object item : leading) {
                    if (item instanceof GlassButton button) leadingRow.add(button);
                }
                actions.add(leadingRow, BorderLayout.WEST);
            }
            actions.add(trailing, BorderLayout.EAST);
            add(actions, BorderLayout.SOUTH);
        }

        @Override public Dimension getPreferredSize() {
            Dimension natural = super.getPreferredSize();
            return new Dimension(Math.max(400, natural.width + 16), natural.height + 17);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            if (backdrop != null) g2.drawImage(backdrop, 0, 0, getWidth(), getHeight(), null);
            Glass.setUp(g2);
            RoundRectangle2D shell = new RoundRectangle2D.Double(SHADOW, SHADOW / 2.0,
                    getWidth() - SHADOW * 2.0, getHeight() - SHADOW * 1.5, 27, 27);
            // Nearly opaque white body; only the outer shadow and rim stay translucent.
            // This prevents canvas strokes showing through Save As fields and text.
            g2.setColor(new Color(255, 255, 255, 253));
            g2.fill(shell);
            Glass.paint(g2, shell, null, 0, 0, 0, 1);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Place embedded actions on the same baseline as the modal's Cancel/Open buttons. */
    public static void setLeadingActions(JComponent content, GlassButton... actions) {
        content.putClientProperty("glass.leadingActions", List.of(actions));
    }

    /** Reuse the exact dialog action appearance in embedded views such as the canvas browser. */
    public static GlassButton actionButton(String label, boolean primary) {
        return new ActionButton(label, primary ? Style.PRIMARY : Style.SECONDARY);
    }

    private enum Style { PRIMARY, SECONDARY, DESTRUCTIVE }

    /** Buttons remain GlassButton-compatible for existing callers and tests. */
    private static final class ActionButton extends GlassButton {
        private final Style style;
        ActionButton(String label, Style style) {
            super(label, 35);
            this.style = style;
            setFont(BoardTheme.font(13.5f));
        }
        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(89, d.width), d.height);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Glass.setUp(g2);
            int inset = 3;
            int w = getWidth() - 2 * inset, h = getHeight() - 2 * inset;
            RoundRectangle2D shape = new RoundRectangle2D.Double(inset, inset, w, h, h, h);
            if (style != Style.DESTRUCTIVE) {
                g2.setColor(style == Style.PRIMARY ? new Color(0, 122, 255) : new Color(238, 238, 240));
                g2.fill(shape);
            }
            g2.setColor(style == Style.PRIMARY ? Color.WHITE :
                    style == Style.DESTRUCTIVE ? new Color(220, 38, 38) : new Color(27, 29, 33));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }
}
