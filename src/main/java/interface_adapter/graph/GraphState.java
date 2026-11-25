package interface_adapter.graph;

import java.util.Collections;
import java.util.List;

// everything the graph card draws, already in view terms: window, line pieces / z grid, ticks, readout
public class GraphState {
    public enum Kind { NONE, CURVE, SURFACE }

    private String latex = "";
    private Kind kind = Kind.NONE;
    private String error;
    private long revision;   // goes up on every brand new graph, the card resets its camera on it
    private double xMin;
    private double xMax;
    private double yMin;
    private double yMax;
    private double zMin;
    private double zMax;
    private List<String> functions = Collections.emptyList();   // each function's latex, several share a graph
    private List<List<double[][]>> curveLayers = Collections.emptyList();   // line pieces per function
    private List<double[][]> zLayers = Collections.emptyList();             // z grid per surface
    private List<GraphMarker> markers = Collections.emptyList();
    private double[][] z;
    private double[][] mesh;   // x y z equation: loose quads, null otherwise
    private List<GraphTick> xTicks = Collections.emptyList();
    private List<GraphTick> yTicks = Collections.emptyList();
    private List<GraphTick> zTicks = Collections.emptyList();
    private GraphTrace trace;
    private GraphLookup lookup;   // the last find's points / cut, null = none

    public GraphState() {
    }

    public GraphState(GraphState copy) {
        latex = copy.latex;
        kind = copy.kind;
        error = copy.error;
        revision = copy.revision;
        xMin = copy.xMin;
        xMax = copy.xMax;
        yMin = copy.yMin;
        yMax = copy.yMax;
        zMin = copy.zMin;
        zMax = copy.zMax;
        functions = copy.functions;
        curveLayers = copy.curveLayers;
        zLayers = copy.zLayers;
        markers = copy.markers;
        z = copy.z;
        mesh = copy.mesh;
        xTicks = copy.xTicks;
        yTicks = copy.yTicks;
        zTicks = copy.zTicks;
        trace = copy.trace;
        lookup = copy.lookup;
    }

    // there's a graph to draw (an error after a good graph leaves the old one up)
    public boolean hasGraph() {
        return kind != Kind.NONE;
    }

    public String getLatex() {
        return latex;
    }

    public void setLatex(String latex) {
        this.latex = latex;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
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

    public void setWindow(double xMin, double xMax, double yMin, double yMax) {
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
    }

    public double getZMin() {
        return zMin;
    }

    public double getZMax() {
        return zMax;
    }

    public void setZRange(double zMin, double zMax) {
        this.zMin = zMin;
        this.zMax = zMax;
    }

    // the first function's line pieces
    public List<double[][]> getPieces() {
        return curveLayers.isEmpty() ? Collections.emptyList() : curveLayers.get(0);
    }

    public void setPieces(List<double[][]> pieces) {
        setCurveLayers(pieces.isEmpty() ? Collections.emptyList() : List.of(pieces));
    }

    // one list of line pieces per function
    public List<List<double[][]>> getCurveLayers() {
        return curveLayers;
    }

    public void setCurveLayers(List<List<double[][]>> layers) {
        List<List<double[][]>> copy = new java.util.ArrayList<>();
        for (List<double[][]> layer : layers) {
            copy.add(List.copyOf(layer));
        }
        this.curveLayers = List.copyOf(copy);
    }

    // one z grid per surface
    public List<double[][]> getZLayers() {
        return zLayers;
    }

    public void setZLayers(List<double[][]> layers) {
        this.zLayers = List.copyOf(layers);
        this.z = layers.isEmpty() ? null : layers.get(0);
    }

    // each function's latex on its own (the title joins them)
    public List<String> getFunctions() {
        return functions;
    }

    public void setFunctions(List<String> functions) {
        this.functions = List.copyOf(functions);
    }

    // how many functions share this graph
    public int functionCount() {
        return Math.max(1, kind == Kind.SURFACE ? zLayers.size() : curveLayers.size());
    }

    public List<GraphMarker> getMarkers() {
        return markers;
    }

    public void setMarkers(List<GraphMarker> markers) {
        this.markers = List.copyOf(markers);
    }

    // z[i][j] at x_i, y_j, null on a 2d graph
    public double[][] getZ() {
        return z;
    }

    public void setZ(double[][] z) {
        this.z = z;
        this.zLayers = z == null ? Collections.emptyList() : Collections.singletonList(z);
    }

    // rows of x0 y0 z0 .. x3 y3 z3 in world coords
    public double[][] getMesh() {
        return mesh;
    }

    public void setMesh(double[][] mesh) {
        this.mesh = mesh;
    }

    public List<GraphTick> getXTicks() {
        return xTicks;
    }

    public List<GraphTick> getYTicks() {
        return yTicks;
    }

    public List<GraphTick> getZTicks() {
        return zTicks;
    }

    public void setTicks(List<GraphTick> xTicks, List<GraphTick> yTicks, List<GraphTick> zTicks) {
        this.xTicks = List.copyOf(xTicks);
        this.yTicks = List.copyOf(yTicks);
        this.zTicks = List.copyOf(zTicks);
    }

    public GraphTrace getTrace() {
        return trace;
    }

    public void setTrace(GraphTrace trace) {
        this.trace = trace;
    }

    public GraphLookup getLookup() {
        return lookup;
    }

    public void setLookup(GraphLookup lookup) {
        this.lookup = lookup;
    }
}
