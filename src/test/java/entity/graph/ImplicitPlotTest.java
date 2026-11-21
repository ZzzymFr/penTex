package entity.graph;

import entity.ImplicitSurface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImplicitPlotTest {
    private static final Viewport TEN = new Viewport(-10, 10, -10, 10);
    private final ImplicitSurface sphere = new ImplicitSurface("x^2+y^2+z^2=1", (x, y, z) -> x * x + y * y + z * z - 1);
    private final ImplicitSurface plane = new ImplicitSurface("x+y+z=1", (x, y, z) -> x + y + z - 1);

    @Test
    void traceGivesTheTopOfTheSurface() {
        ImplicitPlot plot = new ImplicitPlot(sphere, new Viewport(-2, 2, -2, 2), -2, 2);
        TracePoint p = plot.trace(0.6, 0, 0);
        assertEquals(0.8, p.getZ(), 1e-9);
        assertTrue(p.isSurface());
        assertNull(plot.trace(1.5, 0, 0), "misses the sphere");
        assertNull(plot.trace(3, 0, 0), "off the floor");
    }

    @Test
    void zoomScalesTheZRangeTooAndKeepsHome() {
        ImplicitPlot plot = new ImplicitPlot(plane, TEN, -5, 5);
        ImplicitPlot closer = (ImplicitPlot) plot.lookingAt(TEN.zoomed(0, 0, 0.5));
        assertEquals(-2.5, closer.getZMin(), 1e-12);
        assertEquals(2.5, closer.getZMax(), 1e-12);
        assertEquals(TEN, closer.getHome());
        ImplicitPlot back = (ImplicitPlot) closer.lookingAt(closer.getHome());
        assertEquals(-5, back.getZMin(), 1e-12);
    }

    @Test
    void meshIsCutOnceAndVisitorKnowsIt() {
        ImplicitPlot plot = new ImplicitPlot(plane, TEN, -5, 5);
        assertFalse(plot.getMesh().isEmpty());
        assertSame(plot.getMesh(), plot.getMesh());
        assertEquals("x+y+z=1", plot.getLatex());
        String kind = plot.accept(new PlotVisitor<String>() {
            @Override
            public String visitCurve(CurvePlot p) {
                return "2d";
            }

            @Override
            public String visitSurface(SurfacePlot p) {
                return "3d";
            }

            @Override
            public String visitImplicit(ImplicitPlot p) {
                return "implicit";
            }
        });
        assertEquals("implicit", kind);
    }

    @Test
    void smallSphereGetsABoxItFillsAndStaysRound() {
        ImplicitPlot plot = AutoBox.around(sphere, TEN);
        Viewport v = plot.getViewport();
        assertTrue(v.width() <= 5, "zoomed in on the sphere: " + v);
        assertTrue(v.getXMin() < -1 && v.getXMax() > 1);
        assertTrue(plot.getZMin() < -1 && plot.getZMax() > 1);
        assertEquals(0, (v.getXMin() + v.getXMax()) / 2, 1e-9);
        // box is 0.72 as tall as wide on the card, z range follows so a sphere looks round
        assertEquals(AutoBox.BOX_HEIGHT, (plot.getZMax() - plot.getZMin()) / v.width(), 1e-9);
        assertEquals(plot.getViewport(), plot.getHome());
    }

    @Test
    void surfaceThatFillsTheBoxKeepsTheAskedFloor() {
        ImplicitPlot plot = AutoBox.around(plane, TEN);
        assertEquals(TEN, plot.getViewport());
        assertEquals(-7.2, plot.getZMin(), 1e-9);
        assertEquals(7.2, plot.getZMax(), 1e-9);
    }

    @Test
    void offCentreSphereIsFollowed() {
        ImplicitSurface moved = new ImplicitSurface("s", (x, y, z) -> (x - 4) * (x - 4) + y * y + (z + 2) * (z + 2) - 1);
        ImplicitPlot plot = AutoBox.around(moved, TEN);
        assertTrue(plot.getViewport().containsX(4.9) && plot.getViewport().containsX(3.1));
        assertTrue(plot.getZMin() < -2.9 && plot.getZMax() > -1.1);
    }
}
