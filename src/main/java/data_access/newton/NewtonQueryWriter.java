package data_access.newton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// lexer tokens -> plain math newton can read, like "1/(x) * sin(x)^(2)". newton's log is ln
final class NewtonQueryWriter {
    private static final Map<String, String> FUNCTIONS = Map.ofEntries(
            Map.entry("\\sin", "sin"), Map.entry("\\cos", "cos"), Map.entry("\\tan", "tan"),
            Map.entry("\\cot", "cot"), Map.entry("\\sec", "sec"), Map.entry("\\csc", "csc"),
            Map.entry("\\arcsin", "arcsin"), Map.entry("\\arccos", "arccos"), Map.entry("\\arctan", "arctan"),
            Map.entry("\\sinh", "sinh"), Map.entry("\\cosh", "cosh"), Map.entry("\\tanh", "tanh"),
            Map.entry("\\ln", "log"), Map.entry("\\exp", "exp"));
    private static final Map<String, String> SYMBOLS = Map.of("\\cdot", "*", "\\times", "*", "\\pi", "pi");

    private NewtonQueryWriter() {
    }

    // newton always integrates in x, so swap the real variable w/ x on the way in (and back out after)
    static String expression(List<String> tokens, String variable) {
        List<String> swapped = new ArrayList<>();
        for (String t : tokens) {
            swapped.add(t.equals(variable) ? "x" : t.equals("x") ? variable : t);
        }
        return new Writer(swapped).until(null).trim();
    }

    // tiny recursive walk, pos is shared
    private static final class Writer {
        private final List<String> tokens;
        private int pos;

        Writer(List<String> tokens) {
            this.tokens = tokens;
        }

        // reads till close (eaten) or the end
        String until(String close) {
            StringBuilder out = new StringBuilder();
            while (pos < tokens.size()) {
                if (tokens.get(pos).equals(close)) {
                    pos++;
                    break;
                }
                append(out, piece());
            }
            return out.toString();
        }

        private String piece() {
            String t = tokens.get(pos++);
            switch (t) {
                case "{":
                    return "(" + until("}") + ")";
                case "^":
                    return "^(" + group() + ")";
                case "\\frac":
                    return "((" + group() + ")/(" + group() + "))";
                case "\\sqrt":
                    if (peek("[")) {
                        pos++;
                        String n = until("]");
                        return "((" + group() + ")^(1/(" + n + ")))";
                    }
                    return "sqrt(" + group() + ")";
                case "\\log":
                    return log();
                default:
                    if (FUNCTIONS.containsKey(t)) {
                        return function(FUNCTIONS.get(t));
                    }
                    if (SYMBOLS.containsKey(t)) {
                        return SYMBOLS.get(t);
                    }
                    return t.startsWith("\\") ? t.substring(1) : t;
            }
        }

        // our \log is base 10 (or _b), newton only has ln so divide it out
        private String log() {
            String base = "10";
            if (peek("_")) {
                pos++;
                base = group();
            }
            String power = power();
            return "(log(" + argument() + ")/log(" + base + "))" + power;
        }

        // sin^2 x -> sin(x)^(2)
        private String function(String name) {
            String power = power();
            return name + "(" + argument() + ")" + power;
        }

        private String power() {
            if (!peek("^")) {
                return "";
            }
            pos++;
            return "^(" + group() + ")";
        }

        private String argument() {
            return peek("(") ? parens() : factor();
        }

        // ( ... ) w/ the brackets dropped, nested ones kept
        private String parens() {
            pos++;
            StringBuilder out = new StringBuilder();
            int depth = 1;
            while (pos < tokens.size()) {
                String t = tokens.get(pos);
                depth += t.equals("(") ? 1 : t.equals(")") ? -1 : 0;
                if (depth == 0) {
                    pos++;
                    break;
                }
                append(out, piece());
            }
            return out.toString();
        }

        // one thing + its power, for unbracketed function args
        private String factor() {
            if (pos >= tokens.size()) {
                return "";
            }
            String value = piece();
            while (peek("^")) {
                pos++;
                value += "^(" + group() + ")";
            }
            return value;
        }

        private String group() {
            if (peek("{")) {
                pos++;
                return until("}");
            }
            return pos < tokens.size() ? piece() : "";
        }

        private boolean peek(String token) {
            return pos < tokens.size() && tokens.get(pos).equals(token);
        }

        // ^ sticks to whatever came before it, everything else gets a space (= times)
        private static void append(StringBuilder out, String piece) {
            if (!piece.startsWith("^") && out.length() > 0) {
                out.append(' ');
            }
            out.append(piece);
        }
    }
}
