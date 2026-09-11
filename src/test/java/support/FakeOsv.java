package support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import service.VulnerabilityService;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** An in-memory OSV API: queued batch replies plus a table of vulnerability details. */
public final class FakeOsv {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Deque<String> batches = new ArrayDeque<>();
    private final Map<String, String> vulns = new HashMap<>();

    /**
     * Queues the reply to the next batch query: one argument per queried artifact, each a
     * comma-separated list of vulnerability ids ({@code ""} for none).
     */
    public FakeOsv batch(String... idsPerArtifact) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode results = root.putArray("results");
        for (String ids : idsPerArtifact) {
            ObjectNode result = results.addObject();
            if (!ids.isEmpty()) {
                ArrayNode list = result.putArray("vulns");
                for (String id : ids.split(",")) {
                    list.addObject().put("id", id);
                }
            }
        }
        batches.add(root.toString());
        return this;
    }

    /** Registers the details OSV returns for {@code id}; {@code severity} may be null. */
    public FakeOsv vuln(String id, String severity, String... aliases) {
        ObjectNode v = MAPPER.createObjectNode();
        v.put("id", id);
        v.put("summary", "Summary of " + id);
        ArrayNode list = v.putArray("aliases");
        for (String alias : aliases) {
            list.add(alias);
        }
        if (severity != null) {
            v.putObject("database_specific").put("severity", severity);
        }
        return rawVuln(id, v.toString());
    }

    /** Registers the exact JSON OSV returns for {@code id}. */
    public FakeOsv rawVuln(String id, String json) {
        vulns.put(id, json);
        return this;
    }

    /** A VulnerabilityService backed by this fake; any request it doesn't expect gets HTTP 404. */
    public VulnerabilityService service() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doAnswer(invocation -> {
            String path = ((HttpRequest) invocation.getArgument(0)).uri().getPath();
            String body = path.equals("/v1/querybatch") ? batches.poll()
                    : path.startsWith("/v1/vulns/") ? vulns.get(path.substring("/v1/vulns/".length()))
                    : null;
            return new Response(body == null ? 404 : 200, body);
        }).when(http).send(any(HttpRequest.class), any());
        return new VulnerabilityService(http, "https://api.osv.dev");
    }

    private record Response(int statusCode, String body) implements HttpResponse<String> {
        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return null;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
