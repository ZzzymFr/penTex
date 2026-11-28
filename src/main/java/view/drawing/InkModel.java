package view.drawing;

import entity.Stroke;
import view.answer.FormulaBook;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The handwriting on the canvas: strokes in drawing order (world coordinates), the pen colour and
 * width each was written with, and the {@link FormulaBook} of formulas and answers they form.
 *
 * <p>Plain data with no Swing: input handling, recognition, rendering and saving all read it, and
 * only change it through these methods (Single Responsibility).
 */
public final class InkModel {
    public static final float DEFAULT_WIDTH = 4f;
    public static final Color DEFAULT_COLOR = Color.BLACK;

    private final List<Stroke> strokes = new ArrayList<>();
    // one read-only view, always the same object, so "nothing erased" identity checks keep working
    private final List<Stroke> strokesView = Collections.unmodifiableList(strokes);
    private final Map<Stroke, Color> colors = new HashMap<>();
    private final Map<Stroke, Float> widths = new HashMap<>();
    private final Map<Stroke, Float> widthsView = Collections.unmodifiableMap(widths);
    private FormulaBook book = new FormulaBook();  // calculated formulas + their answers
    private long revisions;                       // only goes up, undo too

    /** The strokes in drawing order; a live read-only view, always the same list object. */
    public List<Stroke> strokes() {
        return strokesView;
    }

    public boolean isEmpty() {
        return strokes.isEmpty();
    }

    /** Adds a stroke written with this pen. */
    public void add(Stroke stroke, Color color, float width) {
        colors.put(stroke, color);
        widths.put(stroke, width);
        strokes.add(stroke);
    }

    /** The strokes become exactly these, in this order (colours and widths are kept per stroke). */
    public void replaceStrokes(List<Stroke> next) {
        List<Stroke> copy = new ArrayList<>(next);
        strokes.clear();
        strokes.addAll(copy);
    }

    /** Removes every stroke (their colours and widths may stay; nothing reads them any more). */
    public void clearStrokes() {
        strokes.clear();
    }

    public Color colorOf(Stroke stroke) {
        Color c = colors.get(stroke);
        return c == null ? DEFAULT_COLOR : c;
    }

    public void setColor(Stroke stroke, Color color) {
        colors.put(stroke, color);
    }

    public float widthOf(Stroke stroke) {
        return widths.getOrDefault(stroke, DEFAULT_WIDTH);
    }

    public void setWidth(Stroke stroke, float width) {
        widths.put(stroke, width);
    }

    /** Pen width per stroke, read-only. */
    public Map<Stroke, Float> widths() {
        return widthsView;
    }

    /** A copy of the pen width per stroke. */
    public Map<Stroke, Float> widthsCopy() {
        return new HashMap<>(widths);
    }

    /** The colour of every stroke on the board right now. */
    public Map<Stroke, Color> colorsNow() {
        Map<Stroke, Color> now = new HashMap<>();
        for (Stroke s : strokes) {
            now.put(s, colorOf(s));
        }
        return now;
    }

    /** Erased pieces keep the colour and width of the stroke they came from (the nearest one). */
    public void inheritStyle(List<Stroke> removed, List<Stroke> added) {
        for (Stroke piece : added) {
            if (piece.isEmpty()) {
                continue;
            }
            entity.InkPoint first = piece.getPoints().get(0);
            Stroke best = null;
            double bestDist = Double.MAX_VALUE;
            for (Stroke old : removed) {
                for (entity.InkPoint p : old.getPoints()) {
                    double d = Point2D.distanceSq(p.getX(), p.getY(), first.getX(), first.getY());
                    if (d < bestDist) {
                        bestDist = d;
                        best = old;
                    }
                }
            }
            if (best != null) {
                colors.put(piece, colorOf(best));
                widths.put(piece, widthOf(best));
            }
        }
    }

    /** The pen most of these strokes were written with: an answer is written with it too. */
    public static float mostUsedPen(java.util.Collection<Stroke> formula, Map<Stroke, Float> widths) {
        return view.answer.MostInk.of(formula, s -> widths.getOrDefault(s, DEFAULT_WIDTH), DEFAULT_WIDTH);
    }

    public FormulaBook book() {
        return book;
    }

    /** A fresh revision number; revisions only ever go up, undo too. */
    public long nextRevision() {
        return ++revisions;
    }

    /** Replaces everything at once: after undo / redo, or when a saved canvas is opened. */
    public void replaceAll(List<Stroke> nextStrokes, Map<Stroke, Color> nextColors,
                           Map<Stroke, Float> nextWidths, FormulaBook nextBook) {
        replaceStrokes(nextStrokes);
        colors.clear();
        colors.putAll(nextColors);
        widths.clear();
        widths.putAll(nextWidths);
        book = nextBook;
    }
}
