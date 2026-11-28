package view.drawing;

import entity.InkEditor;
import entity.InkPoint;
import entity.Stroke;
import view.answer.AnswerInk;
import view.answer.AnswerLayout;
import view.answer.CanvasAnswer;
import view.answer.Formula;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * The canvas content as a model with no Swing in it: the ink and its formulas ({@link InkModel}),
 * the pictures, the graph cards, what is picked and the drag in progress, and the edits that
 * change them. It is the originator of the undo memento ({@link #snapshot()} / {@link #restore}).
 *
 * <p>The Swing canvas is the view and controller around it: it turns input into these edits
 * (wrapped in commands), repaints and places things on screen. Edits announce what changed
 * through {@link CanvasEvents} (Observer pattern).
 */
public final class CanvasDocument {

    /** What one eraser segment would remove; {@code after} is the same list object when no ink goes. */
    public record Erasure(List<Stroke> after, List<Long> rubbed, List<CanvasPicture> imagesTouched,
                          boolean changed) {
    }

    private final InkModel inkModel = new InkModel();
    private final List<CanvasPicture> pictures = new ArrayList<>();
    private final GraphCardLayer graphs = new GraphCardLayer();
    private final CanvasSelection picks = new CanvasSelection();
    private final SelectionDrag drag = new SelectionDrag();
    private final CanvasEvents events;

    public CanvasDocument(CanvasEvents events) {
        this.events = Objects.requireNonNull(events, "events");
    }

    // ---------------------------------------------------------------- parts

    public InkModel ink() {
        return inkModel;
    }

    /** The pictures, oldest first (the live list the canvas edits). */
    public List<CanvasPicture> pictures() {
        return pictures;
    }

    public GraphCardLayer graphs() {
        return graphs;
    }

    public CanvasSelection selection() {
        return picks;
    }

    public SelectionDrag drag() {
        return drag;
    }

    /** No ink, no pictures, no graph cards. */
    public boolean isBlank() {
        return inkModel.strokes().isEmpty() && pictures.isEmpty() && graphs.isEmpty();
    }

    /** Only the deliberately picked strokes, in drawing order. */
    public List<Stroke> selectedInk() {
        List<Stroke> selected = new ArrayList<>();
        for (Stroke stroke : inkModel.strokes()) if (picks.contains(stroke)) selected.add(stroke);
        return List.copyOf(selected);
    }

    // ---------------------------------------------------------------- memento

    /** Puts the content back as saved, with nothing picked. */
    public void restore(CanvasSnapshot back) {
        inkModel.replaceAll(back.strokes(), back.colors(), back.widths(), back.book().copy());
        pictures.clear(); pictures.addAll(back.pictures());
        graphs.restore(back.graphs());
        picks.setPrimaryPicture(null);
        picks.clearPictures();
        picks.clearStrokes(); picks.clearAnswers();
    }

    /** Clear: removes all ink, answers, pictures and graph cards. */
    public void removeEverything() {
        pictures.clear();picks.setPrimaryPicture(null);picks.clearPictures();
        graphs.clear();
        inkModel.clearStrokes();
        for (Formula f : inkModel.book().all()) {
            inkModel.book().remove(f.getId());
        }
        picks.clearStrokes();
        picks.clearAnswers();
    }

    // ---------------------------------------------------------------- eraser

    /**
     * What rubbing from {@code from} to {@code to} (world coordinates) would remove: precise mode
     * cuts strokes where the eraser passes, stroke mode removes each stroke it touches. Answers
     * whose ink it touches and pictures it touches go too.
     */
    public Erasure planErase(Point2D from, Point2D to, double radius, boolean precise) {
        List<Stroke> after = precise
                ? InkEditor.eraseAlong(inkModel.strokes(), from.getX(), from.getY(), to.getX(), to.getY(), radius)
                : InkEditor.removeTouchedAlong(inkModel.strokes(), from.getX(), from.getY(), to.getX(), to.getY(), radius);
        List<Long> rubbed = new ArrayList<>();
        for (Formula f : inkModel.book().all()) {
            CanvasAnswer a = f.getAnswer();
            if (a != null && AnswerInk.touches(a, a.getPenWidth(), from.getX(), from.getY(), to.getX(), to.getY(), radius)) {
                rubbed.add(f.getId());  // the ink, not the empty bits of its box
            }
        }
        // Imported History items are canvas objects: erase them while dragging too,
        // with the same single checkpoint as strokes erased in this gesture.
        List<CanvasPicture> imagesTouched = new ArrayList<>();
        for (CanvasPicture asset : pictures) {
            if (asset.box().intersects(to.getX()-radius, to.getY()-radius, 2*radius, 2*radius)
                    || asset.box().intersects(from.getX()-radius, from.getY()-radius, 2*radius, 2*radius))
                imagesTouched.add(asset);
        }
        boolean changed = after != inkModel.strokes() || !rubbed.isEmpty() || !imagesTouched.isEmpty();
        return new Erasure(after, rubbed, imagesTouched, changed);
    }

    /** Carries out a planned rub (all of it, or the part of a drag after the first change). */
    public void applyErase(Erasure erasure) {
        List<Stroke> after = erasure.after();
        List<Long> rubbed = erasure.rubbed();
        List<CanvasPicture> imagesTouched = erasure.imagesTouched();
        if (!imagesTouched.isEmpty()) {
            pictures.removeAll(imagesTouched);
            picks.removePictures(imagesTouched);
            if (imagesTouched.contains(picks.primaryPicture())) picks.setPrimaryPicture(null);
            events.inkChanged();
        }
        for (long id : rubbed) {
            inkModel.book().remove(id);  // whole answer goes, formula unbound
        }
        if (after != inkModel.strokes()) {
            List<Stroke> before = new ArrayList<>(inkModel.strokes());
            inkModel.replaceStrokes(after);
            inkModel.inheritStyle(minus(before, after), minus(after, before));
            inkChanged(minus(before, after), minus(after, before));
        }
    }

    /** Picks the topmost picture at this world point for moving; false if there is none. */
    public boolean pickPictureAt(Point2D p) {
        for (int i = pictures.size() - 1; i >= 0; i--) {
            CanvasPicture asset = pictures.get(i);
            if (asset.box().contains(p)) {
                picks.setPrimaryPicture(asset);
                picks.addPicture(asset);
                return true;
            }
        }
        return false;
    }

    /** Picks the strokes, pictures and answers inside the loop (world coordinates). */
    public void pickInside(List<InkPoint> lassoPath) {
        picks.addStrokes(InkEditor.selectInside(inkModel.strokes(), lassoPath));
        if (lassoPath.size() >= 3) {
            for (CanvasPicture asset : pictures) {
                if (InkEditor.isInside(lassoPath, asset.box().getCenterX(), asset.box().getCenterY())) {
                    picks.addPicture(asset);
                    picks.setPrimaryPicture(asset);
                }
            }
            for (Formula f : inkModel.book().all()) {
                CanvasAnswer a = f.getAnswer();
                if (a != null && InkEditor.isInside(lassoPath, a.getBox().getCenterX(), a.getBox().getCenterY())) {
                    picks.addAnswer(f.getId());
                }
            }
        }
    }

    /** Replaces each picked stroke, answer and picture with its changed copy; the copies stay picked. */
    public void applyTransform(UnaryOperator<Stroke> change, UnaryOperator<CanvasAnswer> answerChange) {
        if (picks.hasPictures()) {
            // Apply the same affine gesture to every imported History item. The
            // underlying formula/answer recognition logic remains untouched.
            Set<CanvasPicture> replacements = new java.util.LinkedHashSet<>();
            for (CanvasPicture current : picks.pictures()) {
                CanvasPicture next = current.withBox(drag.committed(current.box()));
                pictures.set(pictures.indexOf(current), next);
                replacements.add(next);
                if (picks.primaryPicture() == current) picks.setPrimaryPicture(next);
            }
            picks.clearPictures();
            picks.addPictures(replacements);
        }
        List<Stroke> after = new ArrayList<>();
        Map<Stroke, Stroke> copies = new HashMap<>();
        for (Stroke stroke : inkModel.strokes()) {
            if (picks.contains(stroke)) {
                Stroke copy = change.apply(stroke);
                after.add(copy);
                copies.put(stroke, copy);
                inkModel.setColor(copy, inkModel.colorOf(stroke));
                inkModel.setWidth(copy, inkModel.widthOf(stroke));
            } else {
                after.add(stroke);
            }
        }
        inkModel.replaceStrokes(after);
        // whole formula moved: same content, answer tags along
        List<Stroke> removed = new ArrayList<>(picks.strokes());
        Set<Long> moved = new HashSet<>();
        for (Formula f : inkModel.book().all()) {
            if (picks.containsAll(f.getStrokes())) {
                inkModel.book().moveWhole(f.getId(), copies, answerChange);
                removed.removeAll(f.getStrokes());
                moved.add(f.getId());
            }
        }
        for (long id : picks.answerIds()) {
            Formula f = inkModel.book().get(id);
            if (f != null && f.getAnswer() != null && !moved.contains(id)) {
                inkModel.book().put(f.withAnswer(answerChange.apply(f.getAnswer())));
            }
        }
        List<Stroke> added = new ArrayList<>();
        for (Stroke s : removed) {
            added.add(copies.get(s));
        }
        inkChanged(removed, added);  // partly moved formulas count as edited
        picks.clearStrokes();
        picks.addStrokes(copies.values());
        for (long id : moved) {
            events.formulaMoved(id);
        }
    }

    /** The selected strokes as they look right now, including a move or resize being dragged. */
    public List<Stroke> selectionPreview() {
        List<Stroke> preview = new ArrayList<>();
        for (Stroke stroke : picks.strokes()) {
            preview.add(drag.preview(stroke));
        }
        return preview;
    }

    // selected answers as they look right now, drag included
    public List<CanvasAnswer> answerPreview() {
        List<CanvasAnswer> preview = new ArrayList<>();
        for (long id : picks.answerIds()) {
            Formula f = inkModel.book().get(id);
            CanvasAnswer a = f == null ? null : f.getAnswer();
            if (a == null) {
                continue;
            }
            preview.add(drag.preview(a));
        }
        return preview;
    }

    /** Nothing that can be seen is picked. */
    public boolean nothingSelected() {
        return !picks.hasStrokes() && answerPreview().isEmpty() && !picks.hasPictures();
    }

    /** Removes the picked strokes, answers (their ink stays) and pictures. */
    public void removeSelected() {
        for (long id : picks.answerIds()) {
            inkModel.book().remove(id);  // unbinds the formula, ink stays
        }
        picks.clearAnswers();
        if (picks.hasPictures()) {
            pictures.removeAll(picks.pictures());
            picks.clearPictures();
            picks.setPrimaryPicture(null);
        }
        List<Stroke> after = new ArrayList<>(inkModel.strokes());
        after.removeAll(picks.strokes());
        inkModel.replaceStrokes(after);
        inkChanged(new ArrayList<>(picks.strokes()), List.of());
        picks.clearStrokes();
    }

    /** Gives the picked strokes and answers a new colour (same stroke objects). */
    public void paintSelected(Color color) {
        for (Stroke s : picks.strokes()) {
            inkModel.setColor(s, color);  // same stroke objects, formulas + library refs stay
        }
        for (long id : picks.answerIds()) {
            Formula f = inkModel.book().get(id);
            if (f != null && f.getAnswer() != null) {
                inkModel.book().put(f.withAnswer(f.getAnswer().withColor(color)));
            }
        }
    }

    /** The selection's outline in world coordinates (including a drag in progress), or null. */
    public Rectangle selectionBounds() {
        if (nothingSelected()) {
            return null;
        }
        Rectangle bounds = AnswerLayout.bounds(selectionPreview());
        for (CanvasPicture picture : picks.pictures()) {
            Rectangle image = picturePreviewBounds(picture).getBounds();
            if (bounds == null) bounds = image; else bounds.add(image);
        }
        for (CanvasAnswer a : answerPreview()) {
            if (bounds == null) {
                bounds = a.getBox().getBounds();
            } else {
                bounds.add(a.getBox().getBounds());
            }
        }
        int pad = (int) InkModel.DEFAULT_WIDTH + 4;
        bounds.grow(pad, pad);
        return bounds;
    }

    /** The box around all the ink's points (world coordinates), or null if there is no ink. */
    public Rectangle inkBounds() {
        return inkModel.strokes().isEmpty() ? null : AnswerLayout.bounds(inkModel.strokes());
    }

    /** The memento of the content (ink, formulas, pictures, graph cards). */
    public CanvasSnapshot snapshot() {
        return new CanvasSnapshot(new ArrayList<>(inkModel.strokes()), inkModel.colorsNow(), inkModel.widthsCopy(),
                inkModel.book().copy(), new ArrayList<>(pictures), graphs.snapshot());
    }

    // a - b, by identity
    public static List<Stroke> minus(List<Stroke> a, List<Stroke> b) {
        Set<Stroke> drop = new HashSet<>(b);
        List<Stroke> left = new ArrayList<>();
        for (Stroke s : a) {
            if (!drop.contains(s)) {
                left.add(s);
            }
        }
        return left;
    }

    // tell the book, then whoever listens, which formulas got edited
    public void inkChanged(List<Stroke> removed, List<Stroke> added) {
        if (removed.isEmpty() && added.isEmpty()) {
            return;
        }
        for (long id : inkModel.book().strokesReplaced(removed, added, inkModel::nextRevision)) {
            events.formulaChanged(id);
        }
        events.inkChanged();
    }

    /** The same in-progress History geometry drives both the artwork and its selection outline. */
    public Rectangle2D picturePreviewBounds(CanvasPicture picture) {
        Rectangle2D box = picture.box();
        if (!picks.isPicked(picture)) {
            return box;
        }
        return drag.preview(box);
    }

    /**
     * The strokes that make up "the formula": the lasso selection if there is one (so one formula
     * can be picked out when several are written), otherwise all the ink. In drawing order.
     */
    public List<Stroke> formulaStrokes() {
        if (!picks.hasStrokes()) {
            return inkModel.strokes();
        }
        List<Stroke> picked = new ArrayList<>();
        for (Stroke stroke : inkModel.strokes()) {
            if (picks.contains(stroke)) {
                picked.add(stroke);
            }
        }
        return picked;
    }
}
