package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.BuildInfo;
import exception.NetworkException;
import exception.SpringCliException;
import util.Versions;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks the project's GitHub Releases for a newer version than the one running, and downloads
 * release installers. Running a downloaded installer is left to an {@link Installer}. The
 * {@link HttpClient}, API base URL and repo are injectable for testing.
 */
public class UpdateService {

    public static final String DEFAULT_API = "https://api.github.com";
    public static final String DEFAULT_REPO = "Amithkrishna29z/springcli";

    private final HttpClient http;
    private final String apiBase;
    private final String repo;
    private final String currentVersion;
    private final ObjectMapper mapper = new ObjectMapper();

    public UpdateService(HttpClient http, String apiBase, String repo, String currentVersion) {
        this.http = http;
        this.apiBase = HttpSupport.stripTrailingSlash(apiBase);
        this.repo = repo;
        this.currentVersion = currentVersion;
    }

    /** @return the running version, without any leading {@code v}. */
    public String currentVersion() {
        return Versions.normalize(currentVersion);
    }

    /**
     * @return the latest published release version (without leading {@code v})
     * @throws NetworkException if GitHub cannot be reached or returns unexpected data
     */
    public String latestVersion() {
        HttpRequest request = HttpSupport.request(apiBase + "/repos/" + repo + "/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();
        HttpResponse<String> response = HttpSupport.send(http, request, HttpResponse.BodyHandlers.ofString(),
                "GitHub", "check for updates");
        try {
            JsonNode tag = mapper.readTree(response.body()).path("tag_name");
            if (tag.isMissingNode() || tag.asText("").isBlank()) {
                throw new NetworkException("No release information found for " + repo + ".");
            }
            return Versions.normalize(tag.asText());
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

    /**
     * Downloads the latest release's {@code assetName} into a new temporary directory.
     *
     * @return the downloaded file
     * @throws SpringCliException if the download fails
     */
    public Path downloadInstaller(String assetName) {
        Path file;
        try {
            file = Files.createTempDirectory("springcli-update").resolve(assetName);
        } catch (IOException e) {
            throw new SpringCliException("Could not create a download directory: " + e.getMessage(), e);
        }
        // Release downloads redirect to GitHub's CDN, which the shared API client doesn't follow.
        HttpClient downloader = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(HttpSupport.CONNECT_TIMEOUT)
                .build();
        // No request timeout: an installer can take much longer to download than an API call.
        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl(assetName)))
                .header("User-Agent", BuildInfo.USER_AGENT)
                .GET()
                .build();
        HttpSupport.send(downloader, request, HttpResponse.BodyHandlers.ofFile(file),
                "GitHub", "download " + assetName);
        return file;
    }
}
