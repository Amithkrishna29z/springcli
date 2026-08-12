package commands;

import cli.ServiceFactory;
import service.MetadataService;
import service.PomEditor;
import service.UpdateService;
import util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code springcli outdated} — reports whether a newer Spring Boot version is available for the
 * project. Because starters are version-managed by the {@code spring-boot-starter-parent}, "outdated"
 * for a Spring Boot project is really "is the parent Boot version behind the latest release?"; this
 * compares the pom's {@code <parent>} version against the newest version Initializr recommends.
 */
@Command(name = "outdated",
        description = "Check whether a newer Spring Boot version is available for your project.")
public class OutdatedCommand implements Callable<Integer> {

    @Option(names = {"-f", "--file"}, paramLabel = "<pom>",
            description = "Path to the pom.xml to check (default: ./pom.xml).")
    private Path pomFile;

    private final MetadataService metadataService;
    private final PomEditor pomEditor = new PomEditor();

    public OutdatedCommand() {
        this(new ServiceFactory().metadataService());
    }

    public OutdatedCommand(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public Integer call() throws IOException {
        Path pom = pomFile != null ? pomFile : Path.of("pom.xml");
        if (!Files.isRegularFile(pom)) {
            if (pomFile == null && Files.isRegularFile(Path.of("build.gradle"))) {
                Ansi.error("Found build.gradle but 'outdated' supports Maven (pom.xml) only for now.");
                return 2;
            }
            Ansi.error("No pom.xml found at " + pom.toAbsolutePath()
                    + ". Run inside a Maven project or pass --file <pom>.");
            return 2;
        }

        String current = pomEditor.springBootParentVersion(Files.readString(pom));
        if (current == null) {
            Ansi.error("Couldn't find a Spring Boot <parent> version in " + pom.getFileName()
                    + " — is this a Spring Boot Maven project?");
            return 2;
        }

        Ansi.info("Checking the latest Spring Boot version...");
        String latest = metadataService.getMetadata().bootVersion().defaultValue();

        if (UpdateService.isNewer(latest, current)) {
            System.out.println("Spring Boot " + Ansi.yellow(current) + " → " + Ansi.green(latest)
                    + " is available.");
            System.out.println("Update the " + Ansi.cyan("<parent>") + " version in " + pom.getFileName()
                    + " to upgrade.");
        } else {
            Ansi.success("Spring Boot is up to date (" + current + ").");
        }
        return 0;
    }
}
