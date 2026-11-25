package entity.graph;

import entity.ImplicitCurve;
import entity.ImplicitSurface;
import entity.MathFunction;
import entity.SurfaceFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * Works out the blank boxes of a {@link LookupQuery} on a graph.
 *
 * <ul>
 *   <li>2D: x gives y on every function (straight from the formula); y gives every x in view.</li>
 *   <li>3D: two values give the third (z = f(x, y) straight away, anything else searched in the box);
 *       one value gives the whole cut through the graph at that value, as paths.</li>
 * </ul>
 */
public final class PlotLookup implements PlotVisitor<LookupResult> {
    static final int MAX_POINTS = 40;
    static final int SCAN = 2000;          // samples along the window when searching
    static final int CUT_SAMPLES = 240;    // points along a cut of z = f(x, y)
    static final int CONTOUR_CELLS = 140;  // a side, for level curves and cuts of x y z equations
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    private final LookupQuery query;
    private final List<LookupResult.Point> points = new ArrayList<>();
    private final List<LookupResult.Path> paths = new ArrayList<>();
    private boolean truncated;
    private boolean flat;   // a 2d graph: points have no z

    public PlotLookup(LookupQuery query) {
        this.query = query;
    }

    // ---- 2d

    @Override
    public LookupResult visitCurve(CurvePlot plot) {
        LookupResult problem = check(2);
        if (problem != null) {
            return problem;
        }
        Viewport v = plot.getViewport();
        List<MathFunction> functions = plot.getFunctions();
        for (int layer = 0; layer < functions.size(); layer++) {
            MathFunction f = functions.get(layer);
            if (query.given(X)) {
                double x = query.get(X);
                point(x, f.evaluate(x), Double.NaN, layer);
            } else {
                double y = query.get(Y);
                for (double x : RootScan.roots(t -> f.evaluate(t) - y, v.getXMin(), v.getXMax(), SCAN, MAX_POINTS)) {
                    point(x, y, Double.NaN, layer);
                }
            }
        }
        return done(!query.given(X));
    }

    @Override
    public LookupResult visitImplicitCurve(ImplicitCurvePlot plot) {
        LookupResult problem = check(2);
        if (problem != null) {
            return problem;
        }
        Viewport v = plot.getViewport();
        ImplicitCurve f = plot.getFunction();
        if (query.given(X)) {
            double x = query.get(X);
            for (double y : RootScan.roots(t -> f.evaluate(x, t), v.getYMin(), v.getYMax(), SCAN, MAX_POINTS)) {
                point(x, y, Double.NaN, 0);
            }
        } else {
            double y = query.get(Y);
            for (double x : RootScan.roots(t -> f.evaluate(t, y), v.getXMin(), v.getXMax(), SCAN, MAX_POINTS)) {
                point(x, y, Double.NaN, 0);
            }
        }
        return done(true);
    }

    // ---- 3d

    @Override
    public LookupResult visitSurface(SurfacePlot plot) {
        LookupResult problem = check(3);
        if (problem != null) {
            return problem;
        }
        Viewport v = plot.getViewport();
        double[] zWindow = plot.zWindow();
        List<SurfaceFunction> functions = plot.getFunctions();
        double x = query.get(X);
        double y = query.get(Y);
        double z = query.get(Z);
        for (int layer = 0; layer < functions.size(); layer++) {
            SurfaceFunction f = functions.get(layer);
            if (query.given(X) && query.given(Y)) {
                point(x, y, f.evaluate(x, y), layer);
            } else if (query.given(X) && query.given(Z)) {
                for (double t : RootScan.roots(s -> f.evaluate(x, s) - z, v.getYMin(), v.getYMax(), SCAN, MAX_POINTS)) {
                    point(x, t, z, layer);
                }
            } else if (query.given(Y) && query.given(Z)) {
                for (double t : RootScan.roots(s -> f.evaluate(s, y) - z, v.getXMin(), v.getXMax(), SCAN, MAX_POINTS)) {
                    point(t, y, z, layer);
                }
            } else if (query.given(X)) {
                cut(layer, v.getYMin(), v.getYMax(), zWindow, t -> new double[]{x, t, f.evaluate(x, t)});
            } else if (query.given(Y)) {
                cut(layer, v.getXMin(), v.getXMax(), zWindow, t -> new double[]{t, y, f.evaluate(t, y)});
            } else {
                level(layer, v, (a, b) -> f.evaluate(a, b) - z, (a, b) -> new double[]{a, b, z});
            }
        }
        return done(!(query.given(X) && query.given(Y)));
    }

