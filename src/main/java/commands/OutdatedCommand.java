package commands;

import exception.UsageException;
import service.MetadataService;
import service.PomEditor;
import service.PomFile;
import util.Ansi;
import util.Versions;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

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

    @Mixin
    private PomFileOption pomFileOption;

    private final MetadataService metadataService;
    private final PomEditor pomEditor = new PomEditor();

    public OutdatedCommand(MetadataService metadataService) {
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

        Ansi.info("Checking the latest Spring Boot version...");
        String latest = metadataService.getMetadata().bootVersion().defaultValue();

        if (Versions.isNewer(latest, current)) {
            System.out.println("Spring Boot " + Ansi.yellow(current) + " → " + Ansi.green(latest)
                    + " is available.");
            System.out.println("Update the " + Ansi.cyan("<parent>") + " version in " + pom.fileName()
                    + " to upgrade.");
        } else {
            Ansi.success("Spring Boot is up to date (" + current + ").");
        }
        return 0;
    }
}
