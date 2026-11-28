package view.drawing;

import entity.InkPoint;
import entity.Stroke;
import interface_adapter.canvas.ViewportState;
import org.junit.jupiter.api.Test;
import workspace.domain.CanvasState;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The canvas model, viewport and helpers work without any Swing component. */
class CanvasModelTest {
    private static final double EPS = 1e-9;

    private static Stroke line(int x1, int y1, int x2, int y2) {
        Stroke s = new Stroke();
        for (int i = 0; i <= 10; i++) {
            s.addPoint(new InkPoint(x1 + (x2 - x1) * i / 10, y1 + (y2 - y1) * i / 10, i));
        }
        return s;
    }

    private static BufferedImage image() {
        return new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
    }

    @Test
    void inkModelKeepsPenPerStrokeAndOneStableView() {
        InkModel ink = new InkModel();
        List<Stroke> view = ink.strokes();
        Stroke red = line(0, 0, 10, 0);
        ink.add(red, Color.RED, 7);
        assertSame(view, ink.strokes());
        assertEquals(Color.RED, ink.colorOf(red));
        assertEquals(7, ink.widthOf(red), EPS);
        assertEquals(InkModel.DEFAULT_COLOR, ink.colorOf(line(0, 0, 1, 1)));

        Stroke piece = line(1, 0, 3, 0);
        ink.inheritStyle(List.of(red), List.of(piece));
        assertEquals(Color.RED, ink.colorOf(piece));
        assertEquals(7, ink.widthOf(piece), EPS);
        assertTrue(ink.nextRevision() < ink.nextRevision());
    }

    @Test
    void documentSnapshotRestoresInkPicturesAndCardsAndUnpicks() {
        CanvasDocument doc = new CanvasDocument(new CanvasEvents());
        Stroke s = line(0, 0, 50, 0);
        doc.ink().add(s, Color.BLUE, 4);
        doc.pictures().add(new CanvasPicture(image(), new Rectangle2D.Double(0, 0, 20, 10), "x", "1"));
        doc.graphs().add(new GraphSlot(image(), new Rectangle2D.Double(100, 0, 40, 30), 0, null));
        CanvasSnapshot saved = doc.snapshot();

        doc.selection().addStrokes(List.of(s));
        doc.removeEverything();
        assertTrue(doc.isBlank());

        doc.restore(saved);
        assertEquals(List.of(s), doc.ink().strokes());
        assertEquals(Color.BLUE, doc.ink().colorOf(s));
        assertEquals(1, doc.pictures().size());
        assertEquals(1, doc.graphs().size());
        assertTrue(doc.selection().isEmpty());
    }

    @Test
    void strokeEraserRemovesWhatItTouchesAndSaysSo() {
        CanvasEvents events = new CanvasEvents();
        int[] inkEvents = {0};
        events.onInkChanged(() -> inkEvents[0]++);
        CanvasDocument doc = new CanvasDocument(events);
        doc.ink().add(line(0, 0, 100, 0), Color.BLACK, 4);

        CanvasDocument.Erasure miss = doc.planErase(new Point2D.Double(50, 80), new Point2D.Double(60, 80), 3, false);
        assertFalse(miss.changed());
        assertSame(doc.ink().strokes(), miss.after());

        CanvasDocument.Erasure hit = doc.planErase(new Point2D.Double(50, -5), new Point2D.Double(50, 5), 3, false);
        assertTrue(hit.changed());
        doc.applyErase(hit);
        assertTrue(doc.isBlank());
        assertEquals(1, inkEvents[0]);
    }

    @Test
    void moveDragPreviewsAndCommitsPictureBoxes() {
        SelectionDrag drag = new SelectionDrag();
        drag.startMove(new Point2D.Double(10, 10));
        drag.dragTo(new Point2D.Double(14.6, 7));
        assertEquals(5, drag.dx());
        assertEquals(-3, drag.dy());
        Rectangle2D box = new Rectangle2D.Double(0, 0, 20, 10);
        assertEquals(new Rectangle2D.Double(5, -3, 20, 10), drag.preview(box));
        assertEquals(new Rectangle2D.Double(5, -3, 20, 10), drag.committed(box));
        drag.endMove();
        assertFalse(drag.isActive());
        assertSame(box, drag.preview(box));
    }

    @Test
    void resizeDragIsClampedAndKeepsPicturesAtLeastEightUnits() {
        SelectionDrag drag = new SelectionDrag();
        drag.startResize(2, new Point2D.Double(10, 10), new Point2D.Double(0, 0));
        drag.dragTo(new Point2D.Double(-100, -100));
        assertEquals(SelectionDrag.MIN_RESIZE, drag.factor(), EPS);
        Rectangle2D committed = drag.committed(new Rectangle2D.Double(0, 0, 20, 10));
        assertEquals(8, committed.getWidth(), EPS);
        assertEquals(8, committed.getHeight(), EPS);
    }

