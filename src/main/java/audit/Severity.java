package audit;

import java.util.Locale;

/** Vulnerability severity, in increasing order, as labelled by OSV (GitHub advisory severities). */
public enum Severity {

    LOW, MODERATE, HIGH, CRITICAL;

    /** @return the severity for an OSV label, or {@code null} if the label is empty or unrecognised */
    public static Severity parse(String label) {
        return switch (label == null ? "" : label.trim().toUpperCase(Locale.ROOT)) {
            case "LOW" -> LOW;
            case "MODERATE", "MEDIUM" -> MODERATE;
            case "HIGH" -> HIGH;
            case "CRITICAL" -> CRITICAL;
            default -> null;
        };
    }

    /**
     * @return whether a vulnerability labelled {@code label} is at or above {@code threshold}. An
     * unknown severity always counts: a false alarm is better than a missed vulnerability.
     */
    public static boolean meets(String label, Severity threshold) {
        Severity severity = parse(label);
        return severity == null || severity.compareTo(threshold) >= 0;
    }
}
