package pdf_editor.adapter;

import interface_adapter.UiThread;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Read-only compatibility adapter between Canvas answers and PDF/History persistence.
 *
 * The Canvas may publish a formula-only source during Graph and later draw a computed
 * answer without publishing it again (the older exporter deduplicates by revision).
 * This adapter observes only such pending, *already exported* sources. It never runs
 * recognition/calculation, changes Canvas state, or creates history for a preview.
 *
 * Runs on the UI thread; its timer runs only while a formula-only source is
 * pending and stops when the associated answer appears or the source is removed.
 */
public final class CanvasAnswerRecovery {
    @FunctionalInterface public interface ResultSink {
        void accept(long sourceId, String latex, String result);
    }
    private record Pending(String latex, long revision) { }
    private final Supplier<List<CanvasFormulaAnswer>> formulaSource;
    private final ResultSink resultSink;
    private final Map<Long, Pending> pending = new HashMap<>();
    private final UiThread ui;
    private UiThread.Repeating timer;

    public CanvasAnswerRecovery(Supplier<List<CanvasFormulaAnswer>> formulaSource, ResultSink resultSink,
                                UiThread ui) {
        this.formulaSource = Objects.requireNonNull(formulaSource);
        this.resultSink = Objects.requireNonNull(resultSink);
        this.ui = Objects.requireNonNull(ui, "ui");
    }

    /**
     * The answer worker exports readable text, while Canvas owns a correctly typeset
     * answer LaTeX. Reuse that public representation for PDF and History when it exists.
     */
    public String displayResult(long sourceId, String rawResult) {
        if (rawResult == null || rawResult.isBlank()) return rawResult;
        for (CanvasFormulaAnswer formula : formulaSource.get()) {
            if (formula.id() != sourceId) continue;
            if (formula.answered() && !formula.error() && !formula.latex().isBlank()) {
                return formula.latex();
            }
            break;
        }
        return rawResult;
    }

    public void published(long id, String latex, String result) {
        if (latex == null || latex.isBlank()) return;
        if (result != null && !result.isBlank()) {
            pending.remove(id);
        } else {
            for (CanvasFormulaAnswer formula : formulaSource.get()) {
                if (formula.id() == id) {
                    pending.put(id, new Pending(latex, formula.revision()));
                    break;
                }
            }
        }
        recover();
        updateTimer();
    }

    public void synchronize(Set<Long> activeSourceIds) {
        Objects.requireNonNull(activeSourceIds);
        recover();
        pending.keySet().removeIf(id -> !activeSourceIds.contains(id));
        updateTimer();
    }

    private void recover() {
        if (pending.isEmpty()) return;
        for (CanvasFormulaAnswer formula : formulaSource.get()) {
            Pending record = pending.get(formula.id());
            if (record == null) continue;
            if (record.revision() != formula.revision()) {
                pending.remove(formula.id());
                continue;
            }
            if (!formula.answered() || formula.error()) continue;
            String result = formula.latex().isBlank() ? formula.text() : formula.latex();
            if (result.isBlank()) continue;
            pending.remove(formula.id());
            resultSink.accept(formula.id(), record.latex(), result);
        }
    }

    private void updateTimer() {
        if (pending.isEmpty()) {
            if (timer != null) timer.stop();
            return;
        }
        if (timer == null) {
            timer = ui.every(1000, () -> {
                recover();
                updateTimer();
            });
        }
        if (!timer.isRunning()) timer.start();
    }

    /** Release the UI timer if the Canvas attachment is replaced. */
    public void dispose() {
        pending.clear();
        if (timer != null) timer.stop();
    }
}
