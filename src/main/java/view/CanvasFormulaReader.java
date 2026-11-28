package view;

import entity.InkEditor;
import entity.Stroke;
import view.answer.AnswerLayout;
import view.answer.EqualsSpot;
import view.answer.Formula;
import view.drawing.CanvasDocument;
import view.drawing.CanvasSelection;
import view.drawing.FormulaGrouper;
import view.drawing.FormulaImageRenderer;
import view.drawing.InkModel;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Turns canvas ink into what handwriting recognition reads: binds loose strokes into formulas
 * when the writer pauses (through {@link FormulaGrouper}), and captures a formula as a
 * revision-tagged image at a size the model reads well ({@link FormulaCapture}).
 *
 * <p>One responsibility, recognition input (Single Responsibility); the canvas forwards its public
 * recognition API here and keeps answer placement, which moves the view.
 */
final class CanvasFormulaReader {
    static final double READABLE_MIN_HEIGHT = 30;   // char heights the model reads fine w/ pen 4, outside gets scaled
    static final double READABLE_MAX_HEIGHT = 240;
    // draws the recognition picture straight from the strokes, same pen as the screen
    private static final FormulaImageRenderer RENDERER = new FormulaImageRenderer(InkModel.DEFAULT_WIDTH);

    private final CanvasDocument document;
    private final InkModel inkModel;
    private final CanvasSelection picks;
    private final FormulaGrouper grouper;
    private final Runnable repaint;

    CanvasFormulaReader(CanvasDocument document, FormulaGrouper grouper, Runnable repaint) {
        this.document = Objects.requireNonNull(document, "document");
        this.inkModel = document.ink();
        this.picks = document.selection();
        this.grouper = Objects.requireNonNull(grouper, "grouper");
        this.repaint = Objects.requireNonNull(repaint, "repaint");
    }

    // bind loose ink near last as one formula (other session groups too); null if last gone/bound
    FormulaCapture registerNewInk(Stroke last, Collection<Stroke> session) {
        if (!inkModel.strokes().contains(last) || inkModel.book().isBound(last)) {
            return null;
        }
        // char size from everything written this round, a lone "-" would make it tiny
        List<Stroke> pool = new ArrayList<>();
        for (Stroke s : inkModel.strokes()) {
            if (!inkModel.book().isBound(s) && (s == last || session.contains(s))) {
                pool.add(s);
            }
        }
        grouper.regroup(last, pool);
        List<Stroke> round = new ArrayList<>(session);
        round.add(last);
        grouper.joinLoneEquals(round);
        return capture(inkModel.book().holding(last));  // after all of it, a later limit may have merged it again
    }

    // edited formula at the pause: regrouped w/ the guesses next to it first, ink added to half a fraction finds the rest
    FormulaCapture captureEdited(long id) {
        Formula f = inkModel.book().get(id);
        if (f == null || f.getAnswer() != null || !inkModel.book().isGuess(id)) {
            return captureForRecalculation(id);
        }
        List<Stroke> ink = inDrawingOrder(f);
        Stroke last = ink.get(ink.size() - 1);
        grouper.regroup(last, ink);
        return capture(inkModel.book().holding(last));
    }

    // Lasso over one answered formula plus extra ink means correct the grouping.
    // A lasso spanning multiple answered formulas must not merge them accidentally.
    FormulaCapture registerSelection() {
        List<Stroke> picked = document.selectedInk();
        if (picked.isEmpty()) return null;
        for (Formula f : inkModel.book().all()) {
            if (f.getStrokes().equals(new HashSet<>(picked))) {
                return captureForRecalculation(f.getId());
            }
        }
        Set<Long> answered = new HashSet<>();
        for (Stroke s : picked) {
            Formula f = inkModel.book().holding(s);
            if (f != null && f.getAnswer() != null) answered.add(f.getId());
        }
        if (answered.size() > 1) return null;
        Formula mine = inkModel.book().add(picked, inkModel.nextRevision());
        repaint.run();
        return capture(mine);
    }

