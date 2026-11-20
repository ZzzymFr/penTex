package use_case.integrate;

// gateway blew up, failure says how
public class IntegralServiceException extends Exception {
    private final IntegralFailure failure;

    public IntegralServiceException(IntegralFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public IntegralServiceException(IntegralFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public IntegralFailure getFailure() {
        return failure;
    }
}
