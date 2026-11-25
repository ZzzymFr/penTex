package entity.graph;

import entity.ImplicitCurve;
import entity.ImplicitSurfaceReader;
import entity.LatexFunctionParser;
import entity.MathFunction;
import entity.SurfaceFunction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// type some of x / y / z on a graph, get the rest
class PlotLookupTest {
    private static final Viewport WINDOW = new Viewport(-10, 10, -10, 10);
    private final LatexFunctionParser parser = new LatexFunctionParser();

    private CurvePlot curve(String... latexes) throws Exception {
        List<MathFunction> fs = new java.util.ArrayList<>();
        for (String latex : latexes) {
            fs.add(parser.parse(latex));
        }
        return new CurvePlot(fs, WINDOW);
    }

    private static LookupResult find(Plot plot, double... values) {
        return plot.accept(new PlotLookup(LookupQuery.of(values)));
    }

    private static final double NO = Double.NaN;

    @Test
    void xGivesYOnEveryFunction() throws Exception {
        LookupResult r = find(curve("y=x^2", "y=2x+1"), 3, NO);
        assertEquals(LookupResult.Problem.NONE, r.getProblem());
        assertEquals(2, r.getPoints().size());
        assertEquals(9, r.getPoints().get(0).y(), 1e-12);
        assertEquals(0, r.getPoints().get(0).layer());
        assertEquals(7, r.getPoints().get(1).y(), 1e-12);
        assertEquals(1, r.getPoints().get(1).layer());
        assertTrue(Double.isNaN(r.getPoints().get(0).z()));
        assertFalse(r.isInView(), "worked out from the formula, not searched");
    }

    @Test
    void xOutsideTheWindowStillWorksStraightFromTheFormula() throws Exception {
        LookupResult r = find(curve("y=x^2"), 100, NO);
        assertEquals(10000, r.getPoints().get(0).y(), 1e-9);
    }

    @Test
    void yGivesEveryXInView() throws Exception {
        LookupResult r = find(curve("y=\\sin(x)"), NO, 0.5);
        assertTrue(r.isInView());
        // pi/6 + 2k pi and 5pi/6 + 2k pi in [-10, 10]: -9.95, -5.76, -3.67, 0.52, 2.62, 6.81, 8.9
        assertEquals(7, r.getPoints().size());
        for (LookupResult.Point p : r.getPoints()) {
            assertEquals(0.5, Math.sin(p.x()), 1e-9);
        }
    }

    @Test
    void touchingTheValueCountsWithoutCrossingIt() throws Exception {
        LookupResult r = find(curve("y=(x-1.234)^2"), NO, 0);
        assertEquals(1, r.getPoints().size());
        assertEquals(1.234, r.getPoints().get(0).x(), 1e-6);
    }

    @Test
    void aPoleIsNotARoot() throws Exception {
        LookupResult r = find(curve("y=1/x"), NO, 0);
        assertEquals(LookupResult.Problem.NOT_FOUND, r.getProblem());
    }

    @Test
    void undefinedXIsNotFound() throws Exception {
        LookupResult r = find(curve("y=\\sqrt{x}"), -4, NO);
        assertEquals(LookupResult.Problem.NOT_FOUND, r.getProblem());
    }

    @Test
    void boxesThatDontMakeSense() throws Exception {
        assertEquals(LookupResult.Problem.NOTHING_GIVEN, find(curve("y=x"), NO, NO).getProblem());
        assertEquals(LookupResult.Problem.NOTHING_TO_FIND, find(curve("y=x"), 1, 1).getProblem());
        LookupResult bad = curve("y=x").accept(new PlotLookup(LookupQuery.read(List.of("two", ""))));
        assertEquals(LookupResult.Problem.BAD_NUMBER, bad.getProblem());
    }

