package view.answer;

import entity.Stroke;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

// strokes that were calculated together, plus their answer (null = none yet)
public final class Formula {
    private final long id;
    private final Set<Stroke> strokes;
    private final double charHeight;
    private final long revision;
    private final CanvasAnswer answer;
    private final Rectangle erased;  // box before ink was taken out, till it's answered; null if none

    Formula(long id, Set<Stroke> strokes, long revision, CanvasAnswer answer) {
        this(id, strokes, revision, answer, null);
    }

    private Formula(long id, Set<Stroke> strokes, long revision, CanvasAnswer answer, Rectangle erased) {
        this.id = id;
        this.strokes = Collections.unmodifiableSet(new LinkedHashSet<>(strokes));
        this.charHeight = AnswerLayout.charHeight(strokes);
        this.revision = revision;
        this.answer = answer;
        this.erased = erased;
    }

    public long getId() {
        return id;
    }

    public Set<Stroke> getStrokes() {
        return strokes;
    }

    public double getCharHeight() {
        return charHeight;
    }

    // bumps whenever the ink changes, so old results can be spotted
    public long getRevision() {
        return revision;
    }

    public CanvasAnswer getAnswer() {
        return answer;
    }

    public Formula withAnswer(CanvasAnswer answer) {
        return new Formula(id, strokes, revision, answer);
    }

    Formula with(Set<Stroke> newStrokes, long newRevision, CanvasAnswer newAnswer) {
        return new Formula(id, newStrokes, newRevision, newAnswer);
    }

    // ink changed, answer gone; ink taken out keeps its spot so writing it back still lands on this one
    Formula edited(Set<Stroke> newStrokes, long newRevision) {
        boolean lost = !newStrokes.containsAll(strokes);
        return new Formula(id, newStrokes, newRevision, null, lost ? reach() : erased);
    }

    // box new ink is judged against: the ink plus where erased ink was
    Rectangle reach() {
        Rectangle box = AnswerLayout.bounds(strokes);
        return erased == null || box == null ? box : box.union(erased);
    }
}
