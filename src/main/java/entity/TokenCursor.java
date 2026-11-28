package entity;

import java.util.List;

/** A position in a token list, as the parser reads it: look ahead, step, or go back. */
final class TokenCursor {
    private final List<String> tokens;
    private int position;

    TokenCursor(List<String> tokens) {
        this.tokens = tokens;
    }

    int position() {
        return position;
    }

    /** Moves to a token index (to backtrack, or past a construct read by index). */
    void seek(int index) {
        position = index;
    }

    /** The token at an index, or null past the end. */
    String at(int index) {
        if (index < tokens.size()) {
            return tokens.get(index);
        }
        return null;
    }

    /** The current token, or null at the end. */
    String peek() {
        if (position < tokens.size()) {
            return tokens.get(position);
        }
        return null;
    }

    boolean peekIs(String expected) {
        return expected.equals(peek());
    }

    /** Takes the current token. */
    String next() {
        if (position >= tokens.size()) {
            throw new MathSyntaxException(SyntaxProblem.ENDS_EARLY);
        }
        String token = tokens.get(position);
        position++;
        return token;
    }

    void expect(String expected) {
        if (!peekIs(expected)) {
            throw new MathSyntaxException(SyntaxProblem.EXPECTED, expected);
        }
        next();
    }
}
