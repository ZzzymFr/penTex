package data_access.newton;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// real one, plain java HttpClient so no new dependency
public class JdkNewtonHttp implements NewtonHttp {
    private static final Duration CONNECT = Duration.ofSeconds(5);
    private static final Duration WHOLE_REQUEST = Duration.ofSeconds(15);  // newton gives up ~10s anyway (504)

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public Response get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(WHOLE_REQUEST).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }
}
