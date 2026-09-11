package commands;

import audit.AuditReport;
import audit.Auditor;
import audit.ReportFormat;
import audit.Severity;
import exception.NetworkException;
import exception.UsageException;
import model.Artifact;
import service.PomEditor;
import service.PomFile;
import util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code springcli audit} — check the project's dependencies against the OSV vulnerability database.
 *
 * <p>By default Maven resolves the full dependency list — every version the Spring Boot parent manages
 * and every transitive dependency — so the audit covers what the app actually ships. When something is
 * found, newer Spring Boot versions are tried to find the smallest upgrade that fixes it.
 * {@code --no-resolve} skips Maven and checks only the versions written in the pom.
 *
 * <p>Exit codes: 0 = nothing found at or above {@code --fail-on}, 1 = findings, 2 = the audit couldn't
 * run. That makes it usable as a CI gate.
 */
@Command(name = "audit",
        description = "Check your project's dependencies against the OSV vulnerability database.")
public class AuditCommand implements Callable<Integer> {

    @Mixin
    private PomFileOption pomFileOption;

    @Option(names = "--no-resolve",
            description = "Don't run Maven; only audit dependency versions written in the pom.")
    private boolean noResolve;

    @Option(names = "--fail-on", paramLabel = "<severity>",
            description = "Exit with code 1 only for findings at or above low, moderate, high or critical "
                    + "(default: low). Findings with no known severity always count.")
    private Severity failOn = Severity.LOW;

    @Option(names = "--format", paramLabel = "<format>",
            description = "Report format: text, json or sarif (default: text). The report goes to stdout "
                    + "and, for json/sarif, progress messages to stderr.")
    private ReportFormat format = ReportFormat.TEXT;

    private final Auditor auditor;
    private final PomEditor pomEditor = new PomEditor();

    public AuditCommand(Auditor auditor) {
        this.auditor = auditor;
    }

    @Override
    public Integer call() {
        PomFile pom = pomFileOption.load();

        PrintStream stdout = System.out;
        if (format != ReportFormat.TEXT) {
            // Keep stdout for the report alone, so it can be redirected to a file or piped into a tool.
            System.setOut(System.err);
        }
        AuditReport report;
        try {
            report = audit(pom);
        } catch (NetworkException e) {
            Ansi.error(e.getMessage());
            return 2;
        } finally {
            System.setOut(stdout);
        }

        stdout.print(format.render(report));
        return report.hasFindingAtLeast(failOn) ? 1 : 0;
    }

    private AuditReport audit(PomFile pom) {
        String bootVersion = pomEditor.springBootParentVersion(pom.xml());
        List<Artifact> artifacts = noResolve ? pinnedArtifacts(pom) : resolvedArtifacts(pom);
        if (artifacts.isEmpty()) {
            return new AuditReport(pom.path(), bootVersion, 0, List.of(), null);
        }

        Ansi.info("Checking " + artifacts.size() + (artifacts.size() == 1 ? " dependency" : " dependencies")
                + " against OSV...");
        List<AuditReport.Finding> findings = auditor.findings(artifacts);

        AuditReport.UpgradeSuggestion suggestion = null;
        if (!noResolve && bootVersion != null && !findings.isEmpty()) {
            Ansi.info("Looking for a Spring Boot upgrade that fixes them...");
            suggestion = auditor.suggestUpgrade(pom, bootVersion, findings).orElse(null);
        }
        return new AuditReport(pom.path(), bootVersion, artifacts.size(), findings, suggestion);
    }

    private List<Artifact> resolvedArtifacts(PomFile pom) {
        Ansi.info("Resolving dependencies with Maven...");
        try {
            return auditor.resolve(pom);
        } catch (UsageException e) {
            throw new UsageException(e.getMessage()
                    + "\nTo audit only the versions written in the pom, pass --no-resolve.");
        }
    }

    /** The dependencies that declare a {@code <version>} in the pom itself. */
    private List<Artifact> pinnedArtifacts(PomFile pom) {
        List<Artifact> pinned = pomEditor.dependencies(pom.xml()).stream()
                .filter(d -> d.version() != null && !d.version().isBlank())
                .map(d -> new Artifact(d.groupId(), d.artifactId(), d.version(), d.scope(), null))
                .toList();
        if (pinned.isEmpty()) {
            Ansi.warn("No pinned dependency versions to audit in " + pom.fileName() + ".");
            System.out.println("Spring-managed dependencies have no explicit <version>; run "
                    + Ansi.cyan("springcli audit") + " without --no-resolve to check the versions Maven resolves.");
        }
        return pinned;
    }
}