    @Test
    void typedNumbersCanBeLittleExpressions() {
        LookupQuery q = LookupQuery.read(List.of("pi/2", "−1.5", "\\sqrt{2}", " ", "x+1", "π"));
        assertEquals(Math.PI / 2, q.get(0), 1e-12);
        assertEquals(-1.5, q.get(1), 1e-12);
        assertEquals(Math.sqrt(2), q.get(2), 1e-12);
        assertFalse(q.given(3));
        assertEquals(4, q.badAxis(), "x+1 isn't a number");
        assertEquals(Math.PI, q.get(5), 1e-12);
    }

    @Test
    void implicitCurveGivesBothYsOfACircle() {
        ImplicitCurvePlot circle = new ImplicitCurvePlot(
                new ImplicitCurve("x^2+y^2=25", (x, y) -> x * x + y * y - 25), WINDOW);
        LookupResult r = find(circle, 3, NO);
        assertEquals(2, r.getPoints().size());
        assertEquals(-4, r.getPoints().get(0).y(), 1e-9);
        assertEquals(4, r.getPoints().get(1).y(), 1e-9);
        LookupResult side = find(circle, NO, 5);
        assertEquals(1, side.getPoints().size(), "the top just touches y = 5");
        assertEquals(0, side.getPoints().get(0).x(), 1e-6);
    }

    @Test
    void surfaceTwoValuesGiveTheThird() throws Exception {
        SurfaceFunction f = parser.parseSurface("z=x^2+y^2");
        SurfacePlot plot = new SurfacePlot(f, new Viewport(-3, 3, -3, 3));
        assertEquals(5, find(plot, 1, 2, NO).getPoints().get(0).z(), 1e-12);
        LookupResult ys = find(plot, 1, NO, 5);
        assertEquals(2, ys.getPoints().size());
        assertEquals(-2, ys.getPoints().get(0).y(), 1e-9);
        assertEquals(2, ys.getPoints().get(1).y(), 1e-9);
        assertEquals(LookupResult.Problem.NOTHING_TO_FIND, find(plot, 1, 2, 3).getProblem());
    }

    @Test
    void surfaceOneValueDrawsTheCutThroughIt() throws Exception {
        SurfacePlot plot = new SurfacePlot(parser.parseSurface("z=x^2+y^2"), new Viewport(-3, 3, -3, 3));
        LookupResult byX = find(plot, 1, NO, NO);
        assertTrue(byX.getPoints().isEmpty());
        assertFalse(byX.getPaths().isEmpty());
        LookupResult.Path cut = byX.getPaths().get(0);
        for (int k = 0; k < cut.xs().length; k++) {
            assertEquals(1, cut.xs()[k], 1e-12);
            assertEquals(1 + cut.ys()[k] * cut.ys()[k], cut.zs()[k], 1e-9);
        }
        LookupResult level = find(plot, NO, NO, 4);
        assertFalse(level.getPaths().isEmpty(), "z = 4 is the circle of radius 2");
        for (LookupResult.Path p : level.getPaths()) {
            for (int k = 0; k < p.xs().length; k++) {
                assertEquals(2, Math.hypot(p.xs()[k], p.ys()[k]), 0.02);
                assertEquals(4, p.zs()[k], 0);
            }
        }
    }

    @Test
    void xyzEquationSolvesForTheMissingOne() throws Exception {
        ImplicitPlot sphere = new ImplicitPlot(new ImplicitSurfaceReader().read("x^2+y^2+z^2=9"),
                new Viewport(-4, 4, -4, 4), -4, 4);
        LookupResult zs = find(sphere, 1, 2, NO);
        assertEquals(2, zs.getPoints().size());
        assertEquals(-2, zs.getPoints().get(0).z(), 1e-9);
        assertEquals(2, zs.getPoints().get(1).z(), 1e-9);
        LookupResult cut = find(sphere, NO, NO, 0);
        assertFalse(cut.getPaths().isEmpty(), "z = 0 cuts the sphere in a circle of radius 3");
        for (LookupResult.Path p : cut.getPaths()) {
            for (int k = 0; k < p.xs().length; k++) {
                assertEquals(3, Math.hypot(p.xs()[k], p.ys()[k]), 0.05);
            }
        }
    }
}
