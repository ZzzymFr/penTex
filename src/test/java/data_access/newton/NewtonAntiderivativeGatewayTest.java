package data_access.newton;

import entity.integral.IntegralParser;
import org.junit.jupiter.api.Test;
import use_case.integrate.Antiderivative;
import use_case.integrate.IntegralFailure;
import use_case.integrate.IntegralServiceException;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NewtonAntiderivativeGatewayTest {
    // newton's real answers, copied from the api
    private static String json(String expression, String result) {
        return "{\"operation\":\"integrate\",\"expression\":\"" + expression + "\",\"result\":\"" + result + "\"}";
    }

    private static Antiderivative find(String latex, String newtonSays, List<URI> seen) throws Exception {
        return new NewtonAntiderivativeGateway(uri -> {
            seen.add(uri);
            return new NewtonHttp.Response(200, json("whatever", newtonSays));
        }).find(IntegralParser.parse(latex).orElseThrow());
    }

    private static Antiderivative find(String latex, String newtonSays) throws Exception {
        return find(latex, newtonSays, new ArrayList<>());
    }

    @Test
    void callsTheIntegrateEndpoint() throws Exception {
        List<URI> seen = new ArrayList<>();
        find("\\int \\cos(2x) dx", "1/2 sin(2 x)", seen);
        assertEquals("https://newton.vercel.app/api/v2/integrate/cos%282%20x%29", seen.get(0).toString());
        seen.clear();
        find("\\int \\frac{1}{x} dx", "log(x)", seen);
        assertEquals("https://newton.vercel.app/api/v2/integrate/%28%281%29%2F%28x%29%29", seen.get(0).toString());
    }

    @Test
    void resultsToTex() throws Exception {
        assertEquals("\\frac{1}{3} x^{3}", find("\\int x^2 dx", "1/3 x^3").getLatex());
        assertEquals("-\\cos\\left(x\\right)", find("\\int \\sin x dx", "-cos(x)").getLatex());
        assertEquals("\\ln\\left(x\\right)", find("\\int \\frac{1}{x} dx", "log(x)").getLatex());
        assertEquals("-e^{x} + x e^{x}", find("\\int x e^x dx", "-exp(x) + x exp(x)").getLatex());
        assertEquals("\\frac{1}{2} \\sin\\left(2 x\\right)", find("\\int \\cos(2x) dx", "1/2 sin(2 x)").getLatex());
        assertEquals("\\frac{2^{x}}{\\ln\\left(2\\right)}", find("\\int 2^x dx", "2^x / log(2)").getLatex());
        assertEquals("\\frac{3}{4} x^{\\frac{4}{3}}", find("\\int \\sqrt[3]{x} dx", "3/4 x^(4/3)").getLatex());
        assertEquals("\\frac{1}{2} x - \\frac{1}{4} \\sin\\left(2 x\\right)",
                find("\\int \\sin^2 x dx", "1/2 x - 1/4 sin(2 x)").getLatex());
        assertEquals("\\left(\\sin\\left(x\\right)\\right)^{2}", find("\\int x dx", "sin(x)^2").getLatex());
        assertEquals("2 \\cdot 3", find("\\int x dx", "2 3").getLatex());
    }

    @Test
    void plainTextSaysLn() throws Exception {
        assertEquals("-x + x ln(x)", find("\\int \\ln x dx", "-x + x log(x)").getText());
    }

    @Test
    void variableComesBack() throws Exception {
        Antiderivative f = find("\\int \\sin t dt", "-cos(x)");
        assertEquals("-\\cos\\left(t\\right)", f.getLatex());
        assertEquals("-cos(t)", f.getText());
        // ∫ x t dt went over as "t x", newton says 1/2 x^2 t, which is really 1/2 t^2 x
        assertEquals("\\frac{1}{2} t^{2} x", find("\\int x t dt", "1/2 x^2 t").getLatex());
    }

    @Test
    void failures() {
        assertFails(IntegralFailure.OFFLINE, uri -> {
            throw new IOException("no wifi");
        });
        assertFails(IntegralFailure.NO_ANSWER,
                uri -> new NewtonHttp.Response(500, "{\"error\":\"Unable to perform calculation\"}"));
        assertFails(IntegralFailure.NO_ANSWER,
                uri -> new NewtonHttp.Response(504, "An error occurred with your deployment"));
        assertFails(IntegralFailure.NO_ANSWER, uri -> new NewtonHttp.Response(200, json("x", "NaN")));
        assertFails(IntegralFailure.NO_ANSWER, uri -> new NewtonHttp.Response(200, "{}"));
        assertFails(IntegralFailure.NO_ANSWER, uri -> new NewtonHttp.Response(200, json("x", "1/2 x^2 $$")));
    }

    private static void assertFails(IntegralFailure expected, NewtonHttp http) {
        NewtonAntiderivativeGateway gateway = new NewtonAntiderivativeGateway(http);
        IntegralServiceException e = assertThrows(IntegralServiceException.class,
                () -> gateway.find(IntegralParser.parse("\\int x dx").orElseThrow()));
        assertEquals(expected, e.getFailure());
    }
}
