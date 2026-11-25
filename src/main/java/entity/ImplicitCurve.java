package entity;

import java.util.function.DoubleBinaryOperator;

// F(x, y) = left - right of an equation in x and y, the 2d graph draws where it hits 0
public class ImplicitCurve {
    private final String latex;
    private final DoubleBinaryOperator rule;

    public ImplicitCurve(String latex, DoubleBinaryOperator rule) {
        this.latex = latex;
        this.rule = rule;
    }

    public String getLatex() {
        return latex;
    }

    // NaN where it's undefined
    public double evaluate(double x, double y) {
        return rule.applyAsDouble(x, y);
    }
}
