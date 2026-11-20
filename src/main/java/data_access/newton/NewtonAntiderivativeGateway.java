package data_access.newton;

import entity.integral.Integral;
import use_case.integrate.Antiderivative;
import use_case.integrate.AntiderivativeGateway;
import use_case.integrate.IntegralFailure;
import use_case.integrate.IntegralServiceException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// finds F on the newton api (newton.vercel.app), free + no key. we only use /integrate, /area gives wrong numbers
public class NewtonAntiderivativeGateway implements AntiderivativeGateway {
    static final String ENDPOINT = "https://newton.vercel.app/api/v2/integrate/";
    private static final Pattern RESULT = Pattern.compile("\"result\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final NewtonHttp http;

    public NewtonAntiderivativeGateway(NewtonHttp http) {
        this.http = http;
    }

    @Override
    public Antiderivative find(Integral integral) throws IntegralServiceException {
        String query = NewtonQueryWriter.expression(integral.getIntegrand(), integral.getVariable());
        NewtonHttp.Response response;
        try {
            response = http.get(uri(query));
        } catch (IOException e) {
            throw new IntegralServiceException(IntegralFailure.OFFLINE, "couldnt reach newton", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IntegralServiceException(IntegralFailure.OFFLINE, "interrupted", e);
        }
        // 500 = {"error":"Unable to perform calculation"}, 504 = it timed out on a hard one
        if (response.status() != 200) {
            throw new IntegralServiceException(IntegralFailure.NO_ANSWER, "newton said " + response.status());
        }
        String result = result(response.body());
        if (result.isBlank() || result.contains("NaN") || result.contains("undefined")
                || result.contains("Infinity")) {
            throw new IntegralServiceException(IntegralFailure.NO_ANSWER, "newton gave back " + result);
        }
        try {
            return NewtonResultReader.read(result, integral.getVariable());
        } catch (RuntimeException e) {
            throw new IntegralServiceException(IntegralFailure.NO_ANSWER, "cant read " + result, e);
        }
    }

    // it goes in the path, so spaces have to be %20 not +
    static URI uri(String expression) {
        return URI.create(ENDPOINT + URLEncoder.encode(expression, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    // only need one string field, not worth a json library
    private static String result(String json) throws IntegralServiceException {
        Matcher m = RESULT.matcher(json == null ? "" : json);
        if (!m.find()) {
            throw new IntegralServiceException(IntegralFailure.NO_ANSWER, "no result in " + json);
        }
        return unescape(m.group(1));
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                out.append(c);
                continue;
            }
            char next = s.charAt(++i);
            if (next == 'u' && i + 4 < s.length()) {
                out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                i += 4;
            } else {
                out.append(next == 'n' ? ' ' : next == 't' ? ' ' : next);
            }
        }
        return out.toString();
    }
}