    // lasso selection is exactly this formula's ink
    boolean selectionIsFormula(long id) {
        Formula f = inkModel.book().get(id);
        Set<Stroke> picked = new HashSet<>(document.selectedInk());
        return f != null && !picked.isEmpty() && f.getStrokes().equals(picked);
    }

    // pick is exactly one answered formula: its capture anyway, null if not
    FormulaCapture capturePickedAnswered() {
        Set<Stroke> picked = new HashSet<>(document.selectedInk());
        for (Formula f : inkModel.book().all()) {
            if (f.getAnswer() != null && !picked.isEmpty() && f.getStrokes().equals(picked)) {
                return capture(f);
            }
        }
        return null;
    }

    // lasso pick, else unbound ink, else redo every formula; throws if blank
    List<FormulaCapture> takeFormulasToCalculate() {
        if (document.isBlank()) {
            throw new IllegalStateException("The canvas is empty.");
        }
        List<Stroke> picked = document.formulaStrokes();
        if (!picks.hasStrokes()) {
            picked = new ArrayList<>();
            for (Stroke s : inkModel.strokes()) {
                if (!inkModel.book().isBound(s)) {
                    picked.add(s);
                }
            }
        }
        List<FormulaCapture> captures = new ArrayList<>();
        if (!picked.isEmpty()) {
            captures.add(capture(inkModel.book().add(picked, inkModel.nextRevision())));
        } else {
            for (Formula f : inkModel.book().all()) {
                captures.add(capture(inkModel.book().add(f.getStrokes(), inkModel.nextRevision())));  // fresh, no answer
            }
        }
        repaint.run();
        return captures;
    }

    // null if gone or already answered
    FormulaCapture captureForRecalculation(long id) {
        Formula f = inkModel.book().get(id);
        return f == null || f.getAnswer() != null ? null : capture(f);
    }

    // false if the formula is gone or changed since capture
    boolean isCurrent(long id, long revision) {
        Formula f = inkModel.book().get(id);
        return f != null && f.getRevision() == revision;
    }

    List<Stroke> inDrawingOrder(Formula f) {
        List<Stroke> ink = new ArrayList<>();
        for (Stroke s : inkModel.strokes()) {
            if (f.getStrokes().contains(s)) {
                ink.add(s);
            }
        }
        return ink;
    }

    FormulaCapture capture(Formula f) {
        List<Stroke> ink = inDrawingOrder(f);
        List<Stroke> pair = EqualsSpot.trailingPair(ink, f.getCharHeight());
        double k = recognitionScale(AnswerLayout.tallestStroke(ink));  // median would be x height for lowercase
        BufferedImage rest = pair.isEmpty() ? null : imageOf(readable(CanvasDocument.minus(ink, pair), k));  // in case the model loses the =
        return new FormulaCapture(f.getId(), f.getRevision(), imageOf(readable(ink, k)), rest);
    }

    // how much to blow up / shrink ink so pen 4 looks like what the model reads ok (probed 25..240)
    static double recognitionScale(double charHeight) {
        if (charHeight < READABLE_MIN_HEIGHT) {
            return READABLE_MIN_HEIGHT / charHeight;
        }
        if (charHeight > READABLE_MAX_HEIGHT) {
            return READABLE_MAX_HEIGHT / charHeight;
        }
        return 1;
    }

    // scaled copies, same list if k is 1 so normal ink renders exactly as before
    private static List<Stroke> readable(List<Stroke> ink, double k) {
        if (k == 1) {
            return ink;
        }
        List<Stroke> copies = new ArrayList<>();
        for (Stroke s : ink) {
            copies.add(InkEditor.scale(s, 0, 0, k));
        }
        return copies;
    }

    // cropped picture of some strokes, answers never included
    BufferedImage imageOf(List<Stroke> formula) {
        return RENDERER.render(formula);
    }
}
