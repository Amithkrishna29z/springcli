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
 * {@code springcli add <deps>} — add dependencies to an existing Maven project's {@code pom.xml}.
 *
 * <p>The dependency ids are validated against the Initializr metadata, then their exact Maven
 * coordinates are resolved by fetching a reference {@code pom.xml} from Initializr and injecting the
 * new {@code <dependency>} nodes into the target file (skipping any already present).
 */
@Command(name = "add", description = "Add dependencies to an existing Maven project's pom.xml.")
public class AddCommand implements Callable<Integer> {

    @Parameters(arity = "1..*", paramLabel = "<deps>",
            description = "Dependency ids to add, e.g. 'redis actuator' (space or comma separated).")
    private List<String> depArgs;

    @Option(names = "--dry-run", description = "Show what would be added without modifying the file.")
    private boolean dryRun;

    @Option(names = {"-f", "--file"}, paramLabel = "<pom>",
            description = "Path to the pom.xml to edit (default: ./pom.xml).")
    private Path pomFile;

    private final MetadataService metadataService;
    private final InitializrClient initializrClient;
    private final PomEditor pomEditor = new PomEditor();

    public AddCommand() {
        ServiceFactory factory = new ServiceFactory();
        this.metadataService = factory.metadataService();
        this.initializrClient = factory.initializrClient();
    }

    public AddCommand(MetadataService metadataService, InitializrClient initializrClient) {
        this.metadataService = metadataService;
        this.initializrClient = initializrClient;
    }

    @Override
    public Integer call() throws IOException {
        Path pom = pomFile != null ? pomFile : Path.of("pom.xml");
        if (!Files.isRegularFile(pom)) {
            if (pomFile == null && Files.isRegularFile(Path.of("build.gradle"))) {
                Ansi.error("Found build.gradle but 'add' supports Maven (pom.xml) only for now.");
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
        List<PomEditor.Dep> resolved = pomEditor.dependencies(initializrClient.fetchPom(request));

        String pomXml = Files.readString(pom);
        Set<String> present = pomEditor.dependencies(pomXml).stream()
                .map(PomEditor.Dep::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<PomEditor.Dep> toAdd = resolved.stream()
                .filter(d -> !present.contains(d.key()))
                .collect(Collectors.toList());

        if (toAdd.isEmpty()) {
            Ansi.warn("Nothing to add — the requested dependencies are already in " + pom.getFileName() + ".");
            return 0;
        }

        if (dryRun) {
            System.out.println("\nWould add to " + pom + ":\n");
            System.out.print(pomEditor.renderBlock(pomXml, toAdd));
            return 0;
        }

        String updated = pomEditor.addDependencies(pomXml, toAdd);
        writeString(pom, updated);
        for (PomEditor.Dep d : toAdd) {
            Ansi.success("Added " + d.key());
        }
        int skipped = resolved.size() - toAdd.size();
        if (skipped > 0) {
            System.out.println("  (" + skipped + " already present, skipped)");
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
            throw new SpringCliException("No dependency ids given. Example: springcli add web data-jpa");
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
