package audit;

import model.Artifact;
import service.VulnerabilityService.Vuln;
import util.Ansi;

/** The human-readable, coloured report printed to the terminal. */
final class TextReport {

    private TextReport() {
    }

    static String render(AuditReport r) {
        if (r.auditedCount() == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder("\n");
        for (AuditReport.Finding f : r.findings()) {
            Artifact a = f.artifact();
            out.append(Ansi.red("✗ " + a.coordinates()));
            if (a.via() != null) {
                out.append("  (via ").append(a.via().substring(a.via().indexOf(':') + 1)).append(')');
            }
            out.append('\n');
            for (Vuln v : f.vulnerabilities()) {
                out.append("    ").append(label(v)).append(displayId(v)).append('\n');
                if (!v.summary().isBlank()) {
                    out.append("      ").append(v.summary()).append('\n');
                }
            }
        }

        if (r.findings().isEmpty()) {
            out.append(Ansi.green("✔ No known vulnerabilities in the " + r.auditedCount() + " audited "
                    + plural(r.auditedCount(), "dependency", "dependencies") + ".")).append('\n');
            return out.toString();
        }

        int vulns = r.vulnerabilityIds().size();
        int affected = r.findings().size();
        out.append('\n').append(Ansi.red("✗ " + vulns + " known " + plural(vulns, "vulnerability", "vulnerabilities")
                + " across " + affected + " " + plural(affected, "dependency", "dependencies") + ".")).append('\n');

        AuditReport.UpgradeSuggestion s = r.suggestion();
        if (s != null) {
            out.append(Ansi.bold("Fix: ")).append("Spring Boot ").append(Ansi.yellow(s.from())).append(" → ")
                    .append(Ansi.green(s.to())).append(" fixes ").append(s.fixes()).append(" of ").append(s.total());
            if (s.remaining() > 0) {
                out.append(" (").append(s.remaining()).append(" would remain)");
            }
            out.append(".  Run: ").append(Ansi.cyan("springcli upgrade --to " + s.to())).append('\n');
        }
        return out.toString();
    }

    /** Prefer the CVE alias (more recognizable) but keep the OSV id alongside it. */
    static String displayId(Vuln v) {
        for (String alias : v.aliases()) {
            if (alias.startsWith("CVE-")) {
                return alias + " (" + v.id() + ")";
            }
        }
        return v.id();
    }

    /** A "[SEVERITY] " prefix when OSV supplies one, otherwise empty. */
    private static String label(Vuln v) {
        return v.severity().isBlank() ? "" : Ansi.yellow("[" + v.severity() + "] ");
    }

    private static String plural(int n, String one, String many) {
        return n == 1 ? one : many;
    }
}
