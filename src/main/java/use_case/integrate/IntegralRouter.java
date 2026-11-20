package use_case.integrate;

import entity.integral.IntegralParser;
import use_case.evaluate.EvaluateInputBoundary;
import use_case.evaluate.EvaluateInputData;

// sits in front of evaluate: integrals go to integrate, the rest go through untouched
public class IntegralRouter implements EvaluateInputBoundary {
    private final IntegrateInputBoundary integrate;
    private final EvaluateInputBoundary everythingElse;

    public IntegralRouter(IntegrateInputBoundary integrate, EvaluateInputBoundary everythingElse) {
        this.integrate = integrate;
        this.everythingElse = everythingElse;
    }

    @Override
    public void execute(EvaluateInputData inputData) {
        String latex = inputData.getExpression();
        if (IntegralParser.isIntegral(latex)) {
            integrate.execute(new IntegrateInputData(latex));
        } else {
            everythingElse.execute(inputData);
        }
    }
}
