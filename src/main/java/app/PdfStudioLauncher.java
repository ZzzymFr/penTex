package app;

import view.SwingUiThread;
import pdf_editor.rendering.PdfWrapTextLayout;
import pdf_editor.use_case.MaterialStore;
import pdf_editor.adapter.Us4InputBoundary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.data_access.PdfBoxOutputAdapter;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.adapter.CanvasSnapshotGateway;
import pdf_editor.adapter.MathLabCollector;
import pdf_editor.adapter.CanvasAnswerRecovery;
import pdf_editor.adapter.CanvasFormulaAnswer;
import pdf_editor.view.DrawingCanvasSnapshotAdapter;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;
import view.board.PdfPanel;
import view.canvas.SharedCanvas;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * US4 entry point: receives calculations, graphs and canvas events from the board and hands them to
 * the PDF studio. Its collaborators are injected (Dependency Inversion); {@link #create} is the
 * default wiring used by the application.
 */
public final class PdfStudioLauncher implements Us4InputBoundary {
    private final MaterialStore library;
    private final interface_adapter.UiThread ui;
    private final MathLabCollector collector;
    private final PdfEditorController controller;
    private Runnable showPdf = () -> { };
    private CanvasAnswerRecovery answerRecovery;
    private final history.use_case.HistoryInteractor history;
    private pdf_editor.adapter.FormulaRevisionCoordinator formulaRevision;

    /** Inject US2 through its existing public controller adapter, not its implementation. */
    public void setFormulaRecalculator(java.util.function.Function<String, String> evaluator) {
        formulaRevision = new pdf_editor.adapter.FormulaRevisionCoordinator(library, history,
                java.util.Objects.requireNonNull(evaluator));
    }
    public void reviseCanvasHistory(String oldFormula, String oldResult, String newFormula, String newResult)
            throws IOException {
        if (formulaRevision != null)
            formulaRevision.editCanvasHistory(controller, oldFormula, oldResult, newFormula, newResult);
    }

    /**
     * @param history the persistent History, or null for an isolated studio
     * @param ui      the thread materials and recovered results are published on
     */
    public PdfStudioLauncher(MaterialStore library, MathLabCollector collector, PdfEditorController controller,
                             history.use_case.HistoryInteractor history, interface_adapter.UiThread ui) {
        this.library = java.util.Objects.requireNonNull(library, "library");
        this.collector = java.util.Objects.requireNonNull(collector, "collector");
        this.controller = java.util.Objects.requireNonNull(controller, "controller");
        this.history = history;
        this.ui = java.util.Objects.requireNonNull(ui, "ui");
    }

    /** The application's wiring: one material store shared by the collector and the PDF editor. */
    public static PdfStudioLauncher create(history.use_case.HistoryInteractor history) {
        MaterialStore library = new MaterialStore();
        // History is a user-owned archive, not a mirror of live canvas strokes.
        MathLabCollector collector = new MathLabCollector(library, history, SwingUiThread.INSTANCE);
        DocumentEditor editing = new DocumentEditor();
        PdfBoxOutputAdapter pdfOutput = new PdfBoxOutputAdapter();
        PdfEditorController controller = new PdfEditorController(library, editing, new AutoLayoutInteractor(editing),
                new PdfExportInteractor(pdfOutput, pdfOutput), PdfWrapTextLayout::reflow);
        return new PdfStudioLauncher(library, collector, controller, history, SwingUiThread.INSTANCE);
    }

    /** An isolated studio without History. */
    public static PdfStudioLauncher create() {
        return create(null);
    }
    public PdfEditorController controller() { return controller; }
    public void onOpen(Runnable show) { showPdf = java.util.Objects.requireNonNull(show); }
    /** The document model is shared across overlay/split mode changes and stays editable. */
    public PdfPanel createPanel() {
        PdfPanel panel = new PdfPanel(controller);
        if (graphSessions != null) panel.setGraphSessions(graphSessions);
        if (formulaRevision != null) panel.editor().setFormulaEditing(formulaRevision);
        panel.setCanvasCapture(() -> snapshotGateway.capture());
        return panel;
    }
    private CanvasSnapshotGateway snapshotGateway = () -> null;

    /** Team Canvas is only read through its adapter; it is never modified by PDF Studio. */
    public void setSourceCanvas(SharedCanvas canvas) {
        if (answerRecovery != null) answerRecovery.dispose();
        answerRecovery = canvas == null ? null
                : new CanvasAnswerRecovery(() -> answersOn(canvas), collector::acceptCalculation, ui);
        snapshotGateway = canvas == null ? () -> null
                : new DrawingCanvasSnapshotAdapter(canvas);
    }
    // the view's formulas copied into the adapter's plain data, so the adapter never sees view classes
    private static java.util.List<CanvasFormulaAnswer> answersOn(SharedCanvas canvas) {
        java.util.List<CanvasFormulaAnswer> answers = new java.util.ArrayList<>();
        for (view.answer.Formula formula : canvas.getFormulas()) {
            view.answer.CanvasAnswer answer = formula.getAnswer();
            answers.add(answer == null ? CanvasFormulaAnswer.unanswered(formula.getId(), formula.getRevision())
                    : new CanvasFormulaAnswer(formula.getId(), formula.getRevision(), true, answer.isError(),
                    answer.getLatex(), answer.getText()));
        }
        return answers;
    }
    public MaterialStore getMaterialStore() { return library; }
    public MathLabCollector getCollector() { return collector; }
    @Override public void addCalculation(String latex, String result) { collector.acceptCalculation(latex, result); }
    public void addCalculationWithSource(long sourceId, String latex, String result) {
        Runnable publish = () -> {
            String displayResult = answerRecovery == null ? result
                    : answerRecovery.displayResult(sourceId, result);
            collector.acceptCalculation(sourceId, latex, displayResult);
            if (answerRecovery != null) answerRecovery.published(sourceId, latex, displayResult);
        };
        ui.run(publish);
    }
    public void synchronizeCanvas(SharedCanvas canvas) {
        if (canvas == null) return;
        Runnable refreshRecovery = () -> {
            // Formula definitions do not have an answer; they are still valid source ink.
            Set<Long> active = canvas.getDrawingCanvas().getFormulas().stream()
                    .map(formula -> formula.getId()).collect(Collectors.toSet());
            if (answerRecovery != null) answerRecovery.synchronize(active);
            // Deliberately do NOT prune Library or History when ink disappears.
            // Erasing or clearing a canvas must never delete saved materials.
        };
        ui.run(refreshRecovery);
    }
    public void addFormulaResult(String latex, String result) { addCalculation(latex, result); }
    @Override public void addGraph(String latex, BufferedImage image) { collector.acceptGraph(latex, image); }
    public void addInteractiveGraph(entity.graph.GraphDefinition definition, BufferedImage thumbnail) {
        collector.acceptInteractiveGraph(definition, thumbnail);
    }
    private java.util.function.Supplier<view.canvas.GraphSession> graphSessions;
    /** Dedicated graph sessions for the PDF view: restoration must not republish History records. */
    public void setGraphSessions(java.util.function.Supplier<view.canvas.GraphSession> factory) {
        graphSessions = java.util.Objects.requireNonNull(factory);
    }
    public void addGraphPng(String latex, Path png) throws IOException {
        BufferedImage image = ImageIO.read(png.toFile());
        if (image == null) throw new IOException("Not a readable PNG image: " + png);
        addGraph(latex, image);
    }
    public void addGraphPng(String latex, byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null) throw new IOException("Not a readable PNG image");
        addGraph(latex, image);
    }
    @Override public void openEditor() {
        ui.run(showPdf);
    }
}
