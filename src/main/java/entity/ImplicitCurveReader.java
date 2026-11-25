package entity;

import entity.node.Node;
import entity.node.RelationNode;
import entity.value.NumberValue;
import entity.value.Value;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// one equation in exactly x and y -> F(x,y), e.g. x^2 + y^2 = 1 or 4 = 3y^3 + 2 - 9x^2,
// neither side has to be y on its own; uses calculate's parser so it reads what the model writes
public class ImplicitCurveReader {
    private static final Set<String> AXES = Set.of("x", "y");

    public ImplicitCurve read(String latex) throws LatexParseException {
        Node node = parse(latex);
        if (!isOneEquation(node)) {
            throw new LatexParseException("Write one equation in x and y, e.g. x^2 + y^2 = 1.");
        }
        Set<String> unknowns = new HashSet<>();
        node.collectFreeVariables(unknowns);
        if (!unknowns.equals(AXES)) {
            throw new LatexParseException("A 2d equation needs exactly x and y.");
        }
        List<Node> sides = ((RelationNode) node).getOperands();
        Node left = sides.get(0);
        Node right = sides.get(1);
        return new ImplicitCurve(latex, (x, y) -> difference(left, right, x, y));
    }

    private static Node parse(String latex) throws LatexParseException {
        if (latex == null || latex.trim().isEmpty()) {
            throw new LatexParseException("The expression is empty.");
        }
        try {
            return new MathParser(MathLexer.tokenize(latex), Collections.emptyMap()).parse();
        } catch (RuntimeException | StackOverflowError e) {
            throw new LatexParseException("Can't read \"" + latex + "\".");
        }
    }

    private static boolean isOneEquation(Node node) {
        if (!(node instanceof RelationNode)) {
            return false;
        }
        List<String> ops = ((RelationNode) node).getOperators();
        return ops.size() == 1 && "=".equals(ops.get(0));
    }

    // fresh context per point, one context's step budget runs out over a whole grid
    private static double difference(Node left, Node right, double x, double y) {
        Context at = new Context(Collections.emptyMap()).withVariable("x", x).withVariable("y", y);
        try {
            return number(left.evaluate(at)) - number(right.evaluate(at));
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private static double number(Value v) {
        return v instanceof NumberValue ? ((NumberValue) v).getValue() : Double.NaN;
    }
}
