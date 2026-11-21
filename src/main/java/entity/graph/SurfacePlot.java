package entity.graph;

import entity.SurfaceFunction;

import java.util.ArrayList;
import java.util.List;

// z = f(x, y) over an x / y window (one or more surfaces in the same box), the viewport is the floor of
// the box; a fixed z range (null = auto) is the box's height, the surfaces are cut off above and below it
public final class SurfacePlot implements Plot {
    static final int GRID = 56;

    private final List<SurfaceFunction> functions;
    private final Viewport viewport;
    private final Viewport home;
    private final double[] zRange;
    private List<SurfaceGrid> grids;

    public SurfacePlot(SurfaceFunction function, Viewport viewport) {
        this(function, viewport, null);
    }

    /** @param zRange {zMin, zMax} to cut the surface to, or null to pick z from its values */
    public SurfacePlot(SurfaceFunction function, Viewport viewport, double[] zRange) {
        this(List.of(function), viewport, zRange);
    }

    /** Several surfaces in one box, in the order they were added (first = the original). */
    public SurfacePlot(List<SurfaceFunction> functions, Viewport viewport, double[] zRange) {
        this(functions, viewport, viewport, zRange);
    }

    private SurfacePlot(List<SurfaceFunction> functions, Viewport viewport, Viewport home, double[] zRange) {
        if (functions.isEmpty()) {
            throw new IllegalArgumentException("a graph needs at least one function");
        }
        this.functions = List.copyOf(functions);
        this.viewport = viewport;
        this.home = home;
        this.zRange = zRange == null ? null : zRange.clone();
    }

    public SurfaceFunction getFunction() {
        return functions.get(0);
    }

    public List<SurfaceFunction> getFunctions() {
        return functions;
    }

    @Override
    public String getLatex() {
        List<String> names = new ArrayList<>();
        for (SurfaceFunction f : functions) {
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
        return new SurfacePlot(functions, next, home, zRange);
    }

    // the first surface's grid
    public SurfaceGrid getGrid() {
        return getGrids().get(0);
    }

    // one grid per surface, same order
    public synchronized List<SurfaceGrid> getGrids() {
        if (grids == null) {
            List<SurfaceGrid> all = new ArrayList<>();
            for (SurfaceFunction f : functions) {
                all.add(SurfaceGrid.sample(f, viewport, GRID, zRange));
            }
            grids = List.copyOf(all);
        }
        return grids;
    }

    // nothing to draw anywhere: every surface is undefined (or cut off) on the whole floor
    public boolean isEmpty() {
        for (SurfaceGrid g : getGrids()) {
            if (!g.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // the box's height: the typed z range, or one that fits every surface
    public double[] zWindow() {
        if (zRange != null) {
            return zRange.clone();
        }
        double lo = Double.POSITIVE_INFINITY;
        double hi = Double.NEGATIVE_INFINITY;
        for (SurfaceGrid g : getGrids()) {
            if (!g.isEmpty()) {
                lo = Math.min(lo, g.getZMin());
                hi = Math.max(hi, g.getZMax());
            }
        }
        return lo <= hi ? new double[]{lo, hi} : new double[]{-1, 1};
    }

    @Override
    public TracePoint trace(double x, double y, double snapX) {
        return trace(x, y, snapX, 0);
    }

    // the real f(x, y) of the surface under the cursor, the picked spot is only roughly where the mouse is
    @Override
    public TracePoint trace(double x, double y, double snapX, int layer) {
        if (!viewport.containsX(x) || !viewport.containsY(y)) {
            return null;
        }
        SurfaceFunction function = functions.get(Math.max(0, Math.min(functions.size() - 1, layer)));
        double z = function.evaluate(x, y);
        if (zRange != null && (z < zRange[0] || z > zRange[1])) {
            return null;   // cut off, nothing drawn there
        }
        return Double.isFinite(z) ? TracePoint.onSurface(x, y, z) : null;
    }

    @Override
    public <T> T accept(PlotVisitor<T> visitor) {
        return visitor.visitSurface(this);
    }
}
