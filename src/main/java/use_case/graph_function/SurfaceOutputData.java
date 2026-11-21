package use_case.graph_function;

// a 3d graph ready to draw: x / y floor, z range, z[i][j] on an even grid (NaN = hole)
public class SurfaceOutputData {
    private final String latex;
    private final double xMin;
    private final double xMax;
    private final double yMin;
    private final double yMax;
    private final double zMin;
    private final double zMax;
    private final java.util.List<double[][]> layers;   // one z grid per surface
    private final java.util.List<String> latexes;      // each surface on its own, same order

    public SurfaceOutputData(String latex, double xMin, double xMax, double yMin, double yMax,
                             double zMin, double zMax, double[][] z) {
        this(java.util.List.of(latex), latex, xMin, xMax, yMin, yMax, zMin, zMax,
                java.util.Collections.singletonList(z));
    }

    // several surfaces in one box, same x / y grid each
    public SurfaceOutputData(java.util.List<String> latexes, String latex, double xMin, double xMax, double yMin,
                             double yMax, double zMin, double zMax, java.util.List<double[][]> layers) {
        this.latexes = java.util.List.copyOf(latexes);
        this.latex = latex;
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.zMin = zMin;
        this.zMax = zMax;
        this.layers = java.util.List.copyOf(layers);
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
        return layers.get(0);
    }

    public java.util.List<double[][]> getLayers() {
        return layers;
    }

    public java.util.List<String> getLatexes() {
        return latexes;
    }
}
