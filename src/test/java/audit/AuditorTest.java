package audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import exception.UsageException;
import model.Artifact;
import org.junit.jupiter.api.Test;
import service.InitializrClient;
import service.MetadataService;
import service.PomFile;
import service.VulnerabilityService.Vuln;
import support.FakeOsv;
import support.SampleMetadata;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditorTest {

    private static final Artifact TOMCAT = new Artifact("org.apache.tomcat.embed", "tomcat-embed-core",
            "10.1.25", "compile", null);
    private static final PomFile POM = new PomFile(Path.of("pom.xml"), "<project/>");
    private static final List<AuditReport.Finding> ONE_FINDING = List.of(
            new AuditReport.Finding(TOMCAT, List.of(new Vuln("GHSA-1", "", "HIGH", List.of()))));

    /** Initializr metadata whose Spring Boot versions are exactly {@code versions}. */
    private static MetadataService metadataOffering(String... versions) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(SampleMetadata.JSON);
        ObjectNode boot = root.putObject("bootVersion");
        boot.put("default", versions[0]);
        ArrayNode values = boot.putArray("values");
        for (String v : versions) {
            values.addObject().put("id", v).put("name", v);
        }
        InitializrClient client = mock(InitializrClient.class);
        when(client.fetchMetadata()).thenReturn(root.toString());
        return new MetadataService(client);
    }

    /** Records which Spring Boot versions were tried; {@code failing} can't be resolved. */
    private static final class RecordingResolver implements ArtifactResolver {
        final List<String> tried = new ArrayList<>();
        private final String failing;

        RecordingResolver(String failing) {
            this.failing = failing;
        }

        @Override
        public List<Artifact> resolve(PomFile pom) {
            return List.of(TOMCAT);
        }

        @Override
        public List<Artifact> resolveWithSpringBoot(PomFile pom, String springBootVersion) {
            tried.add(springBootVersion);
            if (springBootVersion.equals(failing)) {
                throw new UsageException("Maven couldn't resolve the project's dependencies (exit code 1)");
            }
            return List.of(TOMCAT);
        }
    }

    @Test
    void picksTheOldestVersionThatFixesEverything() throws Exception {
        RecordingResolver resolver = new RecordingResolver(null);
        FakeOsv osv = new FakeOsv()
                .batch("GHSA-1")  // 3.3.2: still vulnerable
                .batch("");       // 3.4.1: clean
        Auditor auditor = new Auditor(resolver, osv.service(),
                metadataOffering("3.4.1", "3.5.0-SNAPSHOT", "3.3.2", "3.2.8", "3.1.0"));

        Optional<AuditReport.UpgradeSuggestion> s = auditor.suggestUpgrade(POM, "3.2.8", ONE_FINDING);

        assertEquals(List.of("3.3.2", "3.4.1"), resolver.tried, "GA versions newer than 3.2.8, oldest first");
        assertEquals("3.4.1", s.orElseThrow().to());
        assertEquals(1, s.get().fixes());
        assertEquals(1, s.get().total());
        assertEquals(0, s.get().remaining());
    }

    @Test
    void noSuggestionWhenNothingDoesBetter() throws Exception {
        FakeOsv osv = new FakeOsv().batch("GHSA-1");
        Auditor auditor = new Auditor(new RecordingResolver(null), osv.service(), metadataOffering("3.3.2"));

        assertTrue(auditor.suggestUpgrade(POM, "3.2.8", ONE_FINDING).isEmpty());
    }

    @Test
    void skipsAVersionThatCannotBeResolved() throws Exception {
        RecordingResolver resolver = new RecordingResolver("3.3.2");
        FakeOsv osv = new FakeOsv().batch(""); // only 3.4.1 reaches OSV
        Auditor auditor = new Auditor(resolver, osv.service(), metadataOffering("3.3.2", "3.4.1"));

        assertEquals("3.4.1", auditor.suggestUpgrade(POM, "3.2.8", ONE_FINDING).orElseThrow().to());
    }

    @Test
    void findingsCarryVulnerabilityDetails() throws Exception {
        Artifact safe = new Artifact("com.example", "safe", "1.0.0", "compile", null);
        FakeOsv osv = new FakeOsv().batch("GHSA-1", "").vuln("GHSA-1", "HIGH", "CVE-2024-1");
        Auditor auditor = new Auditor(new RecordingResolver(null), osv.service(), metadataOffering("3.3.2"));

        List<AuditReport.Finding> findings = auditor.findings(List.of(TOMCAT, safe));

        assertEquals(1, findings.size());
        assertEquals(TOMCAT, findings.get(0).artifact());
        assertEquals("HIGH", findings.get(0).vulnerabilities().get(0).severity());
    }
}
