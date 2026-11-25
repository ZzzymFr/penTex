package use_case.explore_graph;

import entity.graph.LookupQuery;
import entity.graph.Plot;
import entity.graph.PlotLookup;
import entity.graph.Viewport;
import use_case.graph_function.GraphFunctionOutputBoundary;
import use_case.graph_function.GraphSessionDataAccessInterface;
import use_case.graph_function.PlotPublisher;

import java.util.List;
import java.util.function.UnaryOperator;

// pan / zoom / reset re-sample the graph that's up, trace reads the real value under the cursor,
// lookup works out the blank boxes of a find
public class ExploreGraphInteractor implements ExploreGraphInputBoundary {
    private final GraphSessionDataAccessInterface session;
    private final PlotPublisher publisher;

    public ExploreGraphInteractor(GraphSessionDataAccessInterface session, GraphFunctionOutputBoundary presenter) {
        this.session = session;
        this.publisher = new PlotPublisher(presenter);
    }

    @Override
    public void pan(double dx, double dy) {
        move(v -> v.panned(dx, dy));
    }

    @Override
    public void zoom(double cx, double cy, double fx, double fy) {
        move(v -> v.zoomed(cx, cy, fx, fy));
    }

    @Override
    public void reset() {
        Plot plot = session.current();
        if (plot != null) {
            show(plot.lookingAt(plot.getHome()));
        }
    }

    @Override
    public void trace(double x, double y, double snapX) {
        trace(x, y, snapX, 0);
    }

    @Override
    public void trace(double x, double y, double snapX, int layer) {
        Plot plot = session.current();
        if (plot != null) {
            publisher.publishTrace(plot.trace(x, y, snapX, layer));
        }
    }

    @Override
    public void endTrace() {
        publisher.publishTrace(null);
    }

    @Override
    public void lookup(List<String> typed) {
        Plot plot = session.current();
        if (plot != null) {
            publisher.publishLookup(plot.accept(new PlotLookup(LookupQuery.read(typed))));
        }
    }

    @Override
    public void clearLookup() {
        publisher.publishLookup(null);
    }

    private void move(UnaryOperator<Viewport> change) {
        Plot plot = session.current();
        if (plot == null) {
            return;
        }
        Viewport next = change.apply(plot.getViewport());
        if (!next.equals(plot.getViewport())) {
            show(plot.lookingAt(next));
        }
    }

    private void show(Plot plot) {
        session.save(plot);
        publisher.publish(plot, false);
    }
}
