package app;

import view.SwingUiThread;
import pdf_editor.adapter.PngImages;
import java.awt.image.BufferedImage;
import history.data_access.FileHistoryRepository;
import history.use_case.HistoryInteractor;
import history.adapter.HistoryBoardPresenter;
import view.board.PdfPanel;
import pdf_editor.view.CanvasPdfDragController;
import pdf_editor.view.CanvasRegionCaptureController;
import pdf_editor.view.CanvasHistorySelectionMenu;
import pdf_editor.adapter.SelectedInkRegistry;
import pdf_editor.use_case.SelectionToHistory;
import view.canvas.GraphSession;
import view.canvas.GraphDropHandler;
import view.canvas.PhaseSession;
import interface_adapter.history.HistoryViewModel;
import interface_adapter.recognize.FormulaRecognizer;
import view.graph.PhaseSnapshot;
import view.DrawingCanvas;
import view.board.BoardTheme;
import view.board.CanvasWorkspaceDialogs;
import workspace.use_case.CanvasWorkspace;
import workspace.data_access.FileCanvasRepository;
import pdf_editor.view.CanvasHistoryEditMenu;
import view.board.BoardWindow;
import view.board.FloatingToolbar;
import view.board.HistoryPanel;
import view.canvas.AnswerCanvasPage;
import view.canvas.SharedCanvas;

import javax.swing.JFrame;
import javax.swing.JRootPane;

/**
 * Composition root of the one-board window: canvas, History and PDF panels, popup with Calculate
 * and Graph. It only connects the parts; the calculation and graph wiring, the shortcuts and the
 * window lifecycle live in {@link CalculationModule}, {@link GraphModule}, {@link WindowShortcuts}
 * and {@link WindowLifecycle}.
 */
public class StackedAppBuilder {
    private final DrawingCanvas drawing = new DrawingCanvas();
    private final SharedCanvas sharedCanvas = new SharedCanvas(drawing);
    private final HistoryInteractor history = new HistoryInteractor(
            new FileHistoryRepository(FileHistoryRepository.defaultDirectory()));
    private final PdfStudioLauncher pdfStudio = PdfStudioLauncher.create(history);
    private final CanvasWorkspace canvases = new CanvasWorkspace(
            new FileCanvasRepository(FileCanvasRepository.defaultDirectory()));

    {
        pdfStudio.setSourceCanvas(sharedCanvas);
        sharedCanvas.addFormulaChangeListener(id -> pdfStudio.synchronizeCanvas(sharedCanvas));
        drawing.addInkChangeListener(() -> SwingUiThread.INSTANCE.later(
                () -> pdfStudio.synchronizeCanvas(sharedCanvas)));
        sharedCanvas.addUserActionListener(() -> SwingUiThread.INSTANCE.later(
                () -> pdfStudio.synchronizeCanvas(sharedCanvas)));
    }

    public StackedAppBuilder setFormulaRecognizer(FormulaRecognizer recognizer) {
        sharedCanvas.setFormulaRecognizer(recognizer);
        return this;
    }

    public SharedCanvas getSharedCanvas() {
        return sharedCanvas;
    }

