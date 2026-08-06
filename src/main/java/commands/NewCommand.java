package commands;

import cli.ServiceFactory;
import exception.SpringCliException;
import model.ProjectRequest;
import prompts.InteractiveWizard;
import service.MetadataService;
import service.ProjectGenerator;
import util.Ansi;
import util.ProcessUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code springcli new [name]} — creates a new Spring Boot project.
 *
 * <p>By default it launches the interactive wizard (pre-filled with {@code name} if supplied). When
 * {@code --yes} is passed it runs non-interactively, resolving every option from command-line flags
 * and metadata defaults, which is convenient for scripting.</p>
 */
@Command(name = "new", description = "Create a new Spring Boot project (interactive by default).")
public class NewCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", paramLabel = "<name>",
            description = "Project (and default artifact) name.")
    private String name;

    @Option(names = {"-y", "--yes"}, description = "Skip the wizard; use flags and defaults.")
    private boolean nonInteractive;

    @Option(names = "--group", description = "Group ID (e.g. com.example).")
    private String groupId;

    @Option(names = "--artifact", description = "Artifact ID.")
    private String artifactId;

    @Option(names = "--package", description = "Base package name.")
    private String packageName;

    @Option(names = "--description", description = "Project description.")
    private String description;

    @Option(names = "--type", description = "Build type: maven-project or gradle-project.")
    private String type = "maven-project";

    @Option(names = "--language", description = "java, kotlin or groovy.")
    private String language = "java";

    @Option(names = "--packaging", description = "jar or war.")
    private String packaging = "jar";

    @Option(names = "--boot-version", description = "Spring Boot version (defaults to Initializr default).")
    private String bootVersion;

    @Option(names = "--java-version", description = "Java version (defaults to Initializr default).")
    private String javaVersion;

    @Option(names = "--deps", split = ",", description = "Comma-separated dependency ids, e.g. web,data-jpa.")
    private List<String> dependencies = List.of();

    @Option(names = "--force", description = "Overwrite a non-empty destination directory.")
    private boolean force;

    @Option(names = "--git", description = "Initialize a git repository in the new project.")
    private boolean initGit;

    @Option(names = "--open", description = "Open the project in VS Code if 'code' is available.")
    private boolean openInVsCode;

    @Option(names = "--build", description = "Run 'mvn clean install' (or gradle build) after generation.")
    private boolean runBuild;

    private final MetadataService metadataService;
    private final ProjectGenerator projectGenerator;

    public NewCommand() {
        ServiceFactory factory = new ServiceFactory();
        this.metadataService = factory.metadataService();
        this.projectGenerator = factory.projectGenerator();
    }

    public NewCommand(MetadataService metadataService, ProjectGenerator projectGenerator) {
        this.metadataService = metadataService;
        this.projectGenerator = projectGenerator;
    }

    @Override
    public Integer call() {
        Ansi.info("Fetching Spring metadata...");
        // Trigger and validate metadata up front so failures surface before any prompting.
        metadataService.getMetadata();

        ProjectRequest request = nonInteractive ? buildFromFlags() : runWizard();
        if (request == null) {
            return 0; // user cancelled
        }

        Path targetDir = Path.of(request.artifactId());
        Ansi.info("Selecting dependencies...");
        projectGenerator.generate(request, targetDir, force);

        Ansi.success("Project created successfully at ./" + targetDir);

        runPostActions(targetDir, request);
        return 0;
    }

    /** Runs the interactive wizard, using the positional name as the default. */
    private ProjectRequest runWizard() {
        return new InteractiveWizard(metadataService).run(name);
    }

    /** Builds a request purely from flags + metadata defaults, validating version choices. */
    private ProjectRequest buildFromFlags() {
        String resolvedName = name != null ? name : "demo";
        String resolvedArtifact = artifactId != null ? artifactId : resolvedName;
        String boot = bootVersion != null ? bootVersion : metadataService.getMetadata().bootVersion().defaultValue();
        String java = javaVersion != null ? javaVersion : metadataService.getMetadata().javaVersion().defaultValue();

        metadataService.validateBootVersion(boot);
        metadataService.validateJavaVersion(java);
        for (String dep : dependencies) {
            metadataService.validateDependency(dep);
        }

        return ProjectRequest.builder()
                .name(resolvedName)
                .artifactId(resolvedArtifact)
                .groupId(groupId != null ? groupId : "com.example")
                .packageName(packageName)
                .description(description != null ? description : "Demo project for Spring Boot")
                .type(type)
                .language(language)
                .packaging(packaging)
                .bootVersion(boot)
                .javaVersion(java)
                .dependencies(dependencies)
                .build();
    }

    /** Optional post-generation conveniences (git / VS Code / build). Failures are warnings only. */
    private void runPostActions(Path targetDir, ProjectRequest request) {
        if (initGit) {
            runProcess(targetDir, "Initializing git repository...", "git", "init", "-q");
        }
        if (openInVsCode) {
            runProcess(targetDir, "Opening in VS Code...", "code", ".");
        }
        if (runBuild) {
            boolean gradle = request.type().startsWith("gradle");
            boolean win = ProcessUtils.isWindows();
            String wrapper = gradle ? (win ? "gradlew.bat" : "./gradlew") : (win ? "mvnw.cmd" : "./mvnw");
            String[] cmd = gradle ? new String[]{wrapper, "build"} : new String[]{wrapper, "clean", "install"};
            runProcess(targetDir, "Building project (" + wrapper + ")...", cmd);
        }
    }

    private void runProcess(Path workingDir, String message, String... command) {
        Ansi.info(message);
        try {
            Process process = new ProcessBuilder(ProcessUtils.platformCommand(command))
                    .directory(workingDir.toFile())
                    .inheritIO()
                    .start();
            int exit = process.waitFor();
            if (exit != 0) {
                Ansi.warn("Command '" + String.join(" ", command) + "' exited with code " + exit + ".");
            }
        } catch (IOException e) {
            Ansi.warn("Could not run '" + command[0] + "': " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpringCliException("Interrupted while running '" + command[0] + "'.", e);
        }
    }
}
