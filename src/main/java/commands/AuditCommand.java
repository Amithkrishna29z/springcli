package commands;

import exception.NetworkException;
import service.PomEditor;
import service.PomFile;
import service.VulnerabilityService;
import service.VulnerabilityService.Vuln;
import util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code springcli audit} — check the project's dependencies against the OSV vulnerability database.
 *
 * <p>Only dependencies that declare a concrete {@code <version>} can be audited: starters are
 * version-managed by the {@code spring-boot-starter-parent}, so their versions aren't in the pom and
 * are instead governed by your Spring Boot version (use {@code springcli outdated} for that). Exits
 * non-zero when vulnerabilities are found, so it's usable as a CI gate.
 */
@Command(name = "audit",
        description = "Check pinned dependency versions against the OSV vulnerability database.")
public class AuditCommand implements Callable<Integer> {

    @Mixin
    private PomFileOption pomFileOption;

    private final VulnerabilityService vulnerabilityService;
    private final PomEditor pomEditor = new PomEditor();

    public AuditCommand(VulnerabilityService vulnerabilityService) {
        this.vulnerabilityService = vulnerabilityService;
    }

    @Override
    public Integer call() {
        PomFile pom = pomFileOption.load();
        List<PomEditor.Dep> versioned = pomEditor.dependencies(pom.xml()).stream()
                .filter(d -> d.version() != null && !d.version().isBlank())
                .toList();

        if (versioned.isEmpty()) {
            Ansi.warn("No pinned dependency versions to audit in " + pom.fileName() + ".");
            System.out.println("Spring-managed dependencies have no explicit <version>; run "
                    + Ansi.cyan("springcli outdated") + " to check your Spring Boot version instead.");
            return 0;
        }

        Ansi.info("Auditing " + versioned.size() + " pinned "
                + (versioned.size() == 1 ? "dependency" : "dependencies") + " against OSV...");
        System.out.println();

        int vulnerable = 0;
        int total = 0;
        try {
            for (PomEditor.Dep d : versioned) {
                List<Vuln> vulns = vulnerabilityService.check(d.groupId(), d.artifactId(), d.version());
                if (vulns.isEmpty()) {
                    continue;
                }
                vulnerable++;
                total += vulns.size();
                System.out.println(Ansi.red("✗ " + d.key() + ":" + d.version()));
                for (Vuln v : vulns) {
                    System.out.println("    " + label(v) + printableId(v));
                    if (!v.summary().isBlank()) {
                        System.out.println("      " + v.summary());
                    }
                }
            }
        } catch (NetworkException e) {
            Ansi.error(e.getMessage());
            return 2;
        }

        System.out.println();
        if (vulnerable == 0) {
            Ansi.success("No known vulnerabilities in the audited dependencies.");
            return 0;
        }
        Ansi.error(total + " known " + (total == 1 ? "vulnerability" : "vulnerabilities")
                + " across " + vulnerable + " " + (vulnerable == 1 ? "dependency" : "dependencies") + ".");
        return 1;
    }

    /** A "[SEVERITY] " prefix when OSV supplies one, otherwise empty. */
    private static String label(Vuln v) {
        return v.severity().isBlank() ? "" : Ansi.yellow("[" + v.severity() + "] ");
    }

    /** Prefer the CVE alias (more recognizable) but keep the OSV id when there's no alias. */
    private static String printableId(Vuln v) {
        for (String alias : v.aliases()) {
            if (alias.startsWith("CVE-")) {
                return alias + " (" + v.id() + ")";
            }
        }
        return v.id();
    }
}
