package use_case.integrate;

// the recognized latex, = at the end or not
public class IntegrateInputData {
    private final String latex;

    public IntegrateInputData(String latex) {
        this.latex = latex;
    }

    public String getLatex() {
        return latex;
    }
}
