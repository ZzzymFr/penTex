package use_case.integrate;

import entity.MathException;
import entity.UndefinedException;
import entity.integral.DefiniteIntegral;
import entity.integral.Integral;
import entity.integral.IntegralParser;

import java.util.Optional;

// parse -> api finds F -> (definite) F(b)-F(a) ourselves -> presenter
public class IntegrateInteractor implements IntegrateInputBoundary {
    private final AntiderivativeGateway antiderivatives;
    private final IntegrateOutputBoundary presenter;

    public IntegrateInteractor(AntiderivativeGateway antiderivatives, IntegrateOutputBoundary presenter) {
        this.antiderivatives = antiderivatives;
        this.presenter = presenter;
    }

    @Override
    public void execute(IntegrateInputData inputData) {
        String latex = inputData.getLatex();
        Optional<Integral> parsed = IntegralParser.parse(latex);
        if (parsed.isEmpty()) {
            presenter.prepareFailView(IntegralFailure.NOT_AN_INTEGRAL);
            return;
        }
        Integral integral = parsed.get();
        boolean afterEquals = IntegralParser.endsWithEquals(latex);
        try {
            if (integral.isDefinite()) {
                checkBounds(integral);  // before the network call, no point asking if ∞ is a bound
            }
            Antiderivative f = antiderivatives.find(integral);
            if (!integral.isDefinite()) {
                presenter.prepareSuccessView(IntegrateOutputData.indefinite(f, afterEquals));
                return;
            }
            double value = DefiniteIntegral.value(integral, f.getLatex());
            presenter.prepareSuccessView(IntegrateOutputData.definite(f, value, afterEquals));
        } catch (IntegralServiceException e) {
            presenter.prepareFailView(e.getFailure());
        } catch (UndefinedException e) {
            presenter.prepareFailView(IntegralFailure.UNDEFINED);
        } catch (RuntimeException | StackOverflowError e) {
            // F came back in a shape our calculator cant evaluate, or the gateway did something weird
            presenter.prepareFailView(IntegralFailure.NO_ANSWER);
        }
    }

    private void checkBounds(Integral integral) throws IntegralServiceException {
        try {
            DefiniteIntegral.bound(integral.getLower());
            DefiniteIntegral.bound(integral.getUpper());
        } catch (MathException e) {
            throw new IntegralServiceException(IntegralFailure.IMPROPER, "bound isnt a finite number", e);
        }
    }
}
