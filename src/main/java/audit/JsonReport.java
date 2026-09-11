package audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import model.Artifact;
import service.VulnerabilityService.Vuln;

/** A machine-readable JSON report, for scripts and CI tooling. */
final class JsonReport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonReport() {
    }

    static String render(AuditReport r) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("pom", r.pomPath().toString());
        root.put("springBootVersion", r.springBootVersion());
        root.put("auditedDependencies", r.auditedCount());
        root.put("vulnerabilities", r.vulnerabilityIds().size());

        ArrayNode findings = root.putArray("findings");
        for (AuditReport.Finding f : r.findings()) {
            Artifact a = f.artifact();
            ObjectNode finding = findings.addObject();
            finding.put("groupId", a.groupId());
            finding.put("artifactId", a.artifactId());
            finding.put("version", a.version());
            finding.put("scope", a.scope());
            finding.put("via", a.via());
            ArrayNode vulns = finding.putArray("vulnerabilities");
            for (Vuln v : f.vulnerabilities()) {
                ObjectNode vuln = vulns.addObject();
                vuln.put("id", v.id());
                vuln.put("severity", v.severity().isBlank() ? null : v.severity());
                vuln.put("summary", v.summary());
                ArrayNode aliases = vuln.putArray("aliases");
                v.aliases().forEach(aliases::add);
                vuln.put("url", "https://osv.dev/vulnerability/" + v.id());
            }
        }

        AuditReport.UpgradeSuggestion s = r.suggestion();
        if (s == null) {
            root.putNull("upgradeSuggestion");
        } else {
            root.putObject("upgradeSuggestion")
                    .put("from", s.from())
                    .put("to", s.to())
                    .put("fixes", s.fixes())
                    .put("total", s.total())
                    .put("remaining", s.remaining());
        }
        return root.toPrettyString() + "\n";
    }
}
