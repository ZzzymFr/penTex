package use_case.graph_function;

import entity.ImplicitCurve;
import entity.ImplicitCurveReader;
import entity.ImplicitSurface;
import entity.ImplicitSurfaceReader;
import entity.LatexFunctionParser;
import entity.LatexParseException;
import entity.MathFunction;
import entity.SurfaceFunction;
import entity.graph.AutoBox;
import entity.graph.AutoRange;
import entity.graph.CurvePlot;
import entity.graph.ImplicitCurvePlot;
import entity.graph.ImplicitPlot;
import entity.graph.Plot;
import entity.graph.SurfacePlot;
import entity.graph.Viewport;

import java.util.ArrayList;
import java.util.List;

// latex -> a live plot, all in java, no chart api: curve if it's f(x), surface if it's f(x, y)
public class GraphFunctionInteractor implements GraphFunctionInputBoundary {
    // how many points the auto y range looks at
    static final int RANGE_SAMPLES = 400;

    private final LatexFunctionParser parser;
    private final ImplicitSurfaceReader surfaces;   // x y z equations
    private final ImplicitCurveReader curves = new ImplicitCurveReader();   // x y equations, y not on its own
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
        List<String> latexes = new ArrayList<>();
        for (String latex : input.getLatexes()) {
            if (latex == null || latex.trim().isEmpty()) {
                presenter.prepareFailView("No expression to graph.");
                return;
            }
            latexes.add(latex.trim());
        }
        if (latexes.isEmpty()) {
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
        if (!input.isZAutomatic() && !(input.getZMin() < input.getZMax())) {
            presenter.prepareFailView("The z range is invalid: z min must be less than z max.");
            return;
        }
        Plot plot = build(latexes, input);
        if (plot != null) {
            session.save(plot);
            publisher.publish(plot, true);
        }
    }

    // all curves, or all z = f(x, y) surfaces (each one curve first, surface if it only makes sense with a y
    // in it), or one x y z equation on its own; null after a fail view
    private Plot build(List<String> latexes, GraphFunctionInputData input) {
        List<MathFunction> curves = new ArrayList<>();
        List<SurfaceFunction> grids = new ArrayList<>();
        for (String latex : latexes) {
            LatexParseException curveError;
            try {
                curves.add(parser.parse(latex));
                continue;
            } catch (LatexParseException e) {
                curveError = e;
            }
            try {
                grids.add(parser.parseSurface(latex));
                continue;
            } catch (LatexParseException e) {
                // not z = f(x,y) either, maybe an x y z equation
            }
            ImplicitSurface xyz = readOrNull(latex);
            ImplicitCurve xy = xyz == null ? readCurveOrNull(latex) : null;   // 4 = 3y^3 + 2 - 9x^2, x^2 + y^2 = 1
            if (xyz == null && xy == null) {
                presenter.prepareFailView("Cannot graph \"" + latex + "\": " + curveError.getMessage());
                return null;
            }
            if (latexes.size() > 1) {
                presenter.prepareFailView("\"" + latex + "\" is an " + (xyz != null ? "x y z" : "x y")
                        + " equation; those are graphed on their own, they can't share a graph with another"
                        + " function yet.");
                return null;
            }
            return xyz != null ? implicit(xyz, input) : implicitCurve(xy, input);
        }
        if (!curves.isEmpty() && !grids.isEmpty()) {
            presenter.prepareFailView("A 2D curve and a 3D surface can't share one graph: \""
                    + curves.get(0).getLatex() + "\" is 2D, \"" + grids.get(0).getLatex() + "\" is 3D.");
            return null;
        }
        return curves.isEmpty() ? surface(grids, input) : curve(curves, input);
    }

    private ImplicitSurface readOrNull(String latex) {
        try {
            return surfaces.read(latex);
        } catch (LatexParseException e) {
            return null;
        }
    }

    private ImplicitCurve readCurveOrNull(String latex) {
        try {
            return curves.read(latex);
        } catch (LatexParseException e) {
            return null;
        }
    }

