package use_case.graph_function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// a 2d graph ready to draw: the window, the line pieces in world units, the key points
public class CurveOutputData {
    private final String latex;
    private final double xMin;
    private final double xMax;
    private final double yMin;
    private final double yMax;
    private final List<List<double[][]>> layers;
    private final List<String> latexes;            // each function on its own, same order as layers   // one per function, each piece {xs, ys}
    private final List<KeyPointOutputData> keyPoints;

    public CurveOutputData(String latex, double xMin, double xMax, double yMin, double yMax,
                           List<double[][]> pieces, List<KeyPointOutputData> keyPoints) {
        this(List.of(latex), latex, new double[]{xMin, xMax, yMin, yMax}, Collections.singletonList(pieces), keyPoints);
    }

    // several functions: layers.get(i) = the line pieces of function i
    public static CurveOutputData layered(List<String> latexes, String latex, double xMin, double xMax, double yMin,
                                          double yMax, List<List<double[][]>> layers,
                                          List<KeyPointOutputData> keyPoints) {
        return new CurveOutputData(latexes, latex, new double[]{xMin, xMax, yMin, yMax}, layers, keyPoints);
    }

    private CurveOutputData(List<String> latexes, String latex, double[] window, List<List<double[][]>> layers,
                            List<KeyPointOutputData> keyPoints) {
        this.latexes = List.copyOf(latexes);
        this.latex = latex;
        this.xMin = window[0];
        this.xMax = window[1];
        this.yMin = window[2];
        this.yMax = window[3];
        List<List<double[][]>> copy = new ArrayList<>();
        for (List<double[][]> layer : layers) {
            copy.add(Collections.unmodifiableList(new ArrayList<>(layer)));
        }
        this.layers = Collections.unmodifiableList(copy);
        this.keyPoints = Collections.unmodifiableList(new ArrayList<>(keyPoints));
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

    // the first function's pieces
    public List<double[][]> getPieces() {
        return layers.get(0);
    }

    public List<List<double[][]>> getLayers() {
        return layers;
    }

    public List<String> getLatexes() {
        return latexes;
    }

    public List<KeyPointOutputData> getKeyPoints() {
        return keyPoints;
    }
}
