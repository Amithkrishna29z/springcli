package commands;

import cli.ServiceFactory;
import service.MetadataService;
import service.PomEditor;
import service.UpdateService;
import util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Option(names = {"-f", "--file"}, paramLabel = "<pom>",
            description = "Path to the pom.xml to edit (default: ./pom.xml).")
    private Path pomFile;

    @Option(names = "--to", paramLabel = "<version>",
            description = "Upgrade to a specific Spring Boot version (default: the latest release).")
    private String targetVersion;

    @Option(names = "--dry-run", description = "Show the change without modifying the file.")
    private boolean dryRun;

    private final MetadataService metadataService;
    private final PomEditor pomEditor = new PomEditor();

    public UpgradeCommand() {
        this(new ServiceFactory().metadataService());
    }

    public UpgradeCommand(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public Integer call() throws IOException {
        Path pom = pomFile != null ? pomFile : Path.of("pom.xml");
        if (!Files.isRegularFile(pom)) {
            if (pomFile == null && Files.isRegularFile(Path.of("build.gradle"))) {
                Ansi.error("Found build.gradle but 'upgrade' supports Maven (pom.xml) only for now.");
                return 2;
            }
            Ansi.error("No pom.xml found at " + pom.toAbsolutePath()
                    + ". Run inside a Maven project or pass --file <pom>.");
            return 2;
        }

        String pomXml = Files.readString(pom);
        String current = pomEditor.springBootParentVersion(pomXml);
        if (current == null) {
            Ansi.error("Couldn't find a Spring Boot <parent> version in " + pom.getFileName()
                    + " — is this a Spring Boot Maven project?");
            return 2;
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
        if (targetVersion == null && !UpdateService.isNewer(target, current)) {
            Ansi.success("Spring Boot is up to date (" + current + ").");
            return 0;
        }

        String updated = pomEditor.setSpringBootParentVersion(pomXml, target);
        if (updated == null) {
            Ansi.error("Couldn't rewrite the <parent> version in " + pom.getFileName() + ".");
            return 2;
        }

        if (dryRun) {
            System.out.println("\nWould change Spring Boot " + Ansi.yellow(current) + " → "
                    + Ansi.green(target) + " in " + pom + ".");
            return 0;
        }

        writeString(pom, updated);
        Ansi.success("Upgraded Spring Boot " + current + " → " + target + " in " + pom.getFileName() + ".");
        System.out.println("Rebuild to pick up the managed dependency versions (e.g. "
                + Ansi.cyan("./mvnw clean install") + ").");
        return 0;
    }

    private static void writeString(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + path, e);
        }
    }
}
