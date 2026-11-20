package entity.graph;

import entity.ImplicitSurface;

// AutoRange but for F(x,y,z)=0: a small closed surface (sphere) gets a box it fills, else the asked floor
public final class AutoBox {
    // the 3d card's box is this tall for its width, z range follows so a sphere stays round
    public static final double BOX_HEIGHT = 0.72;
    static final int PROBE_CELLS = 24;
    private static final int PASSES = 2;      // 2nd pass on the tighter box fixes the coarse first guess
    private static final double PAD = 1.2;
    private static final double[] NICE = {1, 1.25, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10};

    private AutoBox() {
    }

    // the floor as given, z as tall as the card's box
    public static ImplicitPlot within(ImplicitSurface surface, Viewport floor) {
        double half = Math.max(floor.width(), floor.height()) / 2;
        return new ImplicitPlot(surface, floor, -BOX_HEIGHT * half, BOX_HEIGHT * half);
    }

    public static ImplicitPlot around(ImplicitSurface surface, Viewport floor) {
        ImplicitPlot asked = within(surface, floor);
        ImplicitPlot fit = asked;
        for (int pass = 0; pass < PASSES; pass++) {
            ImplicitPlot next = tighter(fit);
            if (next == null) {
                break;
            }
            fit = next;
        }
        return fit.getViewport().width() < floor.width() ? fit : asked;
    }

    // null = surface runs into the walls (goes on past the box) or isn't there
    private static ImplicitPlot tighter(ImplicitPlot plot) {
        Viewport v = plot.getViewport();
        double[] b = SurfaceNets.mesh(plot.getSurface(), v, plot.getZMin(), plot.getZMax(), PROBE_CELLS).bounds();
        if (b == null || touchesWalls(b, v, plot.getZMin(), plot.getZMax())) {
            return null;
        }
        // mesh points can sit up to a cell inside the real edge, so add one
        double hx = Math.max(b[1] - b[0], b[3] - b[2]) / 2 + Math.max(v.width(), v.height()) / PROBE_CELLS;
        double hz = (b[5] - b[4]) / 2 + (plot.getZMax() - plot.getZMin()) / PROBE_CELLS;
        double s = nice(PAD * Math.max(hx, hz / BOX_HEIGHT), true);
        double unit = nice(s / 5, false);
        double cx = snap((b[0] + b[1]) / 2, unit);
        double cy = snap((b[2] + b[3]) / 2, unit);
        double cz = snap((b[4] + b[5]) / 2, unit);
        return new ImplicitPlot(plot.getSurface(), new Viewport(cx - s, cx + s, cy - s, cy + s),
                cz - BOX_HEIGHT * s, cz + BOX_HEIGHT * s);
    }

    // within a probe cell of a wall
    private static boolean touchesWalls(double[] b, Viewport v, double zMin, double zMax) {
        double cx = v.width() / PROBE_CELLS;
        double cy = v.height() / PROBE_CELLS;
        double cz = (zMax - zMin) / PROBE_CELLS;
        return b[0] < v.getXMin() + cx || b[1] > v.getXMax() - cx || b[2] < v.getYMin() + cy
                || b[3] > v.getYMax() - cy || b[4] < zMin + cz || b[5] > zMax - cz;
    }

    // 1, 1.25, 1.5, 2 .. x 10^k, up = next one at or above v, else at or below
    private static double nice(double v, boolean up) {
        double decade = Math.pow(10, Math.floor(Math.log10(v)));
        double best = up ? decade * 10 : decade;
        for (double n : NICE) {
            double c = n * decade;
            if (up ? c >= v && c < best : c <= v && c > best) {
                best = c;
            }
        }
        return best;
    }

    private static double snap(double v, double unit) {
        return Math.round(v / unit) * unit;
    }
}
