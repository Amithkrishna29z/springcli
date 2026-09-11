package audit;

import model.Artifact;
import service.VulnerabilityService.Vuln;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The outcome of an audit, ready to render in any {@link ReportFormat}.
 *
 * @param springBootVersion the project's Spring Boot parent version, or {@code null} if it has none
 * @param auditedCount      how many artifacts were checked
 * @param suggestion        the smallest Spring Boot upgrade that fixes findings, or {@code null}
 */
public record AuditReport(Path pomPath, String springBootVersion, int auditedCount,
                          List<Finding> findings, UpgradeSuggestion suggestion) {

    /** An artifact and the known vulnerabilities affecting it. */
    public record Finding(Artifact artifact, List<Vuln> vulnerabilities) {
    }

    /**
     * @param fixes     how many of the current {@code total} vulnerabilities upgrading to {@code to} fixes
     * @param remaining how many known vulnerabilities there would be after upgrading
     */
    public record UpgradeSuggestion(String from, String to, int fixes, int total, int remaining) {
    }

    /** @return the distinct vulnerability ids across all findings */
    public Set<String> vulnerabilityIds() {
        return idsOf(findings);
    }

    /** @return whether any finding is at or above {@code threshold} (see {@link Severity#meets}) */
    public boolean hasFindingAtLeast(Severity threshold) {
        return findings.stream()
                .flatMap(f -> f.vulnerabilities().stream())
                .anyMatch(v -> Severity.meets(v.severity(), threshold));
    }

    static Set<String> idsOf(List<Finding> findings) {
        Set<String> ids = new LinkedHashSet<>();
        for (Finding f : findings) {
            for (Vuln v : f.vulnerabilities()) {
                ids.add(v.id());
            }
        }
        return ids;
    }
}
