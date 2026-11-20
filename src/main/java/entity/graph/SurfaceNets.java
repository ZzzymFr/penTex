package entity.graph;

import entity.ImplicitSurface;

import java.util.ArrayList;
import java.util.List;

// surface nets: a point per cell the surface goes through, a quad round every grid edge where F flips sign
public final class SurfaceNets {
    private final ImplicitSurface f;
    private final int n;
    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private final double[][][] value;
    // where along each edge F hits 0 (0..1), NaN = no crossing; ex[i][j][k] runs (i,j,k) -> (i+1,j,k)
    private final double[][][] ex;
    private final double[][][] ey;
    private final double[][][] ez;
    private final double[][][][] point;   // the cell's point, null = surface misses the cell

    private SurfaceNets(ImplicitSurface f, Viewport floor, double zMin, double zMax, int n) {
        this.f = f;
        this.n = n;
        xs = axis(floor.getXMin(), floor.getXMax(), n);
        ys = axis(floor.getYMin(), floor.getYMax(), n);
        zs = axis(zMin, zMax, n);
        value = new double[n + 1][n + 1][n + 1];
        ex = new double[n][n + 1][n + 1];
        ey = new double[n + 1][n][n + 1];
        ez = new double[n + 1][n + 1][n];
        point = new double[n][n][n][];
    }

    // n cells a side over the floor x the z range
    public static SurfaceMesh mesh(ImplicitSurface f, Viewport floor, double zMin, double zMax, int n) {
        SurfaceNets nets = new SurfaceNets(f, floor, zMin, zMax, n);
        nets.sample();
        nets.crossings();
        nets.points();
        return new SurfaceMesh(nets.quads());
    }

    private static double[] axis(double lo, double hi, int n) {
        double[] a = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            a[i] = lo + (hi - lo) * i / n;
        }
        return a;
    }

    private void sample() {
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                for (int k = 0; k <= n; k++) {
                    value[i][j][k] = f.evaluate(xs[i], ys[j], zs[k]);
                }
            }
        }
    }

    private void crossings() {
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                for (int k = 0; k <= n; k++) {
                    if (i < n) {
                        ex[i][j][k] = cross(value[i][j][k], value[i + 1][j][k],
                                (xs[i] + xs[i + 1]) / 2, ys[j], zs[k]);
                    }
                    if (j < n) {
                        ey[i][j][k] = cross(value[i][j][k], value[i][j + 1][k],
                                xs[i], (ys[j] + ys[j + 1]) / 2, zs[k]);
                    }
                    if (k < n) {
                        ez[i][j][k] = cross(value[i][j][k], value[i][j][k + 1],
                                xs[i], ys[j], (zs[k] + zs[k + 1]) / 2);
                    }
                }
            }
        }
    }

    // a sign flip that blows up halfway is a pole (1/x), not the surface
    private double cross(double a, double b, double mx, double my, double mz) {
        if (!Double.isFinite(a) || !Double.isFinite(b) || (a < 0) == (b < 0)) {
            return Double.NaN;
        }
        double mid = f.evaluate(mx, my, mz);
        if (!Double.isFinite(mid) || Math.abs(mid) > Math.max(Math.abs(a), Math.abs(b))) {
            return Double.NaN;
        }
        return a / (a - b);
    }

    // cell point = average of where its 12 edges cross
    private void points() {
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    double[] sum = new double[3];
                    int count = 0;
                    for (int a = 0; a <= 1; a++) {
                        for (int b = 0; b <= 1; b++) {
                            count += add(sum, ex[i][j + a][k + b], xs[i], xs[i + 1], ys[j + a], ys[j + a], zs[k + b], zs[k + b]);
                            count += add(sum, ey[i + a][j][k + b], xs[i + a], xs[i + a], ys[j], ys[j + 1], zs[k + b], zs[k + b]);
                            count += add(sum, ez[i + a][j + b][k], xs[i + a], xs[i + a], ys[j + b], ys[j + b], zs[k], zs[k + 1]);
                        }
                    }
                    if (count > 0) {
                        point[i][j][k] = new double[]{sum[0] / count, sum[1] / count, sum[2] / count};
                    }
                }
            }
        }
    }

    private static int add(double[] sum, double t, double x0, double x1, double y0, double y1, double z0, double z1) {
        if (Double.isNaN(t)) {
            return 0;
        }
        sum[0] += x0 + (x1 - x0) * t;
        sum[1] += y0 + (y1 - y0) * t;
        sum[2] += z0 + (z1 - z0) * t;
        return 1;
    }

    // the 4 cells round a crossing edge, in order round it; edges on the box wall have no 4th cell
    private List<double[]> quads() {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                for (int k = 0; k <= n; k++) {
                    if (i < n && inside(j) && inside(k) && !Double.isNaN(ex[i][j][k])) {
                        quad(out, point[i][j - 1][k - 1], point[i][j][k - 1], point[i][j][k], point[i][j - 1][k]);
                    }
                    if (j < n && inside(i) && inside(k) && !Double.isNaN(ey[i][j][k])) {
                        quad(out, point[i - 1][j][k - 1], point[i][j][k - 1], point[i][j][k], point[i - 1][j][k]);
                    }
                    if (k < n && inside(i) && inside(j) && !Double.isNaN(ez[i][j][k])) {
                        quad(out, point[i - 1][j - 1][k], point[i][j - 1][k], point[i][j][k], point[i - 1][j][k]);
                    }
                }
            }
        }
        return out;
    }

    private boolean inside(int index) {
        return index > 0 && index < n;
    }

    private static void quad(List<double[]> out, double[]... corners) {
        double[] q = new double[12];
        for (int c = 0; c < 4; c++) {
            System.arraycopy(corners[c], 0, q, 3 * c, 3);
        }
        out.add(q);
    }
}
