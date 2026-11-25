package use_case.graph_check;

import entity.ImplicitCurveReader;
import entity.ImplicitSurfaceReader;
import entity.LatexFunctionParser;
import entity.LatexParseException;

import java.util.regex.Pattern;

// graphable = y / f(x) (2d) or z / f(x,y) (3d) on either side of the = and the parser takes it
// or any equation in exactly x y z (3d, no name needed), or in exactly x y (2d implicit curve)
public class GraphCheckInteractor implements GraphCheckInputBoundary {
    private static final String NAME = "(y|z|[a-zA-Z]\\s*\\(\\s*x\\s*(,\\s*y\\s*)?\\))";
    private static final Pattern FUNCTION_LEFT = Pattern.compile("^\\s*" + NAME + "\\s*=.*", Pattern.DOTALL);
    // x^2 = y, same thing written the other way round
    private static final Pattern FUNCTION_RIGHT = Pattern.compile(".*=\\s*" + NAME + "\\s*$", Pattern.DOTALL);

    private final LatexFunctionParser parser;
    private final ImplicitSurfaceReader surfaces;
    private final ImplicitCurveReader curves = new ImplicitCurveReader();

    public GraphCheckInteractor(LatexFunctionParser parser) {
        this(parser, new ImplicitSurfaceReader());
    }

    public GraphCheckInteractor(LatexFunctionParser parser, ImplicitSurfaceReader surfaces) {
        this.parser = parser;
        this.surfaces = surfaces;
    }

    @Override
    public boolean canGraph(String latex) {
        if (latex == null) {
            return false;
        }
        if (namesTheFunction(latex)) {
            synchronized (parser) {   // parser keeps its place in fields, one at a time
                if (parses(latex, false) || parses(latex, true)) {
                    return true;
                }
            }
        }
        return isXyzEquation(latex)    // z=x+y+z^2 names z but only reads this way
                || isXyEquation(latex);   // 4=3y^3+2-9x^2, y isn't on its own on either side
    }

    private boolean isXyEquation(String latex) {
        try {
            curves.read(latex);
            return true;
        } catch (LatexParseException e) {
            return false;
        }
    }

    private boolean isXyzEquation(String latex) {
        try {
            surfaces.read(latex);
            return true;
        } catch (LatexParseException e) {
            return false;
        }
    }

    private boolean parses(String latex, boolean surface) {
        try {
            if (surface) {
                parser.parseSurface(latex);
            } else {
                parser.parse(latex);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean namesTheFunction(String latex) {
        return FUNCTION_LEFT.matcher(latex).matches() || FUNCTION_RIGHT.matcher(latex).matches();
    }
}
