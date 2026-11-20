package data_access.newton;

import java.io.IOException;
import java.net.URI;

// just GET, so tests can fake newton w/o the internet
public interface NewtonHttp {
    Response get(URI uri) throws IOException, InterruptedException;

    record Response(int status, String body) {
    }
}
