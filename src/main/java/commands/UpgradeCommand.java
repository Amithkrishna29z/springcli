package commands;

import exception.UsageException;
import service.MetadataService;
import service.PomEditor;
import service.PomFile;
import util.Ansi;
import util.Versions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * {@code springcli upgrade} — bump the Spring Boot {@code <parent>} version in the project's
 * {@code pom.xml}. This is the "fix" partner to {@code outdated}: where {@code outdated} only reports
 * that a newer Boot release is available, {@code upgrade} performs the bump in place (a targeted text
 * splice via {@link PomEditor}, so comments/ordering/indentation are untouched). Defaults to the
 * latest release; {@code --to <version>} pins a specific one, validated against Initializr metadata.
 */
@Command(name = "upgrade",
        description = "Upgrade the Spring Boot <parent> version in your pom.xml (default: latest).")
public class UpgradeCommand implements Callable<Integer> {

    @Mixin
    private PomFileOption pomFileOption;

    @Option(names = "--to", paramLabel = "<version>",
            description = "Upgrade to a specific Spring Boot version (default: the latest release).")
    private String targetVersion;

    @Option(names = "--dry-run", description = "Show the change without modifying the file.")
    private boolean dryRun;

    private final MetadataService metadataService;
    private final PomEditor pomEditor = new PomEditor();

    public UpgradeCommand(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public Integer call() {
        PomFile pom = pomFileOption.load();
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
}
