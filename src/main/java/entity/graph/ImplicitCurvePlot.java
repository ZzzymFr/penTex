package entity.graph;

import entity.ImplicitCurve;

// F(x, y) = 0 in a window, e.g. x^2 + y^2 = 1: drawn on the same 2d card as y = f(x), but traced by contour
public final class ImplicitCurvePlot implements Plot {
    static final int CELLS = 240;              // a side, ~58k evaluations a window
    private static final double TRACE_REACH = 0.08;   // fraction of the window the cursor may be off the line

    private final ImplicitCurve function;
    private final Viewport viewport;
    private final Viewport home;
    private Curve curve;                       // lazy, plots are immutable so caching is safe

    public ImplicitCurvePlot(ImplicitCurve function, Viewport viewport) {
        this(function, viewport, viewport);
    }

    private ImplicitCurvePlot(ImplicitCurve function, Viewport viewport, Viewport home) {
        this.function = function;
        this.viewport = viewport;
        this.home = home;
    }

    public ImplicitCurve getFunction() {
        return function;
    }

    @Override
    public String getLatex() {
        return function.getLatex();
    }

    @Override
    public Viewport getViewport() {
        return viewport;
    }

    @Override
    public Viewport getHome() {
        return home;
    }

    @Override
    public Plot lookingAt(Viewport next) {
        return new ImplicitCurvePlot(function, next, home);
    }

    public synchronized Curve getCurve() {
        if (curve == null) {
            curve = new ContourTracer(CELLS).trace(function, viewport);
        }
        return curve;
    }

    // the line can double back on itself, so snap to the nearest point on it (in window fractions), not straight down
    @Override
    public TracePoint trace(double x, double y, double snapX) {
        if (!Double.isFinite(x)) {
            return null;
        }
        boolean useY = Double.isFinite(y);
        double best = Double.POSITIVE_INFINITY;
        double bestX = Double.NaN;
        double bestY = Double.NaN;
        for (Curve.Piece piece : getCurve().getPieces()) {
            double[] xs = piece.getXs();
            double[] ys = piece.getYs();
            for (int k = 0; k < xs.length; k++) {
                double ex = (xs[k] - x) / viewport.width();
                double ey = useY ? (ys[k] - y) / viewport.height() : 0;
                double d = ex * ex + ey * ey;
                if (d < best) {
                    best = d;
                    bestX = xs[k];
                    bestY = ys[k];
                }
            }
        }
        if (best > TRACE_REACH * TRACE_REACH) {
            return null;
        }
        return TracePoint.onCurve(bestX, bestY, null);
    }

    @Override
    public <T> T accept(PlotVisitor<T> visitor) {
        return visitor.visitImplicitCurve(this);
    }
}
