package use_case.graph_function;

import entity.graph.Curve;
import entity.graph.CurvePlot;
import entity.graph.KeyPoint;
import entity.graph.Plot;
import entity.graph.PlotVisitor;
import entity.graph.SurfaceGrid;
import entity.graph.SurfacePlot;
import entity.graph.TracePoint;
import entity.graph.Viewport;

import java.util.ArrayList;
import java.util.List;

// plot -> plain output data -> presenter, shared by the graph + explore use cases
public class PlotPublisher {
    private final GraphFunctionOutputBoundary presenter;

    public PlotPublisher(GraphFunctionOutputBoundary presenter) {
        this.presenter = presenter;
    }

    public void publish(Plot plot, boolean fresh) {
        plot.accept(new PlotVisitor<Void>() {
            @Override
            public Void visitCurve(CurvePlot curve) {
                presenter.prepareCurveView(curveData(curve), fresh);
                return null;
            }

            @Override
            public Void visitSurface(SurfacePlot surface) {
                presenter.prepareSurfaceView(surfaceData(surface), fresh);
                return null;
            }
        });
    }

    public void publishTrace(TracePoint point) {
        presenter.prepareTraceView(point == null ? null
                : new TraceOutputData(point.getX(), point.getY(), point.getZ(), point.getKind()));
    }

    static CurveOutputData curveData(CurvePlot plot) {
        Viewport v = plot.getViewport();
        List<List<double[][]>> layers = new ArrayList<>();
        for (Curve curve : plot.getCurves()) {
            List<double[][]> pieces = new ArrayList<>();
            for (Curve.Piece piece : curve.getPieces()) {
                pieces.add(new double[][]{piece.getXs(), piece.getYs()});
            }
            layers.add(pieces);
        }
        List<KeyPointOutputData> keys = new ArrayList<>();
        for (KeyPoint k : plot.getKeyPoints()) {
            keys.add(new KeyPointOutputData(k.getX(), k.getY(), k.getKind()));
        }
        List<String> names = plot.getFunctions().stream().map(f -> f.getLatex()).toList();
        return CurveOutputData.layered(names, plot.getLatex(), v.getXMin(), v.getXMax(), v.getYMin(), v.getYMax(), layers, keys);
    }

    static SurfaceOutputData surfaceData(SurfacePlot plot) {
        Viewport v = plot.getGrid().getDomain();
        List<double[][]> layers = new ArrayList<>();
        for (SurfaceGrid grid : plot.getGrids()) {
            layers.add(grid.getZ());
        }
        double[] z = plot.zWindow();
        List<String> names = plot.getFunctions().stream().map(f -> f.getLatex()).toList();
        return new SurfaceOutputData(names, plot.getLatex(), v.getXMin(), v.getXMax(), v.getYMin(), v.getYMax(),
                z[0], z[1], layers);
    }
}
