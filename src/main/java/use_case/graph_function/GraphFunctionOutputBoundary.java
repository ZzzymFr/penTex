package use_case.graph_function;

// how graphing reports back, fresh = a brand new graph, not just a pan / zoom of the old one
public interface GraphFunctionOutputBoundary {
    void prepareCurveView(CurveOutputData data, boolean fresh);

    void prepareSurfaceView(SurfaceOutputData data, boolean fresh);

    // null = cursor left, hide the readout
    void prepareTraceView(TraceOutputData data);

    void prepareFailView(String errorMessage);

    // what a find came back with, null = take it off the graph
    void prepareLookupView(use_case.lookup.LookupOutputData data);
}
