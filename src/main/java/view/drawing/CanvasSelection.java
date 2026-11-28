package view.drawing;

import entity.Stroke;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What is picked on the canvas with the lasso or the box: strokes (by identity), answers (by
 * formula id) and pictures, plus the primary picture that the Edit action works on.
 *
 * <p>State only (Single Responsibility); the canvas decides what picking means and repaints.
 * Read-only views keep the insertion / hash order of the underlying sets.
 */
public final class CanvasSelection {
    private final Set<Stroke> strokes = new HashSet<>();
    private final Set<Stroke> strokesView = Collections.unmodifiableSet(strokes);
    private final Set<Long> answers = new HashSet<>();  // formula ids
    private final Set<Long> answersView = Collections.unmodifiableSet(answers);
    private final Set<CanvasPicture> pictures = new LinkedHashSet<>();  // multi-item lasso selection
    private final Set<CanvasPicture> picturesView = Collections.unmodifiableSet(pictures);
    private CanvasPicture primaryPicture;  // primary imported History object for the Edit action

    // ---------------------------------------------------------------- strokes

    /** The picked strokes (read-only view). */
    public Set<Stroke> strokes() {
        return strokesView;
    }

    public boolean hasStrokes() {
        return !strokes.isEmpty();
    }

    public boolean contains(Stroke stroke) {
        return strokes.contains(stroke);
    }

    public boolean containsAll(Collection<Stroke> ink) {
        return strokes.containsAll(ink);
    }

    public void addStrokes(Collection<Stroke> ink) {
        strokes.addAll(ink);
    }

    public void clearStrokes() {
        strokes.clear();
    }

    // ---------------------------------------------------------------- answers

    /** Ids of the formulas whose answers are picked (read-only view). */
    public Set<Long> answerIds() {
        return answersView;
    }

    public void addAnswer(long formulaId) {
        answers.add(formulaId);
    }

    public boolean isAnswerPicked(long formulaId) {
        return answers.contains(formulaId);
    }

    public void clearAnswers() {
        answers.clear();
    }

    // ---------------------------------------------------------------- pictures

    /** The picked pictures in the order they were picked (read-only view). */
    public Set<CanvasPicture> pictures() {
        return picturesView;
    }

    public boolean hasPictures() {
        return !pictures.isEmpty();
    }

    public boolean isPicked(CanvasPicture picture) {
        return pictures.contains(picture);
    }

    public void addPicture(CanvasPicture picture) {
        pictures.add(picture);
    }

    public void addPictures(Collection<CanvasPicture> picked) {
        pictures.addAll(picked);
    }

    public void removePicture(CanvasPicture picture) {
        pictures.remove(picture);
    }

    public void removePictures(Collection<CanvasPicture> gone) {
        pictures.removeAll(gone);
    }

    public void clearPictures() {
        pictures.clear();
    }

    /** The picture the Edit action works on, or null. */
    public CanvasPicture primaryPicture() {
        return primaryPicture;
    }

    public void setPrimaryPicture(CanvasPicture picture) {
        primaryPicture = picture;
    }

    // ---------------------------------------------------------------- everything

    /** Nothing at all is picked. */
    public boolean isEmpty() {
        return strokes.isEmpty() && answers.isEmpty() && pictures.isEmpty();
    }

    /** Unpicks everything. */
    public void clear() {
        strokes.clear();
        answers.clear();
        pictures.clear();
        primaryPicture = null;
    }
}
