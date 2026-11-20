package entity.integral;

import entity.Context;
import entity.MathLexer;
import entity.MathParser;
import entity.UncalculableException;
import entity.UncalculableReason;
import entity.UndefinedException;
import entity.node.Node;
import entity.value.NumberValue;
import entity.value.Value;

import java.util.Collections;
import java.util.List;

// F(b) - F(a) w/ our own calculator, the api only gives us F
public final class DefiniteIntegral {
    private static final int SAMPLES = 401;  // odd so the middle (often 0) gets hit

    private DefiniteIntegral() {
    }

    // a bound has to be a plain finite number, ∞ is improper and we dont do limits here
    public static double bound(List<String> tokens) {
        double value = number(parse(tokens), new Context(Collections.emptyMap()));
        if (Double.isInfinite(value)) {
            throw new UncalculableException(UncalculableReason.BEYOND_RANGE);
        }
        return value;
    }

    public static double value(Integral integral, String antiderivativeLatex) {
        double a = bound(integral.getLower());
        double b = bound(integral.getUpper());
        checkIntegrandIsFinite(integral, a, b);
        Node f = parse(MathLexer.tokenize(antiderivativeLatex));
        Context context = new Context(Collections.emptyMap());
        String x = integral.getVariable();
        double result = number(f, context.withVariable(x, b)) - number(f, context.withVariable(x, a));
        if (!Double.isFinite(result)) {
            throw new UndefinedException();
        }
        return result;
    }

    // FTC lies if f blows up in between (1/x^2 from -1 to 1), so sample it first
    private static void checkIntegrandIsFinite(Integral integral, double a, double b) {
        Node f;
        try {
            f = parse(integral.getIntegrand());
        } catch (RuntimeException e) {
            return;  // our parser cant read it but the api could, just trust F then
        }
        Context context = new Context(Collections.emptyMap());
        for (int i = 0; i < SAMPLES; i++) {
            double x = a + (b - a) * i / (SAMPLES - 1);
            double y;
            try {
                y = number(f, context.withVariable(integral.getVariable(), x));
            } catch (UncalculableException e) {
                throw new UndefinedException();
            }
            if (!Double.isFinite(y)) {
                throw new UndefinedException();
            }
        }
    }

    private static Node parse(List<String> tokens) {
        return new MathParser(tokens, Collections.emptyMap()).parse();
    }

    private static double number(Node node, Context context) {
        Value value = node.evaluate(context);
        if (!(value instanceof NumberValue)) {
            throw new UncalculableException(UncalculableReason.WRONG_KIND);
        }
        return ((NumberValue) value).getValue();
    }
}
