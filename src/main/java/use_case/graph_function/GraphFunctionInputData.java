package use_case.graph_function;

/**
 * The LaTeX to graph (e.g. recognised from handwriting), the x range to show, and optionally the
 * y range (when it is not given, the y range is chosen automatically from the function's values).
 * A 3D graph can also be given a z range; outside it the surface is cut off. Without one, z is
 * chosen from the surface's values. Several functions can share one graph (all 2D or all 3D).
 */
public class GraphFunctionInputData {
    public static final double DEFAULT_X_MIN = -10;
    public static final double DEFAULT_X_MAX = 10;
    /** Pass as both y bounds to have the y range chosen automatically. */
    public static final double AUTOMATIC = Double.NaN;

    private final java.util.List<String> latexes;
    private final double xMin;
    private final double xMax;
    private final double yMin;
    private final double yMax;
    private final double zMin;
    private final double zMax;

    public GraphFunctionInputData(String latex) {
        this(latex, DEFAULT_X_MIN, DEFAULT_X_MAX);
    }

    public GraphFunctionInputData(String latex, double xMin, double xMax) {
        this(latex, xMin, xMax, AUTOMATIC, AUTOMATIC);
    }

    /** @param yMin the bottom of the y range, or {@link #AUTOMATIC} (with yMax) to choose it from the values */
    public GraphFunctionInputData(String latex, double xMin, double xMax, double yMin, double yMax) {
        this(latex, xMin, xMax, yMin, yMax, AUTOMATIC, AUTOMATIC);
    }

    /** @param zMin the bottom of a 3D graph's z range, or {@link #AUTOMATIC} (with zMax) to choose it */
    public GraphFunctionInputData(String latex, double xMin, double xMax, double yMin, double yMax,
                                  double zMin, double zMax) {
        this(java.util.Collections.singletonList(latex), xMin, xMax, yMin, yMax, zMin, zMax);
    }

    /** Several functions drawn together on one graph, first one first; ranges as above. */
    public GraphFunctionInputData(java.util.List<String> latexes, double xMin, double xMax, double yMin, double yMax,
                                  double zMin, double zMax) {
        this.latexes = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(latexes));
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.zMin = zMin;
        this.zMax = zMax;
    }

    /** The first (or only) function. */
    public String getLatex() {
        return latexes.isEmpty() ? null : latexes.get(0);
    }

    /** Every function to draw, in order. */
    public java.util.List<String> getLatexes() {
        return latexes;
    }

    public double getXMin() {
        return xMin;
    }

    public double getXMax() {
        return xMax;
    }

    /** True when no y range was given, so it is chosen from the function's values. */
    public boolean isYAutomatic() {
        return Double.isNaN(yMin) && Double.isNaN(yMax);
    }

    public double getYMin() {
        return yMin;
    }

    public double getYMax() {
        return yMax;
    }

    /** True when no z range was given, so a 3D graph picks one from its values. */
    public boolean isZAutomatic() {
        return Double.isNaN(zMin) && Double.isNaN(zMax);
    }

    public double getZMin() {
        return zMin;
    }

    public double getZMax() {
        return zMax;
    }
}
