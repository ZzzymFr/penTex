package data_access.newton;

import use_case.integrate.Antiderivative;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// newton's answer ("1/3 x^3", "-exp(x) + x exp(x)") -> tex for the canvas + plain text
final class NewtonResultReader {
    private static final Pattern TOKEN = Pattern.compile("\\d+(?:\\.\\d+)?|[A-Za-z_][A-Za-z_0-9]*|[-+*/^(),]");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*");
    private static final Set<String> TRIG = Set.of("sin", "cos", "tan", "cot", "sec", "csc", "sinh", "cosh", "tanh",
            "arcsin", "arccos", "arctan");
    private static final Map<String, String> ALIASES = Map.of("asin", "arcsin", "acos", "arccos", "atan", "arctan");
    private static final int SUM = 1;
    private static final int PRODUCT = 2;
    private static final int NEGATIVE = 3;
    private static final int POWER = 4;
    private static final int ATOM = 5;

    private final List<String> tokens;
    private final String variable;
    private int pos;

    private NewtonResultReader(List<String> tokens, String variable) {
        this.tokens = tokens;
        this.variable = variable;
    }

    // variable = what the user integrated over, newton gave it back as x
    static Antiderivative read(String result, String variable) {
        List<String> tokens = tokenize(result);
        NewtonResultReader reader = new NewtonResultReader(tokens, variable);
        Piece f = reader.sum();
        if (reader.pos != tokens.size()) {
            throw new IllegalArgumentException("leftover: " + tokens.get(reader.pos));
        }
        return new Antiderivative(f.latex, plain(result, variable));
    }

    // tex + how tightly it binds, bare = same thing w/o the outer brackets
    private static final class Piece {
        final String latex;
        final int binding;
        final String bare;

        Piece(String latex, int binding) {
            this(latex, binding, latex);
        }

        Piece(String latex, int binding, String bare) {
            this.latex = latex;
            this.binding = binding;
            this.bare = bare;
        }
    }

    private Piece sum() {
        Piece left = product();
        StringBuilder out = new StringBuilder(left.latex);
        boolean many = false;
        while (peek("+") || peek("-")) {
            String op = tokens.get(pos++);
            out.append(' ').append(op).append(' ').append(product().latex);
            many = true;
        }
        return many ? new Piece(out.toString(), SUM) : left;
    }

    private Piece product() {
        Piece left = unary();
        while (peek("*") || peek("/") || startsFactor()) {
            if (peek("/")) {
                pos++;
                Piece right = unary();
                left = new Piece("\\frac{" + left.bare + "}{" + right.bare + "}", ATOM);
                continue;
            }
            if (peek("*")) {
                pos++;
            }
            Piece right = unary();
            String r = right.binding <= NEGATIVE ? brackets(right.latex) : right.latex;
            // 2 3 would read as 23, needs a dot
            String gap = endsWithDigit(left.latex) && Character.isDigit(r.charAt(0)) ? " \\cdot " : " ";
            String l = left.binding == SUM ? brackets(left.latex) : left.latex;
            left = new Piece(l + gap + r, PRODUCT);
        }
        return left;
    }

    private Piece unary() {
        if (peek("-")) {
            pos++;
            Piece inner = unary();
            return new Piece("-" + (inner.binding == SUM ? brackets(inner.latex) : inner.latex), NEGATIVE);
        }
        return power();
    }

    private Piece power() {
        Piece base = atom();
        if (!peek("^")) {
            return base;
        }
        pos++;
        Piece exponent = unary();
        String b = base.binding < ATOM ? brackets(base.latex) : base.latex;
        return new Piece(b + "^{" + exponent.bare + "}", POWER);
    }

    private Piece atom() {
        if (pos >= tokens.size()) {
            throw new IllegalArgumentException("ran out");
        }
        String t = tokens.get(pos++);
        if (t.equals("(")) {
            Piece inner = sum();
            expect(")");
            return new Piece(brackets(inner.latex), ATOM, inner.latex);
        }
        if (Character.isDigit(t.charAt(0))) {
            return new Piece(t, ATOM);
        }
        if (!NAME.matcher(t).matches()) {
            throw new IllegalArgumentException("unexpected " + t);
        }
        if (peek("(")) {
            pos++;
            Piece argument = sum();
            expect(")");
            return call(ALIASES.getOrDefault(t, t), argument.latex);
        }
        return new Piece(name(t), ATOM);
    }

    // calls count as POWER so sin(x)^2 gets wrapped -> (sin(x))^2, no mixup w/ sin(x^2)
    private Piece call(String name, String argument) {
        switch (name) {
            case "log":
                return new Piece("\\ln" + brackets(argument), POWER);
            case "exp":
                return new Piece("e^{" + argument + "}", POWER);
            case "sqrt":
                return new Piece("\\sqrt{" + argument + "}", POWER);
            case "abs":
                return new Piece("\\left|" + argument + "\\right|", POWER);
            default:
                String head = TRIG.contains(name) ? "\\" + name : "\\operatorname{" + name + "}";
                return new Piece(head + brackets(argument), POWER);
        }
    }

    // swap x back to the users variable
    private String name(String t) {
        if (t.equals("x")) {
            return variable;
        }
        if (t.equals(variable)) {
            return "x";
        }
        if (t.equals("pi")) {
            return "\\pi ";
        }
        return t.length() == 1 ? t : "\\mathrm{" + t + "}";
    }

    private boolean startsFactor() {
        if (pos >= tokens.size()) {
            return false;
        }
        String t = tokens.get(pos);
        return t.equals("(") || Character.isLetterOrDigit(t.charAt(0)) || t.charAt(0) == '_';
    }

    private boolean peek(String token) {
        return pos < tokens.size() && tokens.get(pos).equals(token);
    }

    private void expect(String token) {
        if (!peek(token)) {
            throw new IllegalArgumentException("expected " + token);
        }
        pos++;
    }

    // \left( \right) pads itself in jlatexmath (cos (t)), so only use it when a fraction needs tall ones
    private static String brackets(String latex) {
        return latex.contains("\\frac") ? "\\left(" + latex + "\\right)" : "(" + latex + ")";
    }

    private static boolean endsWithDigit(String s) {
        return !s.isEmpty() && Character.isDigit(s.charAt(s.length() - 1));
    }

    private static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = TOKEN.matcher(text);
        int at = 0;
        while (m.find()) {
            if (!text.substring(at, m.start()).isBlank()) {
                throw new IllegalArgumentException("cant read " + text.substring(at, m.start()));
            }
            out.add(m.group());
            at = m.end();
        }
        if (!text.substring(at).isBlank() || out.isEmpty()) {
            throw new IllegalArgumentException("cant read " + text);
        }
        return out;
    }

    // the text version: same string, x swapped back, log -> ln so it reads right
    private static String plain(String result, String variable) {
        Matcher m = NAME.matcher(result);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String t = m.group();
            String swapped = t.equals("x") ? variable : t.equals(variable) ? "x" : t.equals("log") ? "ln" : t;
            m.appendReplacement(out, Matcher.quoteReplacement(swapped));
        }
        m.appendTail(out);
        return out.toString();
    }
}
