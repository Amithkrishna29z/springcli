package audit;

import exception.UsageException;
import model.Artifact;
import service.PomEditor;
import service.PomFile;
import util.FileUtils;
import util.ProcessUtils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves dependencies by running {@code dependency:tree} with the project's own Maven wrapper (or
 * {@code mvn} from the PATH), so versions come out exactly as Maven picks them — including everything
 * the Spring Boot parent manages and every transitive dependency.
 */
public class MavenArtifactResolver implements ArtifactResolver {

    /** Directories never copied into the scratch project: build output and VCS/IDE metadata. */
    private static final Set<String> SKIPPED_DIRS = Set.of("target", "build", ".git", ".idea", "node_modules");

    private final PomEditor pomEditor = new PomEditor();

    @Override
    public List<Artifact> resolve(PomFile pom) {
        Path pomPath = pom.path().toAbsolutePath();
        return resolveIn(pomPath.getParent(), pomPath.getFileName().toString());
    }

    /** Resolves a scratch copy of the project's build files with the parent version changed. */
    @Override
    public List<Artifact> resolveWithSpringBoot(PomFile pom, String springBootVersion) {
        String xml = pomEditor.setSpringBootParentVersion(pom.xml(), springBootVersion);
        if (xml == null) {
            throw new UsageException(pom.fileName() + " has no Spring Boot <parent> version to change.");
        }
        Path pomPath = pom.path().toAbsolutePath();
        String pomName = pomPath.getFileName().toString();
        Path scratch = null;
        try {
            scratch = Files.createTempDirectory("springcli-audit");
            copyBuildFiles(pomPath.getParent(), scratch, pomName);
            Files.writeString(scratch.resolve(pomName), xml);
            return resolveIn(scratch, pomName);
        } catch (IOException e) {
            throw new UsageException("Couldn't prepare a scratch copy of the project: " + e.getMessage());
        } finally {
            FileUtils.deleteQuietly(scratch);
        }
    }

    private List<Artifact> resolveIn(Path projectDir, String pomName) {
        Path tree = null;
        try {
            tree = Files.createTempFile("springcli-deps", ".txt");
            List<String> command = new ArrayList<>(mavenLauncher(projectDir));
            command.addAll(List.of("-q", "-B", "-f", pomName, "dependency:tree",
                    "-DoutputFile=" + tree, "-DappendOutput=true"));
            ProcessUtils.Result result = ProcessUtils.runCapturing(projectDir, command.toArray(String[]::new));
            if (result.exitCode() != 0) {
                throw new UsageException("Maven couldn't resolve the project's dependencies (exit code "
                        + result.exitCode() + "):\n" + lastLines(result.output(), 15));
            }
            return parseTree(Files.readString(tree));
        } catch (IOException e) {
            throw new UsageException("Couldn't run Maven (" + e.getMessage()
                    + "). Install Maven or add the Maven wrapper (mvnw) to the project.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UsageException("Interrupted while resolving dependencies.");
        } finally {
            FileUtils.deleteQuietly(tree);
        }
    }

    /** @return the command that starts Maven: the project's wrapper if it has one, else {@code mvn} */
    static List<String> mavenLauncher(Path projectDir) {
        if (ProcessUtils.isWindows()) {
            // A relative path from the project directory: cmd.exe mangles quoted paths with spaces, and
            // may not search the current directory for a bare name (NoDefaultCurrentDirectoryInExePath).
            return Files.isRegularFile(projectDir.resolve("mvnw.cmd")) ? List.of(".\\mvnw.cmd") : List.of("mvn");
        }
        // Through sh, because an unzipped wrapper often lacks the executable bit.
        return Files.isRegularFile(projectDir.resolve("mvnw")) ? List.of("sh", "mvnw") : List.of("mvn");
    }

    /**
     * Parses {@code dependency:tree} text output. Each module's root line has no tree prefix; each level
     * below it adds three characters ({@code "+- "}, {@code "\- "}, {@code "|  "} or {@code "   "}).
     * Test-scoped artifacts are dropped, and an artifact listed by several modules is kept once.
     */
    static List<Artifact> parseTree(String tree) {
        Map<String, Artifact> artifacts = new LinkedHashMap<>();
        String directDependency = null;
        for (String line : tree.split("\\R")) {
            int start = 0;
            while (start < line.length() && "|+\\- ".indexOf(line.charAt(start)) >= 0) {
                start++;
            }
            if (start == 0 || start == line.length()) {
                continue; // a module's root line, or blank
            }
            // e.g. "org.x:y:jar:1.0:compile", "org.x:y:jar:classifier:1.0:runtime (optional)"
            String[] parts = line.substring(start).split("\\s")[0].split(":");
            if (parts.length < 5) {
                continue;
            }
            String key = parts[0] + ":" + parts[1];
            boolean direct = start == 3;
            if (direct) {
                directDependency = key;
            }
            String scope = parts[parts.length - 1];
            if (scope.equals("test")) {
                continue;
            }
            Artifact artifact = new Artifact(parts[0], parts[1], parts[parts.length - 2], scope,
                    direct ? null : directDependency);
            artifacts.putIfAbsent(artifact.coordinates(), artifact);
        }
        return new ArrayList<>(artifacts.values());
    }

    /** Copies what Maven needs to resolve dependencies: the poms and the Maven wrapper. */
    private static void copyBuildFiles(Path from, Path to, String pomName) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                boolean skip = !dir.equals(from) && SKIPPED_DIRS.contains(dir.getFileName().toString());
                return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = from.relativize(file);
                String name = relative.getFileName().toString();
                if (name.equals("pom.xml") || name.equals("mvnw") || name.equals("mvnw.cmd")
                        || relative.toString().equals(pomName) || relative.startsWith(".mvn")) {
                    Path target = to.resolve(relative.toString());
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String lastLines(String text, int count) {
        List<String> lines = text.lines().filter(l -> !l.isBlank()).toList();
        return String.join("\n", lines.subList(Math.max(0, lines.size() - count), lines.size()));
    }
}