    @Test
    void graphCardsStackInsteadOfOverlappingAndTrackCovers() {
        GraphCardLayer layer = new GraphCardLayer();
        Object live = new Object();
        layer.add(new GraphSlot(image(), new Rectangle2D.Double(0, 0, 100, 50), 0, live));
        Rectangle2D next = layer.clearOfOtherCards(new Rectangle2D.Double(10, 10, 100, 50), 4);
        assertEquals(54, next.getY(), EPS);
        assertTrue(layer.setCovered(live, true));
        assertFalse(layer.setCovered(live, true));
        assertTrue(layer.isCovered(live));

        CanvasViewport viewport = new CanvasViewport();
        assertSame(live, layer.liveTagAt(new Point(50, 25), viewport));
        Point2D close = layer.closeOf(layer.newest(), viewport);
        assertSame(layer.newest(), layer.closeButtonAt(new Point((int) close.getX(), (int) close.getY()), viewport));
    }

    @Test
    void savedPlaceholderBecomesLiveOnlyWhereItWas() {
        GraphCardLayer layer = new GraphCardLayer();
        Rectangle2D where = new Rectangle2D.Double(5, 6, 70, 40);
        layer.add(new GraphSlot(image(), where, 0, new SavedGraphDefinition("y=x")));
        Object live = new Object();
        assertFalse(layer.replaceSavedPlaceholder(image(), new Rectangle2D.Double(0, 0, 70, 40), "y=x", live));
        assertTrue(layer.replaceSavedPlaceholder(image(), where, "y=x", live));
        assertSame(live, layer.newest().tag());
    }

    @Test
    void stateMapperRoundTripsInkPicturesCardsAndView() throws Exception {
        CanvasDocument doc = new CanvasDocument(new CanvasEvents());
        Stroke s = line(0, 0, 30, 20);
        doc.ink().add(s, new Color(10, 20, 30), 6);
        doc.pictures().add(new CanvasPicture(image(), new Rectangle2D.Double(1, 2, 20, 10), "a+b", "3"));
        doc.graphs().add(new GraphSlot(image(), new Rectangle2D.Double(40, 0, 60, 30), 0,
                new SavedGraphDefinition("encoded")));
        ViewportState view = new ViewportState(2, 15, -7);

        CanvasState saved = CanvasStateMapper.export(doc.ink(), doc.pictures(), doc.graphs().all(), view, tag -> "");
        long[] revisions = {0};
        CanvasStateMapper.Restored back = CanvasStateMapper.read(saved, () -> ++revisions[0]);

        assertEquals(1, back.strokes().size());
        assertEquals(s.getPoints().size(), back.strokes().get(0).getPoints().size());
        assertEquals(new Color(10, 20, 30), back.colors().get(back.strokes().get(0)));
        assertEquals(6, back.widths().get(back.strokes().get(0)), EPS);
        assertEquals("a+b", back.pictures().get(0).latex());
        assertEquals("encoded", ((SavedGraphDefinition) back.graphs().get(0).tag()).encoded());
        assertEquals(view, back.viewport());
    }

    @Test
    void viewportMapsBetweenScreenAndWorld() {
        CanvasViewport viewport = new CanvasViewport();
        viewport.set(new ViewportState(2, 10, 20));
        viewport.panBy(5, 0);
        Point2D world = viewport.toWorld(new Point(35, 40));
        assertEquals(10, world.getX(), EPS);
        assertEquals(10, world.getY(), EPS);
        assertEquals(new Rectangle2D.Double(35, 40, 8, 6),
                viewport.toScreen(new Rectangle2D.Double(10, 10, 4, 3)));
        assertEquals(2.5, viewport.toWorldLength(5), EPS);
    }

    @Test
    void eventsReachSubscribersInOrder() {
        CanvasEvents events = new CanvasEvents();
        List<String> log = new ArrayList<>();
        events.onFormulaChanged(id -> log.add("changed " + id));
        events.onFormulaMoved(id -> log.add("moved " + id));
        events.onGraphDrop(tag -> log.add("drop " + tag));
        events.formulaChanged(3);
        events.formulaMoved(4);
        events.graphDropped("card");
        assertEquals(List.of("changed 3", "moved 4", "drop card"), log);
    }

    @Test
    void pickingByLoopPicksInkInsideOnly() {
        CanvasDocument doc = new CanvasDocument(new CanvasEvents());
        Stroke inside = line(10, 10, 20, 10);
        Stroke outside = line(200, 200, 210, 200);
        doc.ink().add(inside, Color.BLACK, 4);
        doc.ink().add(outside, Color.BLACK, 4);
        doc.pickInside(List.of(new InkPoint(0, 0, 0), new InkPoint(50, 0, 0),
                new InkPoint(50, 50, 0), new InkPoint(0, 50, 0)));
        assertEquals(List.of(inside), doc.selectedInk());
        assertEquals(List.of(inside), doc.formulaStrokes());
        doc.selection().clear();
        assertEquals(2, doc.formulaStrokes().size());
        assertNull(doc.selectionBounds());
    }
}
