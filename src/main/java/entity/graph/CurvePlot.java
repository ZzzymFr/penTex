package entity.graph;

import entity.MathFunction;

import java.util.ArrayList;
import java.util.List;

// y = f(x) in a window, one or more functions drawn together; samples + key points worked out once per window
public final class CurvePlot implements Plot {
    static final int SAMPLES = 900;
    static final int KEY_SCAN = 600;

    private final List<MathFunction> functions;
    private final Viewport viewport;
    private final Viewport home;
    private List<Curve> curves;               // lazy, plots are immutable so caching is safe
    private List<List<KeyPoint>> keyPoints;

    public CurvePlot(MathFunction function, Viewport viewport) {
        this(List.of(function), viewport);
    }

    /** Several functions on one graph, in the order they were added (first = the original). */
    public CurvePlot(List<MathFunction> functions, Viewport viewport) {
        this(functions, viewport, viewport);
    }

    private CurvePlot(List<MathFunction> functions, Viewport viewport, Viewport home) {
        if (functions.isEmpty()) {
            throw new IllegalArgumentException("a graph needs at least one function");
        }
        this.functions = List.copyOf(functions);
        this.viewport = viewport;
        this.home = home;
    }

    public MathFunction getFunction() {
        return functions.get(0);
    }

    public List<MathFunction> getFunctions() {
        return functions;
    }

    @Override
    public String getLatex() {
        List<String> names = new ArrayList<>();
        for (MathFunction f : functions) {
            names.add(f.getLatex());
        }
        return Plot.join(names);
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
        return new CurvePlot(functions, next, home);
    }

    // the first function's line
    public Curve getCurve() {
        return getCurves().get(0);
    }

    // one line per function, same order
    public synchronized List<Curve> getCurves() {
        if (curves == null) {
            List<Curve> all = new ArrayList<>();
            for (MathFunction f : functions) {
                all.add(new CurveSampler(SAMPLES).sample(f, viewport));
            }
            curves = List.copyOf(all);
        }
        return curves;
    }

    // every function's key points
    public List<KeyPoint> getKeyPoints() {
        List<KeyPoint> all = new ArrayList<>();
        for (List<KeyPoint> some : keyPointsByFunction()) {
            all.addAll(some);
        }
        return all;
    }

    private synchronized List<List<KeyPoint>> keyPointsByFunction() {
        if (keyPoints == null) {
            List<List<KeyPoint>> all = new ArrayList<>();
            for (MathFunction f : functions) {
                all.add(List.copyOf(new KeyPointFinder(KEY_SCAN).find(f, viewport)));
            }
            keyPoints = List.copyOf(all);
        }
        return keyPoints;
    }

    // onto the function nearest the cursor: near one of its key points -> that point, else straight down
    @Override
    public TracePoint trace(double x, double y, double snapX) {
        if (!Double.isFinite(x)) {
            return null;
        }
        int nearest = -1;
        double nearestFx = Double.NaN;
        for (int i = 0; i < functions.size(); i++) {
            double fx = functions.get(i).evaluate(x);
            if (Double.isFinite(fx) && (nearest < 0
                    || Double.isFinite(y) && Math.abs(fx - y) < Math.abs(nearestFx - y))) {
                nearest = i;
                nearestFx = fx;
            }
        }
        KeyPoint best = null;
        for (KeyPoint k : keyPointsByFunction().get(Math.max(0, nearest))) {
            double d = Math.abs(k.getX() - x);
            if (d <= snapX && (best == null || d < Math.abs(best.getX() - x))) {
                best = k;
            }
        }
        if (best != null) {
            return TracePoint.onCurve(best.getX(), best.getY(), best.getKind());
        }
        return nearest >= 0 ? TracePoint.onCurve(x, nearestFx, null) : null;
    }

    @Override
    public <T> T accept(PlotVisitor<T> visitor) {
        return visitor.visitCurve(this);
    }
}
