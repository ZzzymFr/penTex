package use_case.integrate;

// F w/o the + C, as tex (canvas) and plain text. doesnt care which api made it
public class Antiderivative {
    private final String latex;
    private final String text;

    public Antiderivative(String latex, String text) {
        if (latex == null || latex.isBlank()) {
            throw new IllegalArgumentException("empty antiderivative");
        }
        this.latex = latex;
        this.text = text == null || text.isBlank() ? latex : text;
    }

    public String getLatex() {
        return latex;
    }

    public String getText() {
        return text;
    }
}
