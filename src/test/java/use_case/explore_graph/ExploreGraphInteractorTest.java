package use_case.explore_graph;

import data_access.InMemoryGraphSessionDataAccessObject;
import entity.LatexFunctionParser;
import entity.graph.KeyPoint;
import org.junit.jupiter.api.Test;
import use_case.graph_function.CurveOutputData;
import use_case.graph_function.GraphFunctionInputData;
import use_case.graph_function.GraphFunctionInteractor;
import use_case.graph_function.GraphFunctionOutputBoundary;
import use_case.graph_function.SurfaceOutputData;
import use_case.graph_function.TraceOutputData;

import static org.junit.jupiter.api.Assertions.*;

class ExploreGraphInteractorTest {
    private CurveOutputData curve;
    private SurfaceOutputData surface;
    private TraceOutputData trace;
    private use_case.lookup.LookupOutputData lookup;
    private boolean lookedUp;
    private boolean fresh;

    private final GraphFunctionOutputBoundary presenter = new GraphFunctionOutputBoundary() {
        @Override
        public void prepareCurveView(CurveOutputData data, boolean isFresh) {
            curve = data;
            fresh = isFresh;
        }

        @Override
        public void prepareSurfaceView(SurfaceOutputData data, boolean isFresh) {
            surface = data;
            fresh = isFresh;
        }

        @Override
        public void prepareTraceView(TraceOutputData data) {
            trace = data;
        }

        @Override
        public void prepareFailView(String errorMessage) {
            fail(errorMessage);
        }

        @Override
        public void prepareLookupView(use_case.lookup.LookupOutputData data) {
            lookup = data;
            lookedUp = true;
        }
    };

    private final InMemoryGraphSessionDataAccessObject session = new InMemoryGraphSessionDataAccessObject();
    private final GraphFunctionInteractor graph = new GraphFunctionInteractor(new LatexFunctionParser(), session, presenter);
    private final ExploreGraphInteractor explore = new ExploreGraphInteractor(session, presenter);

    @Test
    void xyzSurfaceZoomsItsZRangeAndTracesTheTop() {
        graph.execute(new GraphFunctionInputData("x^2+y^2+z^2=1"));
        double zMax = surface.getZMax();
        explore.zoom(0, 0, 0.5, 0.5);
        assertFalse(fresh);
        assertEquals(zMax / 2, surface.getZMax(), 1e-9);
        assertNotNull(surface.getMesh());
        explore.reset();
        explore.trace(0.6, 0, 0);
        assertEquals(0.8, trace.getZ(), 1e-9);
    }

    @Test
    void nothingGraphedMeansNothingHappens() {
        explore.pan(1, 1);
        explore.zoom(0, 0, 2, 2);
        explore.reset();
        explore.trace(0, 0, 0.1);
        assertNull(curve);
        assertNull(trace);
    }

    @Test
    void panMovesTheWindowAndResamples() {
        graph.execute(new GraphFunctionInputData("y=x", -10, 10, -10, 10));
        explore.pan(5, -2);
        assertFalse(fresh, "a pan is not a new graph");
        assertEquals(-5, curve.getXMin(), 1e-9);
        assertEquals(15, curve.getXMax(), 1e-9);
        assertEquals(-12, curve.getYMin(), 1e-9);
        double[] xs = curve.getPieces().get(0)[0];
        assertTrue(xs[xs.length - 1] >= 15, "sampled over the new window");
    }

    @Test
    void zoomThenResetGoesHome() {
        graph.execute(new GraphFunctionInputData("y=x", -10, 10, -10, 10));
        explore.zoom(0, 0, 0.5, 0.5);
        assertEquals(10, curve.getXMax() - curve.getXMin(), 1e-9);
        explore.reset();
        assertEquals(-10, curve.getXMin(), 1e-9);
        assertEquals(10, curve.getXMax(), 1e-9);
    }

    @Test
    void traceReadsTheRealValueAndSnapsToKeyPoints() {
        graph.execute(new GraphFunctionInputData("y=x^2-4", -5, 5));
        explore.trace(1.25, 123, 0.01);
        assertEquals(1.25 * 1.25 - 4, trace.getY(), 1e-12);
        assertFalse(trace.isSurface());
        explore.trace(2.05, 0, 0.1);
        assertEquals(KeyPoint.Kind.ROOT, trace.getKind());
        assertEquals(2, trace.getX(), 1e-9);
        explore.endTrace();
        assertNull(trace);
    }

    @Test
    void surfaceTraceGivesZAndZoomResamplesTheGrid() {
        graph.execute(new GraphFunctionInputData("z=xy", -4, 4));
        explore.trace(2, 3, 0);
        assertEquals(6, trace.getZ(), 1e-12);
        assertTrue(trace.isSurface());
        explore.zoom(0, 0, 0.5, 0.5);
        assertEquals(-2, surface.getXMin(), 1e-9);
        assertEquals(4, surface.getZ()[0][0], 1e-9, "corner is now (-2, -2)");
    }

    @Test
    void lookupWorksOutTheBlankBoxOnTheGraphThatsUp() {
        graph.execute(new GraphFunctionInputData("y=x^2", -5, 5));
        explore.lookup(java.util.List.of("3", ""));
        assertEquals(use_case.lookup.LookupOutputData.Problem.NONE, lookup.getProblem());
        assertEquals(1, lookup.getPoints().size());
        assertEquals(9, lookup.getPoints().get(0).y(), 1e-12);

        explore.lookup(java.util.List.of("", "4"));
        assertEquals(2, lookup.getPoints().size(), "both x = -2 and x = 2");
        assertEquals(-2, lookup.getPoints().get(0).x(), 1e-9);
        assertEquals(2, lookup.getPoints().get(1).x(), 1e-9);

        explore.lookup(java.util.List.of("pi/2", ""));
        assertEquals(Math.PI * Math.PI / 4, lookup.getPoints().get(0).y(), 1e-9, "pi/2 reads as a number");

        explore.clearLookup();
        assertTrue(lookedUp);
        assertNull(lookup);
    }

    @Test
    void lookupOnASurfaceGivesZOrACut() {
        graph.execute(new GraphFunctionInputData("z=x^2+y^2", -3, 3));
        explore.lookup(java.util.List.of("1", "2", ""));
        assertEquals(5, lookup.getPoints().get(0).z(), 1e-12);

        explore.lookup(java.util.List.of("1", "", ""));
        assertTrue(lookup.getPoints().isEmpty());
        assertFalse(lookup.getPaths().isEmpty(), "x = 1 alone is a cut through the surface");
        for (use_case.lookup.LookupOutputData.Path p : lookup.getPaths()) {
            for (int k = 0; k < p.xs().length; k++) {
                assertEquals(1, p.xs()[k], 1e-12);
                assertEquals(1 + p.ys()[k] * p.ys()[k], p.zs()[k], 1e-9);
            }
        }
    }

    @Test
    void lookupSaysWhatsWrongWithTheBoxes() {
        graph.execute(new GraphFunctionInputData("y=x", -5, 5));
        explore.lookup(java.util.List.of("abc", ""));
        assertEquals(use_case.lookup.LookupOutputData.Problem.BAD_NUMBER, lookup.getProblem());
        assertEquals(0, lookup.getBadAxis());
        explore.lookup(java.util.List.of("1", "2"));
        assertEquals(use_case.lookup.LookupOutputData.Problem.NOTHING_TO_FIND, lookup.getProblem());
        explore.lookup(java.util.List.of("", ""));
        assertEquals(use_case.lookup.LookupOutputData.Problem.NOTHING_GIVEN, lookup.getProblem());
    }
}
