package entity.graph;

import entity.ImplicitCurve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// marching squares: where F(x, y) changes sign on a grid over the window, joined up into line pieces
final class ContourTracer {
    private final int cells;

    ContourTracer(int cells) {
        if (cells < 1) {
            throw new IllegalArgumentException("need at least one cell");
        }
        this.cells = cells;
    }

    Curve trace(ImplicitCurve f, Viewport v) {
        int n = cells;
        double dx = v.width() / n;
        double dy = v.height() / n;
        double[][] values = new double[n + 1][n + 1];
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                values[i][j] = f.evaluate(v.getXMin() + i * dx, v.getYMin() + j * dy);
            }
        }
        Map<Long, double[]> points = new HashMap<>();
        Map<Long, List<Long>> links = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                cell(f, v, values, i, j, dx, dy, points, links);
            }
        }
        return new Curve(join(points, links));
    }

    // one square: 2 crossings -> one segment, 4 -> a saddle, the middle value says which way they pair up
    private void cell(ImplicitCurve f, Viewport v, double[][] values, int i, int j, double dx, double dy,
                      Map<Long, double[]> points, Map<Long, List<Long>> links) {
        long bottom = horizontal(i, j);
        long right = vertical(i + 1, j);
        long top = horizontal(i, j + 1);
        long left = vertical(i, j);
        boolean b = crossing(f, v, values, bottom, i, j, i + 1, j, dx, dy, points);
        boolean r = crossing(f, v, values, right, i + 1, j, i + 1, j + 1, dx, dy, points);
        boolean t = crossing(f, v, values, top, i, j + 1, i + 1, j + 1, dx, dy, points);
        boolean l = crossing(f, v, values, left, i, j, i, j + 1, dx, dy, points);
        List<Long> hit = new ArrayList<>(4);
        if (b) {
            hit.add(bottom);
        }
        if (r) {
            hit.add(right);
        }
        if (t) {
            hit.add(top);
        }
        if (l) {
            hit.add(left);
        }
        if (hit.size() == 2) {
            link(links, hit.get(0), hit.get(1));
        } else if (hit.size() == 4) {
            double centre = f.evaluate(v.getXMin() + (i + 0.5) * dx, v.getYMin() + (j + 0.5) * dy);
            if ((centre < 0) == (values[i][j] < 0)) {   // bottom-left joins top-right, cut off the other two
                link(links, bottom, right);
                link(links, top, left);
            } else {
                link(links, left, bottom);
                link(links, right, top);
            }
        }
    }

    // sign change between two grid points -> where it crosses, by straight-line interpolation;
    // a sign change through a pole (1/x) isn't a crossing, F there is huge not ~0
    private boolean crossing(ImplicitCurve f, Viewport v, double[][] values, long id, int i0, int j0, int i1, int j1,
                             double dx, double dy, Map<Long, double[]> points) {
        if (points.containsKey(id)) {
            return true;
        }
        double a = values[i0][j0];
        double c = values[i1][j1];
        if (!Double.isFinite(a) || !Double.isFinite(c) || (a < 0) == (c < 0)) {
            return false;
        }
        double s = a / (a - c);
        double x = v.getXMin() + (i0 + s * (i1 - i0)) * dx;
        double y = v.getYMin() + (j0 + s * (j1 - j0)) * dy;
        double there = f.evaluate(x, y);
        if (!Double.isFinite(there) || Math.abs(there) > Math.max(Math.abs(a), Math.abs(c))) {
            return false;
        }
        points.put(id, new double[]{x, y});
        return true;
    }

    private long horizontal(int i, int j) {
        return 2L * ((long) j * (cells + 1) + i);
    }

    private long vertical(int i, int j) {
        return 2L * ((long) j * (cells + 1) + i) + 1;
    }

    private static void link(Map<Long, List<Long>> links, long a, long b) {
        links.computeIfAbsent(a, k -> new ArrayList<>(2)).add(b);
        links.computeIfAbsent(b, k -> new ArrayList<>(2)).add(a);
    }

    // every crossing sits on at most two segments, so walking neighbours gives open runs then closed loops
    private static List<Curve.Piece> join(Map<Long, double[]> points, Map<Long, List<Long>> links) {
        List<Curve.Piece> pieces = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Map.Entry<Long, List<Long>> e : links.entrySet()) {
            if (e.getValue().size() == 1 && !used.contains(e.getKey())) {
                pieces.add(walk(e.getKey(), points, links, used));
            }
        }
        for (Long start : links.keySet()) {
            if (!used.contains(start)) {
                pieces.add(walk(start, points, links, used));
            }
        }
        return pieces;
    }

    private static Curve.Piece walk(long start, Map<Long, double[]> points, Map<Long, List<Long>> links,
                                    Set<Long> used) {
        List<double[]> run = new ArrayList<>();
        Long at = start;
        while (at != null) {
            used.add(at);
            run.add(points.get(at));
            Long next = null;
            for (Long n : links.get(at)) {
                if (!used.contains(n)) {
                    next = n;
                    break;
                }
            }
            if (next == null && run.size() > 2 && links.get(at).contains(start)) {
                run.add(points.get(start));   // closed loop, back to where it began
            }
            at = next;
        }
        double[] xs = new double[run.size()];
        double[] ys = new double[run.size()];
        for (int k = 0; k < run.size(); k++) {
            xs[k] = run.get(k)[0];
            ys[k] = run.get(k)[1];
        }
        return new Curve.Piece(xs, ys);
    }
}
