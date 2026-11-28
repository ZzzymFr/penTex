package entity;

import entity.node.NamedFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The words of written mathematics as the parser knows them: which tokens are relations,
 * operators, prose or things it cannot calculate. Data and pure token tests only.
 */
final class MathVocabulary {
    private MathVocabulary() {
    }

    // stuff we cant calc (integrals, ..., maps)
    static final List<String> MEANINGLESS = Arrays.asList("\\int", "\\ldots", "\\rightarrow",
            "\\partial");
    // not a calculation, and a syntax error w/ one of these around is just prose
    static final List<String> PROSE = Arrays.asList(":", ";", "\\nabla", "\\sup", "\\inf",
            "\\limsup", "\\liminf", "\\det", "\\dim", "\\ker", "\\Pr", "\\dot", "\\ddot", "\\equiv", "\\sim",
            "\\mapsto", "\\leftarrow");
    // tokens that cant start a value
    static final List<String> OPERATORS = Arrays.asList("\\leq", "\\geq", "\\neq", "\\approx",
            "\\times", "\\cdot", "\\land", "\\lor", "\\oplus", "\\Rightarrow", "\\Leftrightarrow",
            "\\leftrightarrow", "\\in", "\\notin", "\\subset", "\\supseteq", "\\cup", "\\cap", "\\perp",
            "\\rangle", "\\}");
    static final Map<String, String> MIRRORED = Map.of("\\supseteq", "\\subset", "=", "=",
            "\\neq", "\\neq", "\\approx", "\\approx", "\\perp", "\\perp", "<", ">", ">", "<",
            "\\leq", "\\geq", "\\geq", "\\leq");
    static final Set<String> RELATIONS = Set.of("=", "<", ">", "\\leq", "\\geq", "\\neq", "\\approx",
            "\\in", "\\notin", "\\subset", "\\supseteq", "\\perp");
    // a bare arg like \sin 2x ends before these
    static final List<String> NAMED_FUNCTIONS = namedFunctions();

    private static List<String> namedFunctions() {
        List<String> names = new ArrayList<>(Arrays.asList("\\lim", "\\sum", "\\prod"));
        for (NamedFunction function : NamedFunction.values()) {
            NamedFunction.Syntax syntax = function.getSyntax();
            if (syntax == NamedFunction.Syntax.NAMED || syntax == NamedFunction.Syntax.LOG) {
                names.add(function.getLatex());
            }
        }
        return names;
    }

    static UncalculableReason meaninglessReason(String token) {
        switch (token) {
            case "\\int":
                return UncalculableReason.INTEGRAL;
            case "\\ldots":
                return UncalculableReason.ELLIPSIS;
            case "\\partial":
                return UncalculableReason.PARTIAL;
            default:
                return UncalculableReason.NOT_CALCULATION;
        }
    }

    static boolean isNumber(String token) {
        char first = token.charAt(0);
        return (first >= '0' && first <= '9') || first == '.';
    }

    // the one list of relation tokens, use case reads it too
    static boolean isRelation(String token) {
        return token != null && RELATIONS.contains(token);  // peek is null at the end
    }
}
