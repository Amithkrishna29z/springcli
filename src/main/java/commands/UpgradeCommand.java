package commands;

import exception.UsageException;
import service.DependencyUpgrader;
import service.DependencyUpgrader.Pin;
import service.DependencyUpgrader.Upgrade;
import service.MetadataService;
import service.PomEditor;
import service.PomFile;
import util.Ansi;
import util.Versions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code springcli upgrade} — bump the Spring Boot {@code <parent>} version in the project's
 * {@code pom.xml}. This is the "fix" partner to {@code outdated}: where {@code outdated} only reports
 * that a newer Boot release is available, {@code upgrade} performs the bump in place (a targeted text
 * splice via {@link PomEditor}, so comments/ordering/indentation are untouched). Defaults to the
 * latest release; {@code --to <version>} pins a specific one, validated against Initializr metadata.
 *
 * <p>With {@code --deps} it instead moves the dependencies that pin their own version to their latest
 * stable release on Maven Central (see {@link DependencyUpgrader}), optionally only the named ones.
 */
@Command(name = "upgrade",
        description = "Upgrade the Spring Boot <parent> version in your pom.xml (default: latest), "
                + "or with --deps the dependencies that pin their own version.")
public class UpgradeCommand implements Callable<Integer> {

    @Mixin
    private PomFileOption pomFileOption;

    @Option(names = "--to", paramLabel = "<version>",
            description = "Upgrade to a specific Spring Boot version (default: the latest release).")
    private String targetVersion;

    @Option(names = "--deps",
            description = "Instead of Spring Boot, update the dependencies that pin their own <version> "
                    + "to their latest stable release on Maven Central.")
    private boolean deps;

    @Parameters(paramLabel = "<dependency>", arity = "0..*",
            description = "With --deps, update only these (artifactId or groupId:artifactId).")
    private List<String> names;

    @Option(names = "--dry-run", description = "Show the change without modifying the file.")
    private boolean dryRun;

    private final MetadataService metadataService;
    private final DependencyUpgrader dependencyUpgrader;
    private final PomEditor pomEditor = new PomEditor();

    public UpgradeCommand(MetadataService metadataService, DependencyUpgrader dependencyUpgrader) {
        this.metadataService = metadataService;
        this.dependencyUpgrader = dependencyUpgrader;
    }

    @Override
    public Integer call() {
        PomFile pom = pomFileOption.load();
        if (deps) {
            return upgradeDependencies(pom);
        }
        if (names != null) {
            throw new UsageException("Naming dependencies only works with --deps, e.g. springcli upgrade --deps "
                    + names.get(0));
        }
        String current = pomEditor.springBootParentVersion(pom.xml());
        if (current == null) {
            throw new UsageException("Couldn't find a Spring Boot <parent> version in " + pom.fileName()
                    + " — is this a Spring Boot Maven project?");
        }

        String target;
        if (targetVersion != null) {
            metadataService.validateBootVersion(targetVersion);
            target = targetVersion;
        } else {
            Ansi.info("Checking the latest Spring Boot version...");
            target = metadataService.getMetadata().bootVersion().defaultValue();
        }

        if (current.equals(target)) {
            Ansi.success("Spring Boot is already at " + current + " — nothing to upgrade.");
            return 0;
        }
        // Without an explicit target, only ever move forward: a current version newer than the latest
        // release (e.g. a pre-release) isn't "outdated".
        if (targetVersion == null && !Versions.isNewer(target, current)) {
            Ansi.success("Spring Boot is up to date (" + current + ").");
            return 0;
        }

        String updated = pomEditor.setSpringBootParentVersion(pom.xml(), target);
        if (updated == null) {
            throw new UsageException("Couldn't rewrite the <parent> version in " + pom.fileName() + ".");
        }

        if (dryRun) {
            System.out.println("\nWould change Spring Boot " + Ansi.yellow(current) + " → "
                    + Ansi.green(target) + " in " + pom.path() + ".");
            return 0;
        }

        pom.write(updated);
        Ansi.success("Upgraded Spring Boot " + current + " → " + target + " in " + pom.fileName() + ".");
        System.out.println("Rebuild to pick up the managed dependency versions (e.g. "
                + Ansi.cyan("./mvnw clean install") + ").");
        return 0;
    }

    private int upgradeDependencies(PomFile pom) {
        if (targetVersion != null) {
            throw new UsageException("--to sets the Spring Boot version, so it can't be combined with --deps.");
        }
        List<Pin> pins = dependencyUpgrader.pins(pom.xml(), names == null ? List.of() : names);
        if (pins.isEmpty()) {
            Ansi.success("No dependency in " + pom.fileName() + " pins its own version; Spring Boot manages them all.");
            System.out.println("Run " + Ansi.cyan("springcli upgrade") + " to move them to a newer Spring Boot release.");
            return 0;
        }

        int checked = pins.stream().mapToInt(p -> p.deps().size()).sum();
        Ansi.info("Checking " + checked + " pinned " + plural(checked) + " on Maven Central...");
        List<Upgrade> upgrades = dependencyUpgrader.upgrades(pins);
        if (upgrades.isEmpty()) {
            Ansi.success("All pinned dependencies are up to date.");
            return 0;
        }

        int labelWidth = upgrades.stream().mapToInt(u -> u.pin().label().length()).max().orElse(0);
        int versionWidth = upgrades.stream().mapToInt(u -> u.pin().version().length()).max().orElse(0);
        System.out.println();
        for (Upgrade u : upgrades) {
            System.out.println("  " + pad(u.pin().label(), labelWidth) + "  "
                    + Ansi.yellow(pad(u.pin().version(), versionWidth)) + " → " + Ansi.green(u.to())
                    + (u.major() ? "  " + Ansi.red("(major)") : ""));
        }
        System.out.println();
        if (upgrades.stream().anyMatch(Upgrade::major)) {
            Ansi.warn("Major upgrades can include breaking changes; check their release notes.");
        }

        String updated = dependencyUpgrader.apply(pom.xml(), upgrades);
        int count = upgrades.stream().mapToInt(u -> u.pin().deps().size()).sum();
        if (dryRun) {
            System.out.println("Would update " + count + " " + plural(count) + " in " + pom.path() + ".");
            return 0;
        }

        pom.write(updated);
        Ansi.success("Updated " + count + " " + plural(count) + " in " + pom.fileName() + ".");
        System.out.println("Rebuild and run your tests to pick up the new versions (e.g. "
                + Ansi.cyan("./mvnw clean verify") + ").");
        return 0;
    }

    private static String plural(int n) {
        return n == 1 ? "dependency" : "dependencies";
    }

    private static String pad(String s, int width) {
        return String.format("%-" + width + "s", s);
    }
}
