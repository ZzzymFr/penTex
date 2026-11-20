package entity.integral;

import java.util.Collections;
import java.util.List;

// one ∫ f dx, bounds both null when its indefinite. tokens are MathLexer tokens
public final class Integral {
    private final List<String> integrand;
    private final String variable;
    private final List<String> lower;
    private final List<String> upper;

    public Integral(List<String> integrand, String variable, List<String> lower, List<String> upper) {
        if (integrand == null || integrand.isEmpty()) {
            throw new IllegalArgumentException("nothing to integrate");
        }
        if ((lower == null) != (upper == null)) {
            throw new IllegalArgumentException("need both bounds or neither");
        }
        this.integrand = List.copyOf(integrand);
        this.variable = variable;
        this.lower = lower == null ? null : List.copyOf(lower);
        this.upper = upper == null ? null : List.copyOf(upper);
    }

    public List<String> getIntegrand() {
        return integrand;
    }

    public String getVariable() {
        return variable;
    }

    public boolean isDefinite() {
        return lower != null;
    }

    // empty list when indefinite so callers dont have to null check
    public List<String> getLower() {
        return lower == null ? Collections.emptyList() : lower;
    }

    public List<String> getUpper() {
        return upper == null ? Collections.emptyList() : upper;
    }
}
