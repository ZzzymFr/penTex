package entity;

// F(x, y, z) = left - right of an equation in x, y, z, the 3d graph draws where it hits 0
public class ImplicitSurface {
    // java has no 3 double operator
    public interface Rule {
        double at(double x, double y, double z);
    }

    private final String latex;
    private final Rule rule;

    public ImplicitSurface(String latex, Rule rule) {
        this.latex = latex;
        this.rule = rule;
    }

    public String getLatex() {
        return latex;
    }

    // NaN where it's undefined
    public double evaluate(double x, double y, double z) {
        return rule.at(x, y, z);
    }
}
