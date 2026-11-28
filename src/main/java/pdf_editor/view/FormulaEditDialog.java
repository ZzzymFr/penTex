package pdf_editor.view;

import pdf_editor.use_case.FormulaEditSubmission;
import pdf_editor.domain.PdfMaterial;
import pdf_editor.rendering.MaterialRenderer;
import view.board.BoardTheme;
import view.glass.GlassDialogs;
import view.glass.GlassSegmentedControl;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.Function;

/** View-only editor. The shared owner-local Glass dialog supplies modality and appearance;
 * formula evaluation still goes through the injected use-case callback. */
public final class FormulaEditDialog {
    public record Result(String formula, String result) { }
    private static final Color INK = BoardTheme.LABEL;
    private static final Color SECONDARY = new Color(110,110,115);
    private static final Color BORDER = new Color(0,0,0,34);
    private static final Color ERROR = new Color(184,65,68);
    private FormulaEditDialog() { }

    public static Result show(Window owner, PdfMaterial item) {
        return show(owner, item, latex -> item.result());
    }
    public static Result show(Window owner, PdfMaterial item, Function<String,String> recalculate) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(recalculate, "recalculate");
        if (item.isGraph()) throw new IllegalArgumentException("Graphs cannot be edited as formulas");
        if (!SwingUtilities.isEventDispatchThread()) {
            java.util.concurrent.FutureTask<Result> task = new java.util.concurrent.FutureTask<>(
                    () -> show(owner,item,recalculate));
            SwingUtilities.invokeLater(task);
            boolean interrupted = false;
            try {
                for (;;) try { return task.get(); }
                catch (InterruptedException e) { interrupted = true; }
                catch (java.util.concurrent.ExecutionException e) {
                    if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                    throw new IllegalStateException(e.getCause());
                }
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
        return new Editor(item,recalculate).show(owner);
    }

    private static final class Editor {
        private final PdfMaterial item;
        private final Function<String,String> recalculate;
        private final JPanel content = new JPanel(new BorderLayout(0,12));
        private final GlassSegmentedControl mode = new GlassSegmentedControl("Calculate (LaTeX)", "Manual Entry");
        private final JTextArea formula;
        private final JLabel preview = new JLabel("", SwingConstants.CENTER);
        private final JLabel status = new JLabel(" ");
        private final JButton save = GlassDialogs.actionButton("Recalculate & Save",true);
        private final JButton cancel = GlassDialogs.actionButton("Cancel",false);
        private final Timer previewDelay = new Timer(160,e -> renderPreview());
        private SwingWorker<BufferedImage,Void> previewWorker;
        private SwingWorker<FormulaEditSubmission.Revision,Void> calculation;
        private long previewGeneration;
        private String shownResult;
        private boolean active = true;
        private Runnable dismiss = () -> { };
        private Result result;

        Editor(PdfMaterial item, Function<String,String> recalculate) {
            this.item = item; this.recalculate = recalculate; shownResult = item.result();
            formula = new JTextArea(item.formula(),3,30);
            formula.setLineWrap(true); formula.setWrapStyleWord(true);
            formula.setFont(new Font(Font.MONOSPACED,Font.PLAIN,14));
            formula.setForeground(INK); formula.setBackground(Color.WHITE);
            formula.setBorder(BorderFactory.createEmptyBorder(8,10,8,10));
            JScrollPane input = new JScrollPane(formula);
            input.setPreferredSize(new Dimension(420,82));
            input.setBorder(BorderFactory.createLineBorder(BORDER,1,true));
            formula.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) {
                    input.setBorder(BorderFactory.createLineBorder(BoardTheme.ACCENT,1,true));
                }
                @Override public void focusLost(java.awt.event.FocusEvent e) {
                    input.setBorder(BorderFactory.createLineBorder(BORDER,1,true));
                }
            });
            content.setOpaque(false);
            JPanel modes = new JPanel(new FlowLayout(FlowLayout.LEFT,0,0)); modes.setOpaque(false);
            mode.setFocusable(true); mode.setToolTipText("Choose calculation or manual entry; use Left/Right when focused");
            mode.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,0),"calculate");
            mode.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT,0),"manual");
            mode.getActionMap().put("calculate",new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { selectMode(0); }
            });
            mode.getActionMap().put("manual",new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { selectMode(1); }
            });
            modes.add(mode); content.add(modes,BorderLayout.NORTH);
            JPanel middle = new JPanel(); middle.setOpaque(false); middle.setLayout(new BoxLayout(middle,BoxLayout.Y_AXIS));
            addSection(middle,"Formula",input);
            middle.add(Box.createVerticalStrut(12));
            JPanel previewCard = new JPanel(new BorderLayout()) {
                @Override protected void paintComponent(Graphics source) {
                    Graphics2D g = (Graphics2D)source.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(Color.WHITE); g.fillRoundRect(0,0,getWidth(),getHeight(),14,14);
                    g.setColor(BORDER); g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,14,14); g.dispose();
                }
            };
            previewCard.setOpaque(false); previewCard.setPreferredSize(new Dimension(420,92));
            previewCard.add(preview,BorderLayout.CENTER); addSection(middle,"Preview",previewCard);
            middle.add(Box.createVerticalStrut(8));
            status.setFont(BoardTheme.font(11.5f)); status.setForeground(SECONDARY);
            status.setAlignmentX(Component.LEFT_ALIGNMENT);
            // A stable width prevents a new message from resizing the modal.
            status.setPreferredSize(new Dimension(420,20));
            middle.add(status); content.add(middle,BorderLayout.CENTER);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0)); actions.setOpaque(false);
            actions.add(cancel); actions.add(save); content.add(actions,BorderLayout.SOUTH);
            content.putClientProperty("glass.defaultAction",save);
            previewDelay.setRepeats(false);
            formula.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { changed(); }
                @Override public void removeUpdate(DocumentEvent e) { changed(); }
                @Override public void changedUpdate(DocumentEvent e) { changed(); }
            });
            mode.addSelectionListener(i -> changed());
            save.addActionListener(e -> submit()); cancel.addActionListener(e -> dismiss.run());
            updateModeText();
        }

        private static void addSection(JPanel parent,String label,JComponent child) {
            JLabel heading = new JLabel(label); heading.setFont(BoardTheme.semibold(12.5f));
            heading.setForeground(INK); heading.setAlignmentX(Component.LEFT_ALIGNMENT);
            child.setAlignmentX(Component.LEFT_ALIGNMENT);
            parent.add(heading); parent.add(Box.createVerticalStrut(5)); parent.add(child);
        }
        private void selectMode(int index) {
            if (!mode.isEnabled() || index == mode.getSelectedIndex()) return;
            mode.setSelectedIndex(index); changed();
        }
        private void updateModeText() {
            boolean manual = modeForSelection(mode.getSelectedIndex()) == FormulaEditSubmission.Mode.MANUAL;
            save.setText(manual ? "Save as Entered" : "Recalculate & Save");
            status.setForeground(SECONDARY);
            status.setText(manual ? "Save the expression without evaluating it." : "A new result will be calculated when you save.");
        }
        private void changed() {
            shownResult = "";
            previewGeneration++;
            updateModeText(); previewDelay.restart();
        }
        private void renderPreview() {
            if (!active) return;
            long generation = ++previewGeneration;
            String latex = formula.getText().trim();
            if (previewWorker != null) previewWorker.cancel(true);
            if (latex.isBlank()) { preview.setIcon(null); return; }
            String value = modeForSelection(mode.getSelectedIndex()) == FormulaEditSubmission.Mode.MANUAL ? "" : shownResult;
            PdfMaterial sample = item.withText(latex,value);
            previewWorker = new SwingWorker<>() {
                @Override protected BufferedImage doInBackground() { return MaterialRenderer.render(sample,408,82); }
                @Override protected void done() {
                    if (!active || generation != previewGeneration || isCancelled()) return;
                    try { preview.setIcon(new ImageIcon(get())); }
                    catch (Exception invalid) { preview.setIcon(null); }
                }
            };
            previewWorker.execute();
        }
        private void submit() {
            if (!active || !save.isEnabled()) return;
            String latex = formula.getText().trim();
            if (latex.isBlank()) { status.setForeground(ERROR); status.setText("Formula cannot be empty."); return; }
            FormulaEditSubmission.Mode selected = modeForSelection(mode.getSelectedIndex());
            if (selected == FormulaEditSubmission.Mode.MANUAL) {
                finish(FormulaEditSubmission.submit(latex,selected,recalculate)); return;
            }
            save.setEnabled(false); mode.setEnabled(false); formula.setEditable(false);
            status.setForeground(SECONDARY); status.setText("Recalculating…");
            calculation = new SwingWorker<>() {
                @Override protected FormulaEditSubmission.Revision doInBackground() {
                    return FormulaEditSubmission.submit(latex,selected,recalculate);
                }
                @Override protected void done() {
                    if (!active || isCancelled()) return;
                    try { finish(get()); }
                    catch (Exception problem) {
                        save.setEnabled(true); mode.setEnabled(true); formula.setEditable(true);
                        Throwable cause = problem.getCause()==null ? problem : problem.getCause();
                        status.setForeground(ERROR); status.setText("Cannot recalculate. Try Manual Entry.");
                        status.setToolTipText(cause.getMessage());
                    }
                }
            };
            calculation.execute();
        }
        private void finish(FormulaEditSubmission.Revision revision) {
            result = new Result(revision.formula(),revision.result()); dismiss.run();
        }
        Result show(Window owner) {
            try {
                GlassDialogs.edit(owner,"Edit Formula",content,close -> {
                    dismiss = close;
                    previewDelay.restart();
                    SwingUtilities.invokeLater(formula::requestFocusInWindow);
                });
                return result;
            } finally {
                active = false; previewDelay.stop();
                if (previewWorker != null) previewWorker.cancel(true);
                if (calculation != null && !calculation.isDone()) calculation.cancel(true);
            }
        }
    }
    static FormulaEditSubmission.Mode modeForSelection(int index) {
        return index == 1 ? FormulaEditSubmission.Mode.MANUAL : FormulaEditSubmission.Mode.CALCULATE;
    }
}
