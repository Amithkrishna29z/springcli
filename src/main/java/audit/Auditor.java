package audit;

import exception.SpringCliException;
import model.Artifact;
import model.Metadata;
import service.MetadataService;
import service.PomFile;
import service.VulnerabilityService;
import service.VulnerabilityService.Vuln;
import util.Ansi;
import util.Versions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Runs a dependency audit: resolves what a project really depends on, checks it against OSV, and
 * looks for the smallest Spring Boot upgrade that fixes what it finds.
 */
public class Auditor {

    /** A general-availability version such as 3.3.5 (no -SNAPSHOT, -M1 or -RC1 suffix). */
    private static final Pattern GA_VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");

    private final ArtifactResolver resolver;
    private final VulnerabilityService vulnerabilityService;
    private final MetadataService metadataService;
    private final Map<String, Vuln> details = new HashMap<>();

    public Auditor(ArtifactResolver resolver, VulnerabilityService vulnerabilityService,
                   MetadataService metadataService) {
        this.resolver = resolver;
        this.vulnerabilityService = vulnerabilityService;
        this.metadataService = metadataService;
    }

    /**
     * @return the non-test artifacts the project resolves to, direct and transitive
     * @throws exception.UsageException if the dependencies can't be resolved
     */
    public List<Artifact> resolve(PomFile pom) {
        return resolver.resolve(pom);
    }

    /** @return each artifact affected by known vulnerabilities, with the details of each vulnerability */
    public List<AuditReport.Finding> findings(List<Artifact> artifacts) {
        List<List<String>> ids = vulnerabilityService.queryBatch(artifacts);
        List<AuditReport.Finding> findings = new ArrayList<>();
        for (int i = 0; i < artifacts.size(); i++) {
            if (!ids.get(i).isEmpty()) {
                findings.add(new AuditReport.Finding(artifacts.get(i), ids.get(i).stream().map(this::details).toList()));
            }
        }
        return findings;
    }

    /**
     * Re-resolves the project with each newer GA Spring Boot version Initializr offers, oldest first,
     * and picks the smallest one that leaves the fewest known vulnerabilities. A version that can't be
     * checked is skipped with a warning.
     *
     * @return the suggestion, or empty if no version does better than {@code currentVersion}
     */
    public Optional<AuditReport.UpgradeSuggestion> suggestUpgrade(PomFile pom, String currentVersion,
                                                                  List<AuditReport.Finding> findings) {
        List<String> candidates;
        try {
            candidates = newerGaVersions(currentVersion);
        } catch (SpringCliException e) {
            Ansi.warn("Couldn't load Spring Boot versions, so no upgrade can be suggested: " + e.getMessage());
            return Optional.empty();
        }

        Set<String> current = AuditReport.idsOf(findings);
        String best = null;
        Set<String> bestRemaining = current;
        for (String candidate : candidates) {
            Ansi.info("  trying Spring Boot " + candidate + "...");
            Set<String> remaining;
            try {
                remaining = vulnerabilityIds(resolver.resolveWithSpringBoot(pom, candidate));
            } catch (SpringCliException e) {
                Ansi.warn("  skipped Spring Boot " + candidate + ": "
                        + e.getMessage().lines().findFirst().orElse(""));
                continue;
            }
            if (remaining.size() < bestRemaining.size()) {
                best = candidate;
                bestRemaining = remaining;
            }
            if (remaining.isEmpty()) {
                break; // candidates are oldest first, so nothing later can do better
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        Set<String> left = bestRemaining;
        int fixes = (int) current.stream().filter(id -> !left.contains(id)).count();
        return Optional.of(new AuditReport.UpgradeSuggestion(currentVersion, best, fixes, current.size(), left.size()));
    }

    private Set<String> vulnerabilityIds(List<Artifact> artifacts) {
        Set<String> ids = new HashSet<>();
        vulnerabilityService.queryBatch(artifacts).forEach(ids::addAll);
        return ids;
    }

    private List<String> newerGaVersions(String currentVersion) {
        return metadataService.getMetadata().bootVersion().values().stream()
                .map(Metadata.Option::id)
                .filter(v -> GA_VERSION.matcher(v).matches() && Versions.isNewer(v, currentVersion))
                .sorted(Versions::compare)
                .toList();
    }

    private Vuln details(String id) {
        return details.computeIfAbsent(id, vulnerabilityService::details);
    }
}