    public JFrame build() {
        CalculationModule calculation = new CalculationModule();
        GraphModule graphing = new GraphModule();
        // every graph on the canvas gets its own session; each new graph also goes to PDF Studio
        java.util.function.Supplier<GraphSession> graphSessions = graphing.graphSessions();
        java.util.function.Supplier<GraphSession> restoredGraphSessions = graphing.restoredGraphSessions();
        pdfStudio.setGraphSessions(restoredGraphSessions);
        // phase portraits of odes: a session per portrait, same popup; also go to PDF Studio
        java.util.function.Supplier<PhaseSession> phaseSessions = graphing.phaseSessions(
                state -> pdfStudio.addGraph(state.getLatex(), PhaseSnapshot.render(state, 880, 600)));

        // history
        HistoryViewModel historyViewModel = new HistoryViewModel();
        HistoryBoardPresenter historyPresenter = new HistoryBoardPresenter(history, historyViewModel, SwingUiThread.INSTANCE);
        historyPresenter.refresh();

        AnswerCanvasPage.FormulaResultSink results = (id, latex, result) -> {
            pdfStudio.addCalculationWithSource(id, latex, result);
            // MathLabCollector persists this calculation when it receives the same event.
        };
        drawing.setBackground(BoardTheme.CANVAS);
        drawing.setGridVisible(false);
        drawing.setMathAssetRenderer((latex, result, width, height) ->
                pdf_editor.rendering.MaterialRenderer.render(
                        new pdf_editor.domain.PdfMaterial("Canvas History", latex, result, null, 0, 0) { },
                        width, height));
        CanvasPdfDragController.install(drawing);
        pdf_editor.adapter.HistoryFormulaCalculator historyEditCalculator = calculation.historyCalculator();
        pdfStudio.setFormulaRecalculator(historyEditCalculator::calculate);
        CanvasHistoryEditMenu.install(drawing, (latex, result) ->
                pdf_editor.rendering.MaterialRenderer.render(
                        new pdf_editor.domain.PdfMaterial("Edited Math", latex, result, null, 0, 0) { },
                        800, 190), historyEditCalculator::calculate,
                (latex, result) -> {
                    if (latex.isBlank()) return;
                    pdfStudio.getCollector().recordSelectedFormula(latex, result);
                    if (result.isBlank()) pdfStudio.getMaterialStore().addFormula(latex);
                    else pdfStudio.getMaterialStore().addCalculation(latex, result);
                }, change -> {
                    try {
                        pdfStudio.reviseCanvasHistory(change.oldFormula(), change.oldResult(),
                                change.formula(), change.result());
                    } catch (java.io.IOException failure) {
                        System.err.println("Formula revision sync failed: " + failure.getMessage());
                    }
                });
        CanvasHistorySelectionMenu.install(drawing,
                new SelectionToHistory<BufferedImage>(sharedCanvas::recognizeFormula, pdfStudio.getMaterialStore(),
                        pdfStudio.getCollector()::recordSelectedFormula),
                new SelectedInkRegistry(drawing, pdfStudio.getMaterialStore()));
        AnswerCanvasPage page = new AnswerCanvasPage(sharedCanvas, calculation.evaluate, calculation.viewModel,
                results, graphSessions, graphing.graphCheck::canGraph, BoardTheme.CANVAS);
        page.setDefinitionCheck(calculation.definitionCheck::isDefinition);
        page.setCalculateCheck(calculation.calculateCheck::canCalculate);
        page.setQuestionCheck(calculation.questionCheck::isQuestion);
        page.setFaintEqualsCheck(calculation.calculateCheck::canCalculate);
        page.enablePhasePortrait(phaseSessions, graphing.phaseCheck::isDifferentialEquation);
        sharedCanvas.attachTo(page);
        PdfPanel pdfPanel = pdfStudio.createPanel();
        pdfPanel.editor().enablePersistence(new pdf_editor.use_case.PdfWorkspace(
                        new pdf_editor.data_access.FilePdfDocumentRepository(
                                pdf_editor.data_access.FilePdfDocumentRepository.defaultDirectory())),
                new pdf_editor.data_access.PdfBoxOutputAdapter());
        // Use the live selection (lasso or whole ink), not an outdated startup screenshot.
        pdfPanel.setCanvasCapture(() -> {
            try { return drawing.toFormulaImage(); }
            catch (RuntimeException empty) { return null; }
        });
        java.util.concurrent.atomic.AtomicReference<Runnable> cancelCapture = new java.util.concurrent.atomic.AtomicReference<>();
        // Open the existing History panel after a successful explicit screenshot capture.
        Runnable[] historyReveal = { () -> { } };
        Runnable revealHistory = () -> historyReveal[0].run();
        pdfPanel.setRegionCapture(() -> {
            if (cancelCapture.get() != null) {
                cancelCapture.getAndSet(null).run();
                return;
            }
            pdfPanel.setCaptureAreaActive(true);
            Runnable close = CanvasRegionCaptureController.begin(
                    page.boardArea(), drawing, image -> {
                        pdfStudio.getMaterialStore().addInk("Canvas screenshot", PngImages.encode(image));
                        revealHistory.run();
                    }, () -> {
                        cancelCapture.set(null);
                        pdfPanel.setCaptureAreaActive(false);
                    });
            cancelCapture.set(close);
        });
        // Save/Open are separate use-case commands; the board knows only the callbacks.
        java.util.concurrent.atomic.AtomicReference<CanvasWorkspaceDialogs> dialogs = new java.util.concurrent.atomic.AtomicReference<>();
        BoardWindow board = new BoardWindow(page, drawing,
                host -> new HistoryPanel(historyViewModel, host, (java.util.function.BooleanSupplier) historyPresenter::clearAll),
                pdfPanel, historyPresenter::delete,
                () -> dialogs.get().save(), () -> dialogs.get().saveAs(),
                () -> dialogs.get().open());
        historyReveal[0] = () -> board.setHistoryOpen(true);
        // A graph enters History once its native card has successfully appeared.
        // VIEW changes and opening saved canvases must not republish duplicate materials.
        page.setGraphCreatedListener(pdfStudio::addInteractiveGraph);
        page.setGraphRestoreSessions(restoredGraphSessions);
        page.setGraphDropHandler(new GraphDropHandler() {
            @Override public boolean accepts(java.awt.Point screen) {
                return board.acceptsInteractiveGraphDrop(screen);
            }
            @Override public boolean shouldAttemptDropOnRelease(java.awt.Point screen) {
                return board.acceptsInteractiveGraphDrop(screen);
            }
            @Override public boolean drop(entity.graph.GraphDefinition definition,
                    java.awt.image.BufferedImage preview, java.awt.Point screen) {
                return board.dropInteractiveGraph(definition, preview, screen);
            }
            @Override public void preview(java.awt.Point screen, double aspectRatio) {
                board.previewInteractiveGraphDrop(screen, aspectRatio);
            }
            @Override public void preview(java.awt.Point screen, double aspectRatio,
                                          java.awt.image.BufferedImage thumbnail) {
                board.previewInteractiveGraphDrop(screen, aspectRatio, thumbnail);
            }
            @Override public void clearPreview() {
                board.cancelInteractiveGraphPreview();
            }
        });
        dialogs.set(new CanvasWorkspaceDialogs(canvases, drawing, board));
        dialogs.get().setGraphRestorer(page::restoreSavedCanvasGraphs);
        FloatingToolbar toolbar = new FloatingToolbar(drawing, board, pdfPanel);
        board.addFloatingToolbar(toolbar.component());
        pdfPanel.setFullscreenAction(() -> board.setPdfFullscreen(!board.isPdfFullscreen()));
        pdfStudio.onOpen(() -> board.setPdfOpen(true));
        dialogs.get().restoreLatestAtStartup();
        board.setSaveStateSupplier(dialogs.get()::saveMenuState);
        drawing.addInkChangeListener(board::scheduleSaveStateRefresh);
        drawing.addGraphStateListener(board::scheduleSaveStateRefresh);
        drawing.addFormulaChangeListener(id -> board.scheduleSaveStateRefresh());
        drawing.addViewListener(board::scheduleSaveStateRefresh);

        JFrame frame = new JFrame("Math Lab");
        JRootPane root = frame.getRootPane();
        if (BoardTheme.MAC) {  // before the window is shown
            root.putClientProperty("apple.awt.fullWindowContent", true);
            root.putClientProperty("apple.awt.transparentTitleBar", true);
            root.putClientProperty("apple.awt.windowTitleVisible", false);
        }
        WindowShortcuts.install(root, drawing, pdfPanel,
                () -> {
                    dialogs.get().save();
                    board.refreshSaveState();
                },
                () -> {
                    dialogs.get().saveAs();
                    board.refreshSaveState();
                },
                () -> board.setPdfFullscreen(false));
        frame.setContentPane(board);
        WindowLifecycle.installCloseConfirmation(frame, () -> dialogs.get().confirmClose());
        frame.setSize(1280, 790);
        WindowLifecycle.onOpened(frame, toolbar::relayout);
        WindowLifecycle.onClosed(frame, pdfPanel::close);
        frame.setLocationRelativeTo(null);
        return frame;
    }
}
