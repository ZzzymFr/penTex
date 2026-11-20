package use_case.graph_function;

// a 3d graph ready to draw: x / y floor, z range, z[i][j] on an even grid (NaN = hole)
// or for an x y z equation loose quads instead of the grid
public class SurfaceOutputData {
    private final String latex;
    private final double xMin;
    private final double xMax;
    private final double yMin;
    private final double yMax;
    private final double zMin;
    private final double zMax;
    private final double[][] z;
    private final double[][] mesh;   // rows of x0 y0 z0 .. x3 y3 z3, null on a grid

    public SurfaceOutputData(String latex, double xMin, double xMax, double yMin, double yMax,
                             double zMin, double zMax, double[][] z) {
        this(latex, xMin, xMax, yMin, yMax, zMin, zMax, z, null);
    }

    public SurfaceOutputData(String latex, double xMin, double xMax, double yMin, double yMax,
                             double zMin, double zMax, double[][] z, double[][] mesh) {
        this.latex = latex;
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.zMin = zMin;
        this.zMax = zMax;
        this.z = z;
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

    public double[][] getZ() {
        return z;
    }

    public double[][] getMesh() {
        return mesh;
    }
}
