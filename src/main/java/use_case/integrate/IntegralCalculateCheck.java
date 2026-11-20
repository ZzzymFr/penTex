package use_case.integrate;

import entity.integral.IntegralParser;
import use_case.evaluate.CalculateCheckInputBoundary;

// so the popup actually offers Calculate on an integral, falls back to the normal check
public class IntegralCalculateCheck implements CalculateCheckInputBoundary {
    private final CalculateCheckInputBoundary everythingElse;

    public IntegralCalculateCheck(CalculateCheckInputBoundary everythingElse) {
        this.everythingElse = everythingElse;
    }

    @Override
    public boolean canCalculate(String latex) {
        return IntegralParser.isIntegral(latex) || everythingElse.canCalculate(latex);
    }
}
