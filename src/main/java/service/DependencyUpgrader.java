package service;

import exception.SpringCliException;
import exception.UsageException;
import util.Ansi;
import util.Versions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Moves the versions a pom pins itself — a literal {@code <version>}, or a {@code ${property}} defined
 * in the same pom — to their latest stable release on Maven Central. Dependencies whose version comes
 * from the Spring Boot parent aren't pinned, so they're left to {@code springcli upgrade}.
 */
public class DependencyUpgrader {

    /** A version that is exactly one property reference, e.g. {@code ${jjwt.version}}. */
    private static final Pattern PROPERTY_REF = Pattern.compile("\\$\\{([^}]+)}");

    private final MavenCentralService mavenCentral;
    private final PomEditor pomEditor = new PomEditor();

    public DependencyUpgrader(MavenCentralService mavenCentral) {
        this.mavenCentral = mavenCentral;
    }

    /**
     * A version pinned in the pom and the dependencies that use it: one for a literal
     * {@code <version>}, or every dependency that shares a {@code ${property}}.
     *
     * @param property the property holding the version, or {@code null} for a literal {@code <version>}
     */
    public record Pin(String property, String version, List<PomEditor.Dep> deps) {

        public String label() {
            if (property == null) {
                return deps.get(0).key();
            }
            return "${" + property + "} (" + deps.stream().map(PomEditor.Dep::artifactId)
                    .collect(Collectors.joining(", ")) + ")";
        }

        /** Whether {@code name} is the artifactId or groupId:artifactId of one of the dependencies. */
        boolean matches(String name) {
            return deps.stream().anyMatch(d -> d.artifactId().equals(name) || d.key().equals(name));
        }
    }

    /** A pin that has a newer stable release, {@code to}. */
    public record Upgrade(Pin pin, String to) {

        public boolean major() {
            return Versions.isMajorUpgrade(pin.version(), to);
        }
    }

    /**
     * @param names if not empty, keep only the pins used by one of these dependencies (artifactId or
     *              groupId:artifactId)
     * @return the versions {@code pomXml} pins itself, in pom order
     * @throws UsageException if one of {@code names} matches no pinned dependency
     */
    public List<Pin> pins(String pomXml, List<String> names) {
        Map<String, Pin> pins = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (PomEditor.Dep d : pomEditor.dependencies(pomXml)) {
            if (d.version() == null || d.version().isBlank() || !seen.add(d.key())) {
                continue;
            }
            Matcher ref = PROPERTY_REF.matcher(d.version());
            if (!ref.matches()) {
                pins.put(d.key(), new Pin(null, d.version(), List.of(d)));
                continue;
            }
            String property = ref.group(1);
            String value = pomEditor.property(pomXml, property);
            // A property this pom doesn't define (e.g. ${project.version}) is inherited, not pinned here.
            if (value != null && !value.isBlank() && !value.contains("${")) {
                pins.computeIfAbsent("${" + property + "}", k -> new Pin(property, value, new ArrayList<>()))
                        .deps().add(d);
            }
        }

        List<Pin> all = new ArrayList<>(pins.values());
        for (String name : names) {
            if (all.stream().noneMatch(p -> p.matches(name))) {
                throw new UsageException("No dependency matching '" + name + "' pins its own version in the pom. "
                        + "Spring Boot manages the rest: run 'springcli upgrade'.");
            }
        }
        return names.isEmpty() ? all : all.stream().filter(p -> names.stream().anyMatch(p::matches)).toList();
    }

    /**
     * Looks up each pin's latest stable release on Maven Central. A version shared by several
     * dependencies only moves to a release all of them have. A pin with a dependency Maven Central
     * doesn't have is skipped with a warning.
     *
     * @return the pins that have a newer release, in the same order
     * @throws exception.NetworkException if Maven Central can't be reached
     */
    public List<Upgrade> upgrades(List<Pin> pins) {
        List<Upgrade> upgrades = new ArrayList<>();
        for (Pin pin : pins) {
            latestRelease(pin).ifPresent(to -> upgrades.add(new Upgrade(pin, to)));
        }
        return upgrades;
    }

    private Optional<String> latestRelease(Pin pin) {
        Set<String> common = null;
        for (PomEditor.Dep d : pin.deps()) {
            List<String> versions = mavenCentral.versions(d.groupId(), d.artifactId());
            if (versions.isEmpty()) {
                Ansi.warn("Skipped " + d.key() + ": it isn't on Maven Central.");
                return Optional.empty();
            }
            if (common == null) {
                common = new HashSet<>(versions);
            } else {
                common.retainAll(versions);
            }
        }
        return Versions.latestRelease(common, pin.version());
    }

    /**
     * @return {@code pomXml} with every upgrade applied and everything else left untouched
     * @throws SpringCliException if a version can't be located to rewrite
     */
    public String apply(String pomXml, List<Upgrade> upgrades) {
        String xml = pomXml;
        for (Upgrade u : upgrades) {
            Pin pin = u.pin();
            String updated = pin.property() != null
                    ? pomEditor.setProperty(xml, pin.property(), u.to())
                    : pomEditor.setDependencyVersion(xml, pin.deps().get(0).key(), pin.version(), u.to());
            if (updated == null) {
                throw new SpringCliException("Couldn't locate the version of " + pin.label()
                        + " to rewrite; update it by hand.");
            }
            xml = updated;
        }
        return xml;
    }
}
