package audit;

import model.Artifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import util.ProcessUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class MavenArtifactResolverTest {

    private static final String WEB = "org.springframework.boot:spring-boot-starter-web";

    /** Real-world shaped `mvn dependency:tree` output, with a second module at the end. */
    private static final String TREE = """
            com.example:demo:jar:0.0.1-SNAPSHOT
            +- org.springframework.boot:spring-boot-starter-web:jar:3.3.2:compile
            |  +- org.springframework.boot:spring-boot-starter-tomcat:jar:3.3.2:compile
            |  |  \\- org.apache.tomcat.embed:tomcat-embed-core:jar:10.1.26:compile
            |  \\- org.springframework:spring-web:jar:6.1.11:compile -- module spring.web [auto]
            +- org.postgresql:postgresql:jar:42.7.3:runtime
            +- org.projectlombok:lombok:jar:1.18.34:compile (optional)
            +- io.netty:netty-resolver-dns-native-macos:jar:osx-x86_64:4.1.111.Final:runtime
            \\- org.springframework.boot:spring-boot-starter-test:jar:3.3.2:test
               \\- org.assertj:assertj-core:jar:3.25.3:test

            com.example:other:jar:0.0.1-SNAPSHOT
            \\- org.postgresql:postgresql:jar:42.7.3:runtime
            """;

    private static Artifact find(List<Artifact> artifacts, String artifactId) {
        return artifacts.stream().filter(a -> a.artifactId().equals(artifactId)).findFirst().orElseThrow();
    }

    @Test
    void parsesDirectAndTransitiveArtifactsWithTheirOrigin() {
        List<Artifact> artifacts = MavenArtifactResolver.parseTree(TREE);

        Artifact web = find(artifacts, "spring-boot-starter-web");
        assertNull(web.via(), "a direct dependency has no 'via'");
        assertEquals("3.3.2", web.version());

        Artifact tomcat = find(artifacts, "tomcat-embed-core");
        assertEquals("10.1.26", tomcat.version());
        assertEquals(WEB, tomcat.via(), "deeply nested artifacts point at their direct dependency");
        assertEquals(WEB, find(artifacts, "spring-web").via());
        assertEquals("runtime", find(artifacts, "postgresql").scope());
    }

    @Test
    void dropsTestScopeAndKeepsEachArtifactOnce() {
        List<Artifact> artifacts = MavenArtifactResolver.parseTree(TREE);

        assertFalse(artifacts.stream().anyMatch(a -> a.scope().equals("test")));
        assertEquals(1, artifacts.stream().filter(a -> a.artifactId().equals("postgresql")).count());
        assertEquals(7, artifacts.size());
    }

    @Test
    void handlesClassifiersAndTrailingAnnotations() {
        List<Artifact> artifacts = MavenArtifactResolver.parseTree(TREE);

        assertEquals("4.1.111.Final", find(artifacts, "netty-resolver-dns-native-macos").version());
        assertEquals("1.18.34", find(artifacts, "lombok").version());
        assertEquals("6.1.11", find(artifacts, "spring-web").version());
    }

    @Test
    void prefersTheProjectsMavenWrapper(@TempDir Path dir) throws Exception {
        assertEquals(List.of("mvn"), MavenArtifactResolver.mavenLauncher(dir));

        Files.createFile(dir.resolve("mvnw"));
        Files.createFile(dir.resolve("mvnw.cmd"));

        List<String> expected = ProcessUtils.isWindows() ? List.of(".\\mvnw.cmd") : List.of("sh", "mvnw");
        assertEquals(expected, MavenArtifactResolver.mavenLauncher(dir));
    }
}
