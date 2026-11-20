package entity.graph;

import entity.ImplicitSurface;

// F(x,y,z) = 0 in a box: viewport is the floor, z range zooms along with it
public final class ImplicitPlot implements Plot {
    static final int CELLS = 36;              // a side, ~3k quads for a sphere, spins fine
    private static final int TRACE_STEPS = 240;
    private static final int BISECT_ROUNDS = 60;

    private final ImplicitSurface surface;
    private final Viewport viewport;
    private final Viewport home;
    private final double zMin;
    private final double zMax;
    private final double homeZMin;
    private final double homeZMax;
    private SurfaceMesh mesh;

    public ImplicitPlot(ImplicitSurface surface, Viewport floor, double zMin, double zMax) {
        this(surface, floor, zMin, zMax, floor, zMin, zMax);
    }

    private ImplicitPlot(ImplicitSurface surface, Viewport viewport, double zMin, double zMax,
                         Viewport home, double homeZMin, double homeZMax) {
        this.surface = surface;
        this.viewport = viewport;
        this.zMin = zMin;
        this.zMax = zMax;
        this.home = home;
        this.homeZMin = homeZMin;
        this.homeZMax = homeZMax;
    }

    public ImplicitSurface getSurface() {
        return surface;
    }

    @Override
    public String getLatex() {
        return surface.getLatex();
    }

    @Override
    public Viewport getViewport() {
        return viewport;
    }

    @Override
    public Viewport getHome() {
        return home;
    }

    public double getZMin() {
        return zMin;
    }

    public double getZMax() {
        return zMax;
    }

    // z range grows / shrinks w/ the floor width, round the home's middle
    @Override
    public Plot lookingAt(Viewport next) {
        double half = (homeZMax - homeZMin) / 2 * next.width() / home.width();
        double mid = (homeZMin + homeZMax) / 2;
        return new ImplicitPlot(surface, next, mid - half, mid + half, home, homeZMin, homeZMax);
    }

    public synchronized SurfaceMesh getMesh() {
        if (mesh == null) {
            mesh = SurfaceNets.mesh(surface, viewport, zMin, zMax, CELLS);
        }
        return mesh;
    }

    // card only knows the floor spot under the mouse, so give the highest point of the surface over it
    @Override
    public TracePoint trace(double x, double y, double snapX) {
        if (!viewport.containsX(x) || !viewport.containsY(y)) {
            return null;
        }
        double step = (zMax - zMin) / TRACE_STEPS;
        double hi = zMax;
        double fHi = surface.evaluate(x, y, hi);
        for (int i = 1; i <= TRACE_STEPS; i++) {
            double lo = zMax - step * i;
            double fLo = surface.evaluate(x, y, lo);
            if (Double.isFinite(fLo) && Double.isFinite(fHi) && (fLo < 0) != (fHi < 0)) {
                double z = root(x, y, lo, hi, fLo);
                double fz = surface.evaluate(x, y, z);
                if (Math.abs(fz) <= Math.max(Math.abs(fLo), Math.abs(fHi))) {   // else it was a pole
                    return TracePoint.onSurface(x, y, z);
                }
            }
            hi = lo;
            fHi = fLo;
        }
        return null;
    }

    private double root(double x, double y, double lo, double hi, double fLo) {
        for (int r = 0; r < BISECT_ROUNDS; r++) {
            double mid = (lo + hi) / 2;
            double fMid = surface.evaluate(x, y, mid);
            if (fMid == 0 || !Double.isFinite(fMid)) {
                return mid;
            }
            if ((fMid < 0) == (fLo < 0)) {
                lo = mid;
                fLo = fMid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    @Override
    public <T> T accept(PlotVisitor<T> visitor) {
        return visitor.visitImplicit(this);
    }
}
