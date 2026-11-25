package entity.graph;

import entity.ImplicitCurve;
import entity.ImplicitCurveReader;
import entity.LatexParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImplicitCurvePlotTest {
    private static final Viewport TEN = new Viewport(-10, 10, -10, 10);

    private static ImplicitCurve read(String latex) throws LatexParseException {
        return new ImplicitCurveReader().read(latex);
    }

    @Test
    void readerWantsExactlyXAndY() {
        assertThrows(LatexParseException.class, () -> read("y^2=4"));
        assertThrows(LatexParseException.class, () -> read("x+y+z=1"));
        assertThrows(LatexParseException.class, () -> read("x^2+y^2"));
        assertThrows(LatexParseException.class, () -> read("x<y"));
    }

    @Test
    void traceSnapsToTheNearestPointOnTheCircle() throws LatexParseException {
        ImplicitCurvePlot plot = new ImplicitCurvePlot(read("x^2+y^2=25"), TEN);
        TracePoint p = plot.trace(0.2, 5.3, 0.1);
        assertNotNull(p);
        assertEquals(5, Math.hypot(p.getX(), p.getY()), 0.05);
        assertTrue(p.getY() > 4.5, "top of the circle, not the bottom");
        assertNull(plot.trace(0, 0, 0.1), "middle of the circle is far from the line");
    }

    @Test
    void poleIsNotDrawnAsALine() throws LatexParseException {
        ImplicitCurvePlot plot = new ImplicitCurvePlot(read("xy=1"), TEN);
        for (Curve.Piece piece : plot.getCurve().getPieces()) {
            double[] xs = piece.getXs();
            double[] ys = piece.getYs();
            for (int k = 0; k < xs.length; k++) {
                assertEquals(1, xs[k] * ys[k], 0.05);
            }
        }
        assertEquals(2, plot.getCurve().getPieces().size(), "two branches of the hyperbola");
    }

    @Test
    void zoomKeepsHomeAndVisitorKnowsIt() throws LatexParseException {
        ImplicitCurvePlot plot = new ImplicitCurvePlot(read("x^2+y^2=1"), TEN);
        Plot zoomed = plot.lookingAt(TEN.zoomed(0, 0, 0.5));
        assertEquals(TEN, zoomed.getHome());
        assertSame(plot.getCurve(), plot.getCurve());
        String kind = zoomed.accept(new PlotVisitor<String>() {
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
                return "xyz";
            }

            @Override
            public String visitImplicitCurve(ImplicitCurvePlot p) {
                return "xy";
            }
        });
        assertEquals("xy", kind);
    }
}
