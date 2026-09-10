package service;

import config.BuildInfo;
import exception.NetworkException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP plumbing shared by the API clients ({@link InitializrClient}, {@link VulnerabilityService},
 * {@link UpdateService}): one client configuration, one request template, and one place that turns
 * transport failures and non-2xx responses into {@link NetworkException}s.
 */
public final class HttpSupport {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private HttpSupport() {
    }

    /** @return a client with springcli's standard connect timeout; safe to share between services. */
    public static HttpClient newClient() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /** @return a request to {@code uri} carrying springcli's User-Agent and the standard timeout. */
    static HttpRequest.Builder request(String uri) {
        return HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("User-Agent", BuildInfo.USER_AGENT)
                .timeout(REQUEST_TIMEOUT);
    }

    static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Sends {@code request} and returns the response if its status is 2xx.
     *
     * @param service who is being called, for error messages (e.g. "GitHub")
     * @param action  what the call is for, completing "while trying to ..." (e.g. "check for updates")
     * @throws NetworkException if the service can't be reached, the call is interrupted, or the
     *                          status isn't 2xx
     */
    static <T> HttpResponse<T> send(HttpClient http, HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                    String service, String action) {
        HttpResponse<T> response;
        try {
            response = http.send(request, handler);
        } catch (IOException e) {
            throw new NetworkException("Could not reach " + service + " to " + action
                    + ". Check your internet connection and try again.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetworkException("Interrupted while trying to " + action + ".", e);
        }
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new NetworkException(service + " returned HTTP " + status + " while trying to " + action + ".");
        }
        return response;
    }
}
