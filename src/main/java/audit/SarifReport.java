package audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import config.BuildInfo;
import model.Artifact;
import service.VulnerabilityService.Vuln;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * A SARIF 2.1.0 report, which GitHub code scanning (and other tools) can ingest. Every result points
 * at the audited pom, since that's where a fix (usually the Spring Boot version) is made.
 */
final class SarifReport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SarifReport() {
    }

    static String render(AuditReport r) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        root.put("version", "2.1.0");
        ObjectNode run = root.putArray("runs").addObject();
        ObjectNode driver = run.putObject("tool").putObject("driver");
        driver.put("name", "springcli");
        driver.put("version", BuildInfo.VERSION);
        driver.put("informationUri", "https://github.com/Amithkrishna29z/springcli");
        ArrayNode rules = driver.putArray("rules");
        ArrayNode results = run.putArray("results");

        String pomUri = uri(r.pomPath());
        Set<String> ruleIds = new HashSet<>();
        for (AuditReport.Finding f : r.findings()) {
            for (Vuln v : f.vulnerabilities()) {
                if (ruleIds.add(v.id())) {
                    rules.add(rule(v));
                }
                ObjectNode result = results.addObject();
                result.put("ruleId", v.id());
                result.put("level", level(v));
                result.putObject("message").put("text", message(f.artifact(), v, r.suggestion()));
                ObjectNode location = result.putArray("locations").addObject().putObject("physicalLocation");
                location.putObject("artifactLocation").put("uri", pomUri);
                location.putObject("region").put("startLine", 1);
            }
        }
        return root.toPrettyString() + "\n";
    }

    private static ObjectNode rule(Vuln v) {
        ObjectNode rule = MAPPER.createObjectNode();
        rule.put("id", v.id());
        rule.putObject("shortDescription").put("text", v.summary().isBlank() ? v.id() : v.summary());
        rule.put("helpUri", "https://osv.dev/vulnerability/" + v.id());
        ObjectNode properties = rule.putObject("properties");
        properties.putArray("tags").add("security");
        String score = securitySeverity(Severity.parse(v.severity()));
        if (score != null) {
            properties.put("security-severity", score);
        }
        return rule;
    }

    private static String message(Artifact a, Vuln v, AuditReport.UpgradeSuggestion suggestion) {
        StringBuilder text = new StringBuilder(a.coordinates()).append(" is affected by ").append(TextReport.displayId(v));
        if (!v.summary().isBlank()) {
            text.append(": ").append(v.summary());
        }
        if (a.via() != null) {
            text.append(" (via ").append(a.via()).append(')');
        }
        if (suggestion != null) {
            text.append(". Upgrading Spring Boot to ").append(suggestion.to())
                    .append(" fixes ").append(suggestion.fixes()).append(" of ").append(suggestion.total())
                    .append(" known vulnerabilities");
        }
        return text.append('.').toString();
    }

    /** SARIF levels: error for high/critical, note for low, warning otherwise (including unknown). */
    private static String level(Vuln v) {
        Severity severity = Severity.parse(v.severity());
        if (severity == Severity.HIGH || severity == Severity.CRITICAL) {
            return "error";
        }
        return severity == Severity.LOW ? "note" : "warning";
    }

    /** The CVSS-like score GitHub uses to rank security alerts, or {@code null} if unknown. */
    private static String securitySeverity(Severity severity) {
        if (severity == null) {
            return null;
        }
        return switch (severity) {
            case CRITICAL -> "9.5";
            case HIGH -> "8.0";
            case MODERATE -> "5.5";
            case LOW -> "2.0";
        };
    }

    /** The pom's path relative to the working directory (as GitHub expects), falling back to a file URI. */
    private static String uri(Path pom) {
        try {
            return Path.of("").toAbsolutePath().relativize(pom.toAbsolutePath()).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return pom.toAbsolutePath().toUri().toString();
        }
    }
}
