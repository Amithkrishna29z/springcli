package service;

import model.ProjectRequest;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

public class InitializrClient {

    public static final String DEFAULT_BASE_URL = "https://start.spring.io";

    private final HttpClient httpClient;
    private final String baseUrl;

    public InitializrClient(HttpClient httpClient, String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = HttpSupport.stripTrailingSlash(baseUrl);
    }

    public String fetchMetadata() {
        HttpRequest request = HttpSupport.request(baseUrl + "/metadata/client")
                .header("Accept", "application/vnd.initializr.v2.2+json")
                .GET()
                .build();
        return send(request, HttpResponse.BodyHandlers.ofString(), "fetch metadata");
    }

    /**
     * Fetches a reference {@code pom.xml} for the given request. Used to resolve the exact Maven
     * coordinates (groupId/artifactId/scope) of dependencies, which the client metadata omits.
     */
    public String fetchPom(ProjectRequest request) {
        return send(get("/pom.xml?" + toQuery(request)), HttpResponse.BodyHandlers.ofString(), "fetch pom.xml");
    }

    public byte[] downloadStarter(ProjectRequest request) {
        return send(get("/starter.zip?" + toQuery(request)), HttpResponse.BodyHandlers.ofByteArray(),
                "download starter");
    }

    String toQuery(ProjectRequest r) {
        StringJoiner q = new StringJoiner("&");
        q.add(param("type", r.type()));
        q.add(param("language", r.language()));
        q.add(param("bootVersion", r.bootVersion()));
        q.add(param("groupId", r.groupId()));
        q.add(param("artifactId", r.artifactId()));
        q.add(param("name", r.name()));
        q.add(param("description", r.description()));
        q.add(param("packageName", r.packageName()));
        q.add(param("packaging", r.packaging()));
        q.add(param("javaVersion", r.javaVersion()));
        if (!r.dependencies().isEmpty()) {
            q.add(param("dependencies", String.join(",", r.dependencies())));
        }
        return q.toString();
    }

    private static String param(String key, String value) {
        return key + "=" + URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private HttpRequest get(String path) {
        return HttpSupport.request(baseUrl + path).GET().build();
    }

    private <T> T send(HttpRequest request, HttpResponse.BodyHandler<T> handler, String action) {
        return HttpSupport.send(httpClient, request, handler, "Spring Initializr at " + baseUrl, action).body();
    }
}
