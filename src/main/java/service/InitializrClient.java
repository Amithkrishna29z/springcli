package service;

import exception.NetworkException;
import model.ProjectRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.StringJoiner;

public class InitializrClient {

    public static final String DEFAULT_BASE_URL = "https://start.spring.io";

    private static final String USER_AGENT = "springcli/1.0.0";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final String baseUrl;

    public InitializrClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), DEFAULT_BASE_URL);
    }

    public InitializrClient(HttpClient httpClient, String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String fetchMetadata() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/metadata/client"))
                .header("User-Agent", USER_AGENT)

                .header("Accept", "application/vnd.initializr.v2.2+json")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response.statusCode(), "fetch metadata");
        return response.body();
    }

    public byte[] downloadStarter(ProjectRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/starter.zip?" + toQuery(request)))
                .header("User-Agent", USER_AGENT)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<byte[]> response = send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        ensureSuccess(response.statusCode(), "download starter");
        return response.body();
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

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return httpClient.send(request, handler);
        } catch (IOException e) {
            throw new NetworkException(
                    "Could not reach Spring Initializr at " + baseUrl
                            + ". Check your internet connection and try again.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetworkException("Request interrupted while contacting Spring Initializr.", e);
        }
    }

    private static void ensureSuccess(int status, String action) {
        if (status < 200 || status >= 300) {
            throw new NetworkException(
                    "Spring Initializr returned HTTP " + status + " while trying to " + action + ".");
        }
    }
}
