package commands;

import cli.ServiceFactory;
import exception.SpringCliException;
import model.ProjectRequest;
import service.InitializrClient;
import service.MetadataService;
import service.PomEditor;
import util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * {@code springcli remove <deps>} — remove dependencies from an existing Maven project's
 * {@code pom.xml}. The inverse of {@link AddCommand}: dependency ids are validated against the
 * Initializr metadata and resolved to their exact Maven coordinates, then any matching
 * {@code <dependency>} nodes are deleted from the target file (leaving everything else untouched).
 */
@Command(name = "remove", description = "Remove dependencies from an existing Maven project's pom.xml.")
public class RemoveCommand implements Callable<Integer> {

    @Parameters(arity = "1..*", paramLabel = "<deps>",
            description = "Dependency ids to remove, e.g. 'redis actuator' (space or comma separated).")
    private List<String> depArgs;

    @Option(names = "--dry-run", description = "Show what would be removed without modifying the file.")
    private boolean dryRun;

    @Option(names = {"-f", "--file"}, paramLabel = "<pom>",
            description = "Path to the pom.xml to edit (default: ./pom.xml).")
    private Path pomFile;

    private final MetadataService metadataService;
    private final InitializrClient initializrClient;
    private final PomEditor pomEditor = new PomEditor();

    public RemoveCommand() {
        ServiceFactory factory = new ServiceFactory();
        this.metadataService = factory.metadataService();
        this.initializrClient = factory.initializrClient();
    }

    public RemoveCommand(MetadataService metadataService, InitializrClient initializrClient) {
        this.metadataService = metadataService;
        this.initializrClient = initializrClient;
    }

    @Override
    public Integer call() throws IOException {
        Path pom = pomFile != null ? pomFile : Path.of("pom.xml");
        if (!Files.isRegularFile(pom)) {
            if (pomFile == null && Files.isRegularFile(Path.of("build.gradle"))) {
                Ansi.error("Found build.gradle but 'remove' supports Maven (pom.xml) only for now.");
                return 2;
            }
            Ansi.error("No pom.xml found at " + pom.toAbsolutePath()
                    + ". Run inside a Maven project or pass --file <pom>.");
            return 2;
        }

        List<String> ids = parseIds(depArgs);
        for (String id : ids) {
            metadataService.validateDependency(id);
        }

        Ansi.info("Resolving dependency coordinates...");
        ProjectRequest request = ProjectRequest.builder()
                .type("maven-project")
                .bootVersion(metadataService.getMetadata().bootVersion().defaultValue())
                .dependencies(ids)
                .build();
        Set<String> resolvedKeys = pomEditor.dependencies(initializrClient.fetchPom(request)).stream()
                .map(PomEditor.Dep::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String pomXml = Files.readString(pom);
        Set<String> present = pomEditor.dependencies(pomXml).stream()
                .map(PomEditor.Dep::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> toRemove = resolvedKeys.stream()
                .filter(present::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (toRemove.isEmpty()) {
            Ansi.warn("Nothing to remove — none of the requested dependencies are in "
                    + pom.getFileName() + ".");
            return 0;
        }

        if (dryRun) {
            System.out.println("\nWould remove from " + pom + ":\n");
            for (String key : toRemove) {
                System.out.println("  " + key);
            }
            return 0;
        }

        String updated = pomEditor.removeDependencies(pomXml, toRemove);
        writeString(pom, updated);
        for (String key : toRemove) {
            Ansi.success("Removed " + key);
        }
        return 0;
    }

    private static List<String> parseIds(List<String> args) {
        Set<String> ids = new LinkedHashSet<>();
        for (String arg : args) {
            for (String part : arg.split(",")) {
                String id = part.trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            throw new SpringCliException("No dependency ids given. Example: springcli remove web data-jpa");
        }
        return new ArrayList<>(ids);
    }

    private static void writeString(Path path, String content) {
        try {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + path, e);
        }
    }
}
