package commands;

import cli.ServiceFactory;
import config.Defaults;
import exception.SpringCliException;
import model.ProjectRequest;
import model.UserConfig;
import prompts.InteractiveWizard;
import service.ConfigService;
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
    private String type;

    @Option(names = "--language", description = "java, kotlin or groovy.")
    private String language;

    @Option(names = "--packaging", description = "jar or war.")
    private String packaging;

    @Option(names = "--boot-version", description = "Spring Boot version (defaults to Initializr default).")
    private String bootVersion;

    @Option(names = "--java-version", description = "Java version (defaults to Initializr default).")
    private String javaVersion;

    @Option(names = "--deps", split = ",",
            description = "Comma-separated dependency ids, e.g. web,data-jpa. "
                    + "If omitted, a sensible default set is used (web, data-jpa, devtools, validation, lombok).")
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
    private final ConfigService configService;

    private UserConfig config;

    public NewCommand() {
        ServiceFactory factory = new ServiceFactory();
        this.metadataService = factory.metadataService();
        this.projectGenerator = factory.projectGenerator();
        this.configService = new ConfigService();
    }

    public NewCommand(MetadataService metadataService, ProjectGenerator projectGenerator, ConfigService configService) {
        this.metadataService = metadataService;
        this.projectGenerator = projectGenerator;
        this.configService = configService;
    }

    @Override
    public Integer call() {
        Ansi.info("Fetching Spring metadata...");

        metadataService.getMetadata();
        this.config = configService.load();

        ProjectRequest request = nonInteractive ? buildFromFlags() : runWizard();
        if (request == null) {
            return 0;
        }

        Path targetDir = Path.of(request.artifactId());
        Ansi.info("Selecting dependencies...");
        projectGenerator.generate(request, targetDir, force);

        Ansi.success("Project created successfully at ./" + targetDir);

        runPostActions(targetDir, request);
        return 0;
    }

    private ProjectRequest runWizard() {
        return new InteractiveWizard(metadataService, config).run(name);
    }

    private ProjectRequest buildFromFlags() {
        String resolvedName = name != null ? name : "demo";
        String resolvedArtifact = artifactId != null ? artifactId : resolvedName;
        String boot = bootVersion != null ? bootVersion : metadataService.getMetadata().bootVersion().defaultValue();
        String java = firstNonBlank(javaVersion, config.getJavaVersion(), metadataService.getMetadata().javaVersion().defaultValue());
        String group = firstNonBlank(groupId, config.getGroupId(), "com.example");
        String lang = firstNonBlank(language, config.getLanguage(), "java");
        String pack = firstNonBlank(packaging, config.getPackaging(), "jar");
        String buildType = firstNonBlank(type, config.getType(), "maven-project");

        List<String> deps = resolveDependencies(dependencies, config.getDependencies());

        metadataService.validateBootVersion(boot);
        metadataService.validateJavaVersion(java);
        for (String dep : deps) {
            metadataService.validateDependency(dep);
        }

        return ProjectRequest.builder()
                .name(resolvedName)
                .artifactId(resolvedArtifact)
                .groupId(group)
                .packageName(packageName)
                .description(description != null ? description : "Demo project for Spring Boot")
                .type(buildType)
                .language(lang)
                .packaging(pack)
                .bootVersion(boot)
                .javaVersion(java)
                .dependencies(deps)
                .build();
    }

    /**
     * Resolves the effective dependency list with precedence: explicit {@code --deps} &gt; saved
     * config default &gt; built-in {@link Defaults#DEPENDENCIES}. Package-visible for testing.
     */
    static List<String> resolveDependencies(List<String> provided, List<String> configDefault) {
        if (provided != null && !provided.isEmpty()) {
            return provided;
        }
        if (configDefault != null && !configDefault.isEmpty()) {
            return configDefault;
        }
        return Defaults.DEPENDENCIES;
    }

    /** @return the first argument that is non-null and non-blank. */
    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

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
