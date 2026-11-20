package use_case.integrate;

public interface IntegrateOutputBoundary {
    void prepareSuccessView(IntegrateOutputData answer);

    void prepareFailView(IntegralFailure why);
}
