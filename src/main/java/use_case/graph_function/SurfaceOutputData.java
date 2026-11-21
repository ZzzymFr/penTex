package use_case.graph_function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// a 3d graph ready to draw: x / y floor, z range, z[i][j] on an even grid (NaN = hole), one grid per
// surface when several share the box; or for an x y z equation loose quads instead of the grid
public class SurfaceOutputData {
    private final String latex;
    private final double xMin;
    private final double xMax;
    private final double yMin;
    private final double yMax;
    private final double zMin;
    private final double zMax;
    private final List<double[][]> layers;   // one z grid per surface, empty for a mesh
    private final List<String> latexes;      // each surface on its own, same order
    private final double[][] mesh;           // rows of x0 y0 z0 .. x3 y3 z3, null on a grid

    public SurfaceOutputData(String latex, double xMin, double xMax, double yMin, double yMax,
                             double zMin, double zMax, double[][] z) {
        this(latex, xMin, xMax, yMin, yMax, zMin, zMax, z, null);
    }

    public SurfaceOutputData(String latex, double xMin, double xMax, double yMin, double yMax,
                             double zMin, double zMax, double[][] z, double[][] mesh) {
        this(List.of(latex), latex, xMin, xMax, yMin, yMax, zMin, zMax,
                z == null ? Collections.emptyList() : Collections.singletonList(z), mesh);
    }

    // several surfaces in one box, same x / y grid each
    public SurfaceOutputData(List<String> latexes, String latex, double xMin, double xMax, double yMin,
                             double yMax, double zMin, double zMax, List<double[][]> layers) {
        this(latexes, latex, xMin, xMax, yMin, yMax, zMin, zMax, layers, null);
    }

    private SurfaceOutputData(List<String> latexes, String latex, double xMin, double xMax, double yMin,
                              double yMax, double zMin, double zMax, List<double[][]> layers, double[][] mesh) {
        this.latexes = List.copyOf(latexes);
        this.latex = latex;
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.zMin = zMin;
        this.zMax = zMax;
        this.layers = Collections.unmodifiableList(new ArrayList<>(layers));
        this.mesh = mesh;
    }

    public String getLatex() {
        return latex;
    }

    public double getXMin() {
        return xMin;
    }

    public double getXMax() {
        return xMax;
    }

    public double getYMin() {
        return yMin;
    }

    public double getYMax() {
        return yMax;
    }

    public double getZMin() {
        return zMin;
    }

    public double getZMax() {
        return zMax;
    }

    // the first surface's grid, null for a mesh
    public double[][] getZ() {
        return layers.isEmpty() ? null : layers.get(0);
    }

    public List<double[][]> getLayers() {
        return layers;
    }

    public List<String> getLatexes() {
        return latexes;
    }

    public double[][] getMesh() {
        return mesh;
    }
}
