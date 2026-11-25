package entity.phase;

import entity.graph.LookupQuery;
import entity.graph.LookupResult;
import entity.graph.Viewport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// reading values off pinned solutions of an ode
class PhaseLookupTest {
    private static final double NO = Double.NaN;

    private static PhasePlot plot(String latex) throws Exception {
        return new PhasePlot(new DifferentialEquationParser().parse(latex), new Viewport(-3, 3, -1, 12));
    }

    @Test
    void nothingPinnedNothingToReadFrom() throws Exception {
        LookupResult r = plot("y'=y").lookup(LookupQuery.of(1, NO));
        assertEquals(LookupResult.Problem.NO_SOLUTIONS, r.getProblem());
    }

    @Test
    void xGivesYOnTheSolutionThroughTheTypedStart() throws Exception {
        PhasePlot p = plot("y'=y").withSolutionThrough(0, 1);   // y = e^x
        LookupResult r = p.lookup(LookupQuery.of(1, NO));
        assertEquals(1, r.getPoints().size());
        assertEquals(Math.E, r.getPoints().get(0).y(), 1e-6);
        LookupResult back = p.lookup(LookupQuery.of(NO, Math.E * Math.E));
        assertEquals(2, back.getPoints().get(0).x(), 1e-5);
    }

    @Test
    void onASlopeFieldAnyXWorksNotJustWhatsInView() throws Exception {
        PhasePlot p = plot("y'=y").withSolutionThrough(0, 1);   // window is x in [-3, 3]
        LookupResult far = p.lookup(LookupQuery.of(5, NO));
        assertEquals(Math.exp(5), far.getPoints().get(0).y(), 1e-6 * Math.exp(5));
        assertFalse(far.isInView());
        LookupResult left = p.lookup(LookupQuery.of(-6, NO));
        assertEquals(Math.exp(-6), left.getPoints().get(0).y(), 1e-9);
    }

    @Test
    void aSolutionThatBlowsUpFirstIsntThere() throws Exception {
        PhasePlot p = plot("y'=y^2").withSolutionThrough(0, 1);   // y = 1/(1-x), gone at x = 1
        assertEquals(LookupResult.Problem.NOT_FOUND, p.lookup(LookupQuery.of(2, NO)).getProblem());
        assertEquals(2, p.lookup(LookupQuery.of(0.5, NO)).getPoints().get(0).y(), 1e-6);
    }

    @Test
    void bothGivenIsThePointItself() throws Exception {
        PhasePlot p = plot("y'=y").withSolutionThrough(0, 1);
        assertSame(p, p.withSolutionThrough(0, 1), "already pinned");
        LookupResult r = p.lookup(LookupQuery.of(0, 1));
        assertEquals(1, r.getPoints().size());
        assertEquals(1, r.getPoints().get(0).y(), 0);
    }

    @Test
    void anOrbitCrossesALineTwice() throws Exception {
        PhasePlot p = new PhasePlot(new DifferentialEquationParser().parse("x'=y\\\\y'=-x"),
                new Viewport(-5, 5, -5, 5)).withSolutionThrough(3, 0);   // circle of radius 3
        LookupResult r = p.lookup(LookupQuery.of(0, NO));
        assertEquals(2, r.getPoints().size(), r.getPoints().toString());
        for (LookupResult.Point pt : r.getPoints()) {
            assertEquals(3, Math.abs(pt.y()), 1e-3);
        }
    }
}
