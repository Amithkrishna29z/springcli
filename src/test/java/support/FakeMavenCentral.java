package support;

import service.MavenCentralService;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** An in-memory Maven Central: the versions published for each artifact; anything else is a 404. */
public final class FakeMavenCentral {

    private final Map<String, String> metadata = new HashMap<>();

    /** Publishes {@code versions} (oldest first) of {@code key}, a {@code groupId:artifactId}. */
    public FakeMavenCentral artifact(String key, String... versions) {
        String[] parts = key.split(":");
        StringBuilder xml = new StringBuilder("<metadata><groupId>" + parts[0] + "</groupId><artifactId>"
                + parts[1] + "</artifactId><versioning><latest>" + versions[versions.length - 1]
                + "</latest><versions>");
        for (String v : versions) {
            xml.append("<version>").append(v).append("</version>");
        }
        xml.append("</versions></versioning></metadata>");
        metadata.put("/maven2/" + parts[0].replace('.', '/') + "/" + parts[1] + "/maven-metadata.xml", xml.toString());
        return this;
    }

    @SuppressWarnings("unchecked")
    public MavenCentralService service() {
        HttpClient http = mock(HttpClient.class);
        try {
            doAnswer(invocation -> {
                String body = metadata.get(((HttpRequest) invocation.getArgument(0)).uri().getPath());
                HttpResponse<String> response = mock(HttpResponse.class);
                when(response.statusCode()).thenReturn(body == null ? 404 : 200);
                when(response.body()).thenReturn(body);
                return response;
            }).when(http).send(any(HttpRequest.class), any());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return new MavenCentralService(http, MavenCentralService.DEFAULT_REPO);
    }
}
