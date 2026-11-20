package use_case.integrate;

import entity.integral.Integral;

// whatever finds F for us (newton api for now). blocks, so never call it on the EDT
public interface AntiderivativeGateway {
    Antiderivative find(Integral integral) throws IntegralServiceException;
}
