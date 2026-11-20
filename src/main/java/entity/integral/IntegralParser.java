package entity.integral;

import entity.MathException;
import entity.MathLexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// pulls \int_a^b f dx apart, only a lone integral counts (2\int... or \int + 1 dont)
public final class IntegralParser {
    private static final String INT = "\\int";
    private static final String DEFAULT_VARIABLE = "x";

    private IntegralParser() {
    }

    public static boolean isIntegral(String latex) {
        return parse(latex).isPresent();
    }

    public static Optional<Integral> parse(String latex) {
        if (latex == null) {
            return Optional.empty();
        }
        try {
            return parse(MathLexer.tokenize(latex));
        } catch (MathException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // trailing = is fine, thats just where the answer goes
    public static boolean endsWithEquals(String latex) {
        return latex != null && latex.trim().endsWith("=");
    }

    private static Optional<Integral> parse(List<String> raw) {
        List<String> tokens = foldUprightD(raw);
        if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("=")) {
            tokens.remove(tokens.size() - 1);
        }
        if (tokens.isEmpty() || !tokens.get(0).equals(INT) || tokens.lastIndexOf(INT) != 0) {
            return Optional.empty();  // nested / doubled integrals, newton only does one at a time
        }
        int[] at = {1};
        List<String> lower = null;
        List<String> upper = null;
        // bounds can come _ then ^ or the other way round
        for (int k = 0; k < 2 && at[0] < tokens.size(); k++) {
            String t = tokens.get(at[0]);
            if (t.equals("_") && lower == null) {
                at[0]++;
                lower = readGroup(tokens, at);
            } else if (t.equals("^") && upper == null) {
                at[0]++;
                upper = readGroup(tokens, at);
            }
        }
        if ((lower == null) != (upper == null) || (lower != null && (lower.isEmpty() || upper.isEmpty()))) {
            return Optional.empty();
        }
        List<String> rest = new ArrayList<>(tokens.subList(at[0], tokens.size()));
        if (rest.contains("=")) {
            return Optional.empty();
        }
        String variable = DEFAULT_VARIABLE;
        int n = rest.size();
        // dx at the end, model sometimes forgets it so x is the fallback
        if (n >= 2 && rest.get(n - 2).equals("d") && MathLexer.isLetter(rest.get(n - 1))) {
            variable = rest.get(n - 1);
            rest = rest.subList(0, n - 2);
        }
        if (rest.isEmpty() || !balanced(rest)) {
            return Optional.empty();
        }
        return Optional.of(new Integral(rest, variable, lower, upper));
    }

    // {..} or one token, at[0] moves past it
    private static List<String> readGroup(List<String> tokens, int[] at) {
        if (at[0] >= tokens.size()) {
            return new ArrayList<>();
        }
        if (!tokens.get(at[0]).equals("{")) {
            return new ArrayList<>(List.of(tokens.get(at[0]++)));
        }
        int depth = 0;
        int start = at[0] + 1;
        for (int i = at[0]; i < tokens.size(); i++) {
            String t = tokens.get(i);
            depth += t.equals("{") ? 1 : t.equals("}") ? -1 : 0;
            if (depth == 0) {
                at[0] = i + 1;
                return new ArrayList<>(tokens.subList(start, i));
            }
        }
        throw new IllegalArgumentException("unclosed bound");
    }

    // \mathrm{d}x / \operatorname{d}x -> d x
    private static List<String> foldUprightD(List<String> tokens) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            boolean upright = t.equals("\\mathrm") || t.equals("\\operatorname") || t.equals("\\text");
            if (upright && i + 3 < tokens.size() && tokens.get(i + 1).equals("{")
                    && tokens.get(i + 2).equals("d") && tokens.get(i + 3).equals("}")) {
                out.add("d");
                i += 3;
            } else {
                out.add(t);
            }
        }
        return out;
    }

    private static boolean balanced(List<String> tokens) {
        int braces = 0;
        int parens = 0;
        for (String t : tokens) {
            braces += t.equals("{") ? 1 : t.equals("}") ? -1 : 0;
            parens += t.equals("(") ? 1 : t.equals(")") ? -1 : 0;
            if (braces < 0 || parens < 0) {
                return false;
            }
        }
        return braces == 0 && parens == 0;
    }
}