    @Override
    public LookupResult visitImplicit(ImplicitPlot plot) {
        LookupResult problem = check(3);
        if (problem != null) {
            return problem;
        }
        ImplicitSurface f = plot.getSurface();
        Viewport v = plot.getViewport();
        double[][] box = {{v.getXMin(), v.getXMax()}, {v.getYMin(), v.getYMax()}, {plot.getZMin(), plot.getZMax()}};
        double x = query.get(X);
        double y = query.get(Y);
        double z = query.get(Z);
        if (query.givenCount(3) == 2) {
            int missing = query.given(X) ? (query.given(Y) ? Z : Y) : X;
            DoubleUnaryOperator g = t -> f.evaluate(missing == X ? t : x, missing == Y ? t : y, missing == Z ? t : z);
            for (double t : RootScan.roots(g, box[missing][0], box[missing][1], SCAN, MAX_POINTS)) {
                point(missing == X ? t : x, missing == Y ? t : y, missing == Z ? t : z, 0);
            }
        } else if (query.given(X)) {
            level(0, new Viewport(box[Y][0], box[Y][1], box[Z][0], box[Z][1]),
                    (a, b) -> f.evaluate(x, a, b), (a, b) -> new double[]{x, a, b});
        } else if (query.given(Y)) {
            level(0, new Viewport(box[X][0], box[X][1], box[Z][0], box[Z][1]),
                    (a, b) -> f.evaluate(a, y, b), (a, b) -> new double[]{a, y, b});
        } else {
            level(0, v, (a, b) -> f.evaluate(a, b, z), (a, b) -> new double[]{a, b, z});
        }
        return done(true);
    }

    // ---- helpers

    // null when the boxes make sense for a graph with this many axes
    private LookupResult check(int axes) {
        flat = axes == 2;
        int given = query.givenCount(axes);
        if (query.badAxis() >= 0 && query.badAxis() < axes) {
            return LookupResult.problem(query, LookupResult.Problem.BAD_NUMBER);
        }
        if (given == 0) {
            return LookupResult.problem(query, LookupResult.Problem.NOTHING_GIVEN);
        }
        if (given == axes) {
            return LookupResult.problem(query, LookupResult.Problem.NOTHING_TO_FIND);
        }
        return null;
    }

    private void point(double x, double y, double z, int layer) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !(flat || Double.isFinite(z))) {
            return;   // undefined there
        }
        if (points.size() >= MAX_POINTS) {
            truncated = true;
            return;
        }
        points.add(new LookupResult.Point(x, y, flat ? Double.NaN : z, layer));
    }

    private interface Along {
        double[] at(double t);
    }

    // z = f along one line of the floor, split where it's undefined or cut off by the box
    private void cut(int layer, double lo, double hi, double[] zWindow, Along along) {
        List<double[]> run = new ArrayList<>();
        for (int i = 0; i <= CUT_SAMPLES; i++) {
            double[] p = along.at(lo + (hi - lo) * i / CUT_SAMPLES);
            boolean inside = Double.isFinite(p[2]) && p[2] >= zWindow[0] && p[2] <= zWindow[1];
            if (inside) {
                run.add(p);
            } else {
                flush(run, layer);
            }
        }
        flush(run, layer);
    }

    private interface Place {
        double[] at(double a, double b);
    }

    // where g(a, b) = 0 over a window of two of the axes, each point placed back in x y z
    private void level(int layer, Viewport window, DoubleBinaryOperator g, Place place) {
        Curve curve = new ContourTracer(CONTOUR_CELLS).trace(new ImplicitCurve("", g), window);
        for (Curve.Piece piece : curve.getPieces()) {
            double[] as = piece.getXs();
            double[] bs = piece.getYs();
            List<double[]> run = new ArrayList<>();
            for (int k = 0; k < as.length; k++) {
                run.add(place.at(as[k], bs[k]));
            }
            flush(run, layer);
        }
    }

    private void flush(List<double[]> run, int layer) {
        if (!run.isEmpty()) {
            double[] xs = new double[run.size()];
            double[] ys = new double[run.size()];
            double[] zs = new double[run.size()];
            for (int k = 0; k < run.size(); k++) {
                xs[k] = run.get(k)[0];
                ys[k] = run.get(k)[1];
                zs[k] = run.get(k)[2];
            }
            paths.add(new LookupResult.Path(xs, ys, zs, layer));
            run.clear();
        }
    }

    private LookupResult done(boolean inView) {
        return LookupResult.found(query, points, paths, inView, truncated);
    }
}