    // 2d F(x, y) = 0: x range as typed, y range as typed or the same as x
    private Plot implicitCurve(ImplicitCurve f, GraphFunctionInputData input) {
        double yMin = input.isYAutomatic() ? input.getXMin() : input.getYMin();
        double yMax = input.isYAutomatic() ? input.getXMax() : input.getYMax();
        ImplicitCurvePlot plot = new ImplicitCurvePlot(f, new Viewport(input.getXMin(), input.getXMax(), yMin, yMax));
        if (plot.getCurve().isEmpty()) {
            presenter.prepareFailView("\"" + f.getLatex() + "\" has no points in x in [" + input.getXMin() + ", "
                    + input.getXMax() + "], y in [" + yMin + ", " + yMax + "].");
            return null;
        }
        return plot;
    }

    // default range -> box fits the surface (sphere fills it), typed range -> that floor as is,
    // typed z range -> that box height
    private Plot implicit(ImplicitSurface f, GraphFunctionInputData input) {
        double yMin = input.isYAutomatic() ? input.getXMin() : input.getYMin();
        double yMax = input.isYAutomatic() ? input.getXMax() : input.getYMax();
        Viewport floor = new Viewport(input.getXMin(), input.getXMax(), yMin, yMax);
        boolean untouched = input.isYAutomatic() && input.getXMin() == GraphFunctionInputData.DEFAULT_X_MIN
                && input.getXMax() == GraphFunctionInputData.DEFAULT_X_MAX;
        ImplicitPlot plot;
        if (!input.isZAutomatic()) {
            plot = new ImplicitPlot(f, floor, input.getZMin(), input.getZMax());
        } else {
            plot = untouched ? AutoBox.around(f, floor) : AutoBox.within(f, floor);
        }
        if (plot.getMesh().isEmpty()) {
            presenter.prepareFailView("\"" + f.getLatex() + "\" has no points in x in [" + floor.getXMin() + ", "
                    + floor.getXMax() + "], y in [" + yMin + ", " + yMax + "], z in [" + plot.getZMin() + ", "
                    + plot.getZMax() + "].");
            return null;
        }
        return plot;
    }

    // y range: as typed, or one that fits every function
    private Plot curve(List<MathFunction> functions, GraphFunctionInputData input) {
        double xMin = input.getXMin();
        double xMax = input.getXMax();
        double[] ys = new double[(RANGE_SAMPLES + 1) * functions.size()];
        int k = 0;
        for (MathFunction f : functions) {
            for (int i = 0; i <= RANGE_SAMPLES; i++) {
                ys[k++] = f.evaluate(xMin + (xMax - xMin) * i / RANGE_SAMPLES);
            }
        }
        String name = Plot.join(functions.stream().map(MathFunction::getLatex).toList());
        double[] auto = AutoRange.window(ys);
        if (auto == null) {
            presenter.prepareFailView("\"" + name + "\" is undefined for every x in ["
                    + xMin + ", " + xMax + "].");
            return null;
        }
        if (input.isYAutomatic()) {
            return new CurvePlot(functions, new Viewport(xMin, xMax, auto[0], auto[1]));
        }
        if (!anyInside(ys, input.getYMin(), input.getYMax())) {
            presenter.prepareFailView("The graph of \"" + name + "\" is outside the y range ["
                    + input.getYMin() + ", " + input.getYMax() + "] for every x in [" + xMin + ", " + xMax + "].");
            return null;
        }
        return new CurvePlot(functions, new Viewport(xMin, xMax, input.getYMin(), input.getYMax()));
    }

    // 3d: x box is the x range, y box is the y range if typed, else same as x; z cut off if typed
    private Plot surface(List<SurfaceFunction> functions, GraphFunctionInputData input) {
        double yMin = input.isYAutomatic() ? input.getXMin() : input.getYMin();
        double yMax = input.isYAutomatic() ? input.getXMax() : input.getYMax();
        double[] z = input.isZAutomatic() ? null : new double[]{input.getZMin(), input.getZMax()};
        SurfacePlot plot = new SurfacePlot(functions, new Viewport(input.getXMin(), input.getXMax(), yMin, yMax), z);
        if (plot.isEmpty() && z != null) {
            presenter.prepareFailView("The graph of \"" + plot.getLatex() + "\" is outside the z range ["
                    + z[0] + ", " + z[1] + "] everywhere on x in [" + input.getXMin() + ", " + input.getXMax()
                    + "], y in [" + yMin + ", " + yMax + "].");
            return null;
        }
        if (plot.isEmpty()) {
            presenter.prepareFailView("\"" + plot.getLatex() + "\" is undefined everywhere on x in ["
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
