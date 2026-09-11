package service;

import exception.NetworkException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists the versions of an artifact published to Maven Central, read from the repository's
 * {@code maven-metadata.xml}. The {@link HttpClient} and repository URL are injectable for testing.
 */
public class MavenCentralService {

    public static final String DEFAULT_REPO = "https://repo1.maven.org/maven2";

    private final HttpClient http;
    private final String repoBase;

    public MavenCentralService(HttpClient http, String repoBase) {
        this.http = http;
        this.repoBase = HttpSupport.stripTrailingSlash(repoBase);
    }

    /**
     * @return every published version of {@code groupId:artifactId}, or an empty list if Maven Central
     * doesn't have the artifact
     * @throws NetworkException if Maven Central can't be reached or returns unexpected data
     */
    public List<String> versions(String groupId, String artifactId) {
        String url = repoBase + "/" + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
        HttpResponse<String> response = HttpSupport.sendAllowingNotFound(http, HttpSupport.request(url).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
                "Maven Central", "look up the versions of " + groupId + ":" + artifactId);
        return response.statusCode() == 404 ? List.of() : parse(response.body());
    }

    /** The {@code <versioning><versions><version>} entries of a {@code maven-metadata.xml}. */
    private static List<String> parse(String xml) {
        NodeList nodes;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            nodes = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                    .getElementsByTagName("version");
        } catch (Exception e) {
            throw new NetworkException("Received malformed data from Maven Central.", e);
        }
        List<String> versions = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getParentNode().getNodeName().equals("versions")) {
                versions.add(n.getTextContent().trim());
            }
        }
        return versions;
    }
}
