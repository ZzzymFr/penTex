package use_case.graph_function;

import entity.ImplicitSurface;
import entity.ImplicitSurfaceReader;
import entity.LatexFunctionParser;
import entity.LatexParseException;
import entity.MathFunction;
import entity.SurfaceFunction;
import entity.graph.AutoBox;
import entity.graph.AutoRange;
import entity.graph.CurvePlot;
import entity.graph.ImplicitPlot;
import entity.graph.Plot;
import entity.graph.SurfacePlot;
import entity.graph.Viewport;

// latex -> a live plot, all in java, no chart api: curve if it's f(x), surface if it's f(x, y)
public class GraphFunctionInteractor implements GraphFunctionInputBoundary {
    // how many points the auto y range looks at
    static final int RANGE_SAMPLES = 400;

    private final LatexFunctionParser parser;
    private final ImplicitSurfaceReader surfaces;   // x y z equations
    private final GraphSessionDataAccessInterface session;
    private final GraphFunctionOutputBoundary presenter;
    private final PlotPublisher publisher;

    public GraphFunctionInteractor(LatexFunctionParser parser, GraphSessionDataAccessInterface session,
                                   GraphFunctionOutputBoundary presenter) {
        this(parser, new ImplicitSurfaceReader(), session, presenter);
    }

    public GraphFunctionInteractor(LatexFunctionParser parser, ImplicitSurfaceReader surfaces,
                                   GraphSessionDataAccessInterface session, GraphFunctionOutputBoundary presenter) {
        this.parser = parser;
        this.surfaces = surfaces;
        this.session = session;
        this.presenter = presenter;
        this.publisher = new PlotPublisher(presenter);
    }

    @Override
    public void execute(GraphFunctionInputData input) {
        String latex = input.getLatex();
        if (latex == null || latex.trim().isEmpty()) {
            presenter.prepareFailView("No expression to graph.");
            return;
        }
        if (!(input.getXMin() < input.getXMax())) {
            presenter.prepareFailView("The x range is invalid: x min must be less than x max.");
            return;
        }
        if (!input.isYAutomatic() && !(input.getYMin() < input.getYMax())) {
            presenter.prepareFailView("The y range is invalid: y min must be less than y max.");
            return;
        }
        Plot plot = build(latex.trim(), input);
        if (plot != null) {
            session.save(plot);
            publisher.publish(plot, true);
        }
    }

    // curve first, surface if it only makes sense with a y in it; null after a fail view
    private Plot build(String latex, GraphFunctionInputData input) {
        LatexParseException curveError;
        try {
            return curve(parser.parse(latex), input);
        } catch (LatexParseException e) {
            curveError = e;
        }
        try {
            return surface(parser.parseSurface(latex), input);
        } catch (LatexParseException e) {
            // not z = f(x,y) either, maybe an x y z equation
        }
        try {
            return implicit(surfaces.read(latex), input);
        } catch (LatexParseException e) {
            presenter.prepareFailView("Cannot graph \"" + latex + "\": " + curveError.getMessage());
            return null;
        }
    }

    // default range -> box fits the surface (sphere fills it), typed range -> that floor as is
    private Plot implicit(ImplicitSurface f, GraphFunctionInputData input) {
        double yMin = input.isYAutomatic() ? input.getXMin() : input.getYMin();
        double yMax = input.isYAutomatic() ? input.getXMax() : input.getYMax();
        Viewport floor = new Viewport(input.getXMin(), input.getXMax(), yMin, yMax);
        boolean untouched = input.isYAutomatic() && input.getXMin() == GraphFunctionInputData.DEFAULT_X_MIN
                && input.getXMax() == GraphFunctionInputData.DEFAULT_X_MAX;
        ImplicitPlot plot = untouched ? AutoBox.around(f, floor) : AutoBox.within(f, floor);
        if (plot.getMesh().isEmpty()) {
            presenter.prepareFailView("\"" + f.getLatex() + "\" has no points in x in [" + floor.getXMin() + ", "
                    + floor.getXMax() + "], y in [" + yMin + ", " + yMax + "], z in [" + plot.getZMin() + ", "
                    + plot.getZMax() + "].");
            return null;
        }
        return plot;
    }

    private Plot curve(MathFunction f, GraphFunctionInputData input) {
        double xMin = input.getXMin();
        double xMax = input.getXMax();
        double[] ys = new double[RANGE_SAMPLES + 1];
        for (int i = 0; i <= RANGE_SAMPLES; i++) {
            ys[i] = f.evaluate(xMin + (xMax - xMin) * i / RANGE_SAMPLES);
        }
        double[] auto = AutoRange.window(ys);
        if (auto == null) {
            presenter.prepareFailView("\"" + f.getLatex() + "\" is undefined for every x in ["
                    + xMin + ", " + xMax + "].");
            return null;
        }
        if (input.isYAutomatic()) {
            return new CurvePlot(f, new Viewport(xMin, xMax, auto[0], auto[1]));
        }
        if (!anyInside(ys, input.getYMin(), input.getYMax())) {
            presenter.prepareFailView("The graph of \"" + f.getLatex() + "\" is outside the y range ["
                    + input.getYMin() + ", " + input.getYMax() + "] for every x in [" + xMin + ", " + xMax + "].");
            return null;
        }
        return new CurvePlot(f, new Viewport(xMin, xMax, input.getYMin(), input.getYMax()));
    }

    // 3d: x box is the x range, y box is the y range if typed, else same as x
    private Plot surface(SurfaceFunction f, GraphFunctionInputData input) {
        double yMin = input.isYAutomatic() ? input.getXMin() : input.getYMin();
        double yMax = input.isYAutomatic() ? input.getXMax() : input.getYMax();
        SurfacePlot plot = new SurfacePlot(f, new Viewport(input.getXMin(), input.getXMax(), yMin, yMax));
        if (plot.getGrid().isEmpty()) {
            presenter.prepareFailView("\"" + f.getLatex() + "\" is undefined everywhere on x in ["
                    + input.getXMin() + ", " + input.getXMax() + "], y in [" + yMin + ", " + yMax + "].");
            return null;
        }
        return plot;
    }

    private static boolean anyInside(double[] ys, double lo, double hi) {
        for (double y : ys) {
            if (Double.isFinite(y) && y >= lo && y <= hi) {
                return true;
            }
        }
        return false;
    }
}
