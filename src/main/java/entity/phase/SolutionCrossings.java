package entity.phase;

import entity.graph.Curve;
import entity.graph.LookupResult;
import entity.graph.Viewport;

import java.util.ArrayList;
import java.util.List;

/**
 * Where pinned solution curves cross the line horizontal = h (or vertical = v): the traced path finds
 * the crossing, then a few RK4 steps along the equation itself land on the line exactly, so the value
 * read off is the solution's, not the drawing's.
 */
final class SolutionCrossings {
    private static final int SUBSTEPS = 8;
    private static final int MAX_STEPS = 4000;   // straight runs to a typed h on a slope field

    private static final double STEP = 1.0 / 400;   // of the window's width per step, at most

    private SolutionCrossings() {
    }

    /**
     * @param alongH true = crossings of h = value (read off v), false = crossings of v = value (read off h)
     */
    static List<LookupResult.Point> find(DifferentialEquation eq, List<Curve> solutions, Viewport view,
                                         boolean alongH, double value, int max) {
        List<LookupResult.Point> out = new ArrayList<>();
        double apart = 1e-7 * (alongH ? view.height() : view.width());
        for (int layer = 0; layer < solutions.size(); layer++) {
            List<Double> seen = new ArrayList<>();
            for (Curve.Piece piece : solutions.get(layer).getPieces()) {
                double[] hs = piece.getXs();
                double[] vs = piece.getYs();
                double[] s = alongH ? hs : vs;
                double[] o = alongH ? vs : hs;
                for (int k = 0; k + 1 < s.length; k++) {
                    double a = s[k] - value;
                    double b = s[k + 1] - value;
                    boolean hit = a == 0 || (a < 0 && b > 0) || (a > 0 && b < 0) || (k + 2 == s.length && b == 0);
                    if (!hit) {
                        continue;
                    }
                    double other = a == 0 ? o[k] : b == 0 ? o[k + 1] : land(eq, alongH, s, o, k, value);
                    if (Double.isFinite(other) && seen.stream().noneMatch(d -> Math.abs(d - other) <= apart)) {
                        seen.add(other);
                        out.add(alongH ? new LookupResult.Point(value, other, Double.NaN, layer)
                                : new LookupResult.Point(other, value, Double.NaN, layer));
                        if (out.size() > max) {
                            return out;
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * Slope field (h' = 1, so each solution is v(h)): runs every pinned solution from its start straight
     * to h with RK4, so any h works, in view or not. A solution that blows up before h isn't there.
     */
    static List<LookupResult.Point> atH(DifferentialEquation eq, List<double[]> starts, Viewport view, double h) {
        List<LookupResult.Point> out = new ArrayList<>();
        double apart = 1e-9 * view.height();
        List<Double> seen = new ArrayList<>();
        for (int layer = 0; layer < starts.size(); layer++) {
            double[] s = starts.get(layer);
            double span = h - s[0];
            int steps = (int) Math.min(MAX_STEPS, Math.max(SUBSTEPS, Math.ceil(Math.abs(span) / (STEP * view.width()))));
            double step = span / steps;
            double at = s[0];
            double v = s[1];
            for (int i = 0; i < steps && Double.isFinite(v); i++) {
                double k1 = eq.dv(at, v);
                double k2 = eq.dv(at + step / 2, v + step / 2 * k1);
                double k3 = eq.dv(at + step / 2, v + step / 2 * k2);
                double k4 = eq.dv(at + step, v + step * k3);
                v += step * (k1 + 2 * k2 + 2 * k3 + k4) / 6;
                at = s[0] + span * (i + 1) / steps;
            }
            double found = v;
            if (Double.isFinite(found) && Math.abs(found) < 1e12
                    && seen.stream().noneMatch(d -> Math.abs(d - found) <= apart)) {
                seen.add(found);
                out.add(new LookupResult.Point(h, found, Double.NaN, layer));
            }
        }
        return out;
    }

    // from point k, follow d(other)/d(s) = field ratio onto s = value; the straight line between the
    // traced points if the field won't say (vertical there)
    private static double land(DifferentialEquation eq, boolean alongH, double[] s, double[] o, int k, double value) {
        double linear = o[k] + (o[k + 1] - o[k]) * (value - s[k]) / (s[k + 1] - s[k]);
        double step = (value - s[k]) / SUBSTEPS;
        double at = s[k];
        double other = o[k];
        for (int i = 0; i < SUBSTEPS; i++) {
            double k1 = slope(eq, alongH, at, other);
            double k2 = slope(eq, alongH, at + step / 2, other + step / 2 * k1);
            double k3 = slope(eq, alongH, at + step / 2, other + step / 2 * k2);
            double k4 = slope(eq, alongH, at + step, other + step * k3);
            other += step * (k1 + 2 * k2 + 2 * k3 + k4) / 6;
            at += step;
        }
        double lo = Math.min(o[k], o[k + 1]);
        double hi = Math.max(o[k], o[k + 1]);
        double slack = hi - lo + Math.abs(s[k + 1] - s[k]);
        // the field steered somewhere the traced path didn't go: trust the path
        return Double.isFinite(other) && other >= lo - slack && other <= hi + slack ? other : linear;
    }

    private static double slope(DifferentialEquation eq, boolean alongH, double s, double o) {
        double h = alongH ? s : o;
        double v = alongH ? o : s;
        double dh = eq.dh(h, v);
        double dv = eq.dv(h, v);
        return alongH ? dv / dh : dh / dv;
    }
}
