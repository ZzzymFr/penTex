package entity.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Every t in [lo, hi] where g(t) = 0: sample, bisect each sign change (skipping poles like 1/t, where g
 * jumps instead of crossing), and polish dips that just touch 0 without crossing (t^2 at 0).
 */
final class RootScan {
    private static final int BISECT_ROUNDS = 100;
    private static final int GOLDEN_ROUNDS = 90;
    private static final double TOUCH_TOLERANCE = 1e-7;   // of the neighbours' size: a dip this low touches 0
    private static final double INV_PHI = (Math.sqrt(5) - 1) / 2;

    private RootScan() {
    }

    /** The roots in order, at most {@code max} of them (one more means there were too many). */
    static List<Double> roots(DoubleUnaryOperator g, double lo, double hi, int samples, int max) {
        int n = Math.max(2, samples);
        double[] t = new double[n + 1];
        double[] v = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            t[i] = lo + (hi - lo) * i / n;
            v[i] = g.applyAsDouble(t[i]);
        }
        List<Double> found = new ArrayList<>();
        for (int i = 0; i <= n && found.size() <= max; i++) {
            if (v[i] == 0) {
                found.add(t[i]);
            } else if (i < n && Double.isFinite(v[i]) && Double.isFinite(v[i + 1]) && v[i + 1] != 0
                    && (v[i] < 0) != (v[i + 1] < 0)) {
                double r = bisect(g, t[i], t[i + 1], v[i]);
                double gr = Math.abs(g.applyAsDouble(r));
                if (gr <= Math.max(Math.abs(v[i]), Math.abs(v[i + 1]))) {   // else it jumped over a pole
                    found.add(r);
                }
            } else if (i > 0 && i < n && touches(v[i - 1], v[i], v[i + 1])) {
                double r = lowest(g, t[i - 1], t[i + 1]);
                double tolerance = TOUCH_TOLERANCE * Math.max(Math.abs(v[i - 1]), Math.abs(v[i + 1]));
                if (Math.abs(g.applyAsDouble(r)) <= tolerance) {
                    found.add(r);
                }
            }
        }
        found.sort(Double::compare);
        List<Double> out = new ArrayList<>();
        double apart = (hi - lo) * 1e-9;
        for (double r : found) {
            if (out.isEmpty() || r - out.get(out.size() - 1) > apart) {
                out.add(r);
            }
        }
        return out.size() > max + 1 ? out.subList(0, max + 1) : out;
    }

    // a dip towards 0 that stays on one side
    private static boolean touches(double before, double here, double after) {
        return Double.isFinite(before) && Double.isFinite(here) && Double.isFinite(after)
                && (before < 0) == (here < 0) && (here < 0) == (after < 0)
                && Math.abs(here) < Math.abs(before) && Math.abs(here) <= Math.abs(after);
    }

    private static double bisect(DoubleUnaryOperator g, double a, double b, double ga) {
        for (int r = 0; r < BISECT_ROUNDS; r++) {
            double mid = (a + b) / 2;
            if (mid <= a || mid >= b) {
                break;
            }
            double gm = g.applyAsDouble(mid);
            if (gm == 0) {
                return mid;
            }
            if (!Double.isFinite(gm) || (gm < 0) == (ga < 0)) {
                a = mid;
                ga = Double.isFinite(gm) ? gm : ga;
            } else {
                b = mid;
            }
        }
        return (a + b) / 2;
    }

    // golden section on |g|
    private static double lowest(DoubleUnaryOperator g, double a, double b) {
        double c = b - INV_PHI * (b - a);
        double d = a + INV_PHI * (b - a);
        double gc = size(g, c);
        double gd = size(g, d);
        for (int r = 0; r < GOLDEN_ROUNDS; r++) {
            if (gc < gd) {
                b = d;
                d = c;
                gd = gc;
                c = b - INV_PHI * (b - a);
                gc = size(g, c);
            } else {
                a = c;
                c = d;
                gc = gd;
                d = a + INV_PHI * (b - a);
                gd = size(g, d);
            }
        }
        return (a + b) / 2;
    }

    private static double size(DoubleUnaryOperator g, double t) {
        double v = Math.abs(g.applyAsDouble(t));
        return Double.isFinite(v) ? v : Double.POSITIVE_INFINITY;
    }
}
