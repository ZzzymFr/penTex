package use_case.integrate;

// indefinite = F (+ C added by the presenter), definite = just the number
public class IntegrateOutputData {
    private final Antiderivative antiderivative;
    private final Double value;  // null when indefinite
    private final boolean afterEquals;  // user wrote = so answer sits right after it

    private IntegrateOutputData(Antiderivative antiderivative, Double value, boolean afterEquals) {
        this.antiderivative = antiderivative;
        this.value = value;
        this.afterEquals = afterEquals;
    }

    public static IntegrateOutputData indefinite(Antiderivative antiderivative, boolean afterEquals) {
        return new IntegrateOutputData(antiderivative, null, afterEquals);
    }

    public static IntegrateOutputData definite(Antiderivative antiderivative, double value, boolean afterEquals) {
        return new IntegrateOutputData(antiderivative, value, afterEquals);
    }

    public boolean isDefinite() {
        return value != null;
    }

    public double getValue() {
        return value == null ? Double.NaN : value;
    }

    public String getLatex() {
        return antiderivative.getLatex();
    }

    public String getText() {
        return antiderivative.getText();
    }

    public boolean isAfterEquals() {
        return afterEquals;
    }
}
