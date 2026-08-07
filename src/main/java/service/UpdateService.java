package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import exception.NetworkException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks the project's GitHub Releases for a newer version than the one running. Only performs the
 * read-only "is there a newer release?" query; downloading/launching the installer is handled by the
 * command layer. The {@link HttpClient}, API base URL and repo are injectable for testing.
 */
public class UpdateService {

    public static final String DEFAULT_API = "https://api.github.com";
    public static final String DEFAULT_REPO = "Amithkrishna29z/springcli";

    private final HttpClient http;
    private final String apiBase;
    private final String repo;
    private final String currentVersion;
    private final ObjectMapper mapper = new ObjectMapper();

    public UpdateService(String currentVersion) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                DEFAULT_API, DEFAULT_REPO, currentVersion);
    }

    public UpdateService(HttpClient http, String apiBase, String repo, String currentVersion) {
        this.http = http;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.repo = repo;
        this.currentVersion = currentVersion;
    }

    /** @return the running version, without any leading {@code v}. */
    public String currentVersion() {
        return normalize(currentVersion);
    }

    /**
     * @return the latest published release version (without leading {@code v})
     * @throws NetworkException if GitHub cannot be reached or returns unexpected data
     */
    public String latestVersion() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + "/repos/" + repo + "/releases/latest"))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "springcli/" + currentVersion)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new NetworkException("Could not check for updates (network error).", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NetworkException("Update check was interrupted.", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new NetworkException("GitHub returned HTTP " + response.statusCode() + " while checking for updates.");
        }
        try {
            JsonNode tag = mapper.readTree(response.body()).path("tag_name");
            if (tag.isMissingNode() || tag.asText("").isBlank()) {
                throw new NetworkException("No release information found for " + repo + ".");
            }
            return normalize(tag.asText());
        } catch (IOException e) {
            throw new NetworkException("Received malformed release data from GitHub.", e);
        }
    }

    public String releaseUrl() {
        return "https://github.com/" + repo + "/releases/latest";
    }

    public String downloadUrl(String assetName) {
        return "https://github.com/" + repo + "/releases/latest/download/" + assetName;
    }

    /** @return {@code true} if {@code candidate} is a strictly higher version than {@code current}. */
    public static boolean isNewer(String candidate, String current) {
        int[] a = parse(candidate);
        int[] b = parse(current);
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    static String normalize(String v) {
        if (v == null) {
            return "";
        }
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        return v;
    }

    /** Parses the leading numeric dotted components (e.g. "1.2.0-rc1" -> [1,2,0]). */
    private static int[] parse(String v) {
        List<Integer> nums = new ArrayList<>();
        for (String part : normalize(v).split("[.\\-+]")) {
            try {
                nums.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                break;
            }
        }
        int[] out = new int[nums.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = nums.get(i);
        }
        return out;
    }
}
