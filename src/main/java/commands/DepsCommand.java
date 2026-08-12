package commands;

import service.PomEditor;
import util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code springcli deps} — list the dependencies declared in an existing project's {@code pom.xml}.
 * A read-only, offline companion to {@link AddCommand}/{@link RemoveCommand}: it shows what is
 * currently in the project (the "installed" view), not the Initializr catalog that {@code list} shows.
 */
@Command(name = "deps", description = "List the dependencies declared in an existing project's pom.xml.")
public class DepsCommand implements Callable<Integer> {

    @Option(names = {"-f", "--file"}, paramLabel = "<pom>",
            description = "Path to the pom.xml to read (default: ./pom.xml).")
    private Path pomFile;

    private final PomEditor pomEditor = new PomEditor();

    @Override
    public Integer call() throws IOException {
        Path pom = pomFile != null ? pomFile : Path.of("pom.xml");
        if (!Files.isRegularFile(pom)) {
            if (pomFile == null && Files.isRegularFile(Path.of("build.gradle"))) {
                Ansi.error("Found build.gradle but 'deps' supports Maven (pom.xml) only for now.");
                return 2;
            }
            Ansi.error("No pom.xml found at " + pom.toAbsolutePath()
                    + ". Run inside a Maven project or pass --file <pom>.");
            return 2;
        }

        String pomXml = Files.readString(pom);
        String bootVersion = pomEditor.springBootParentVersion(pomXml);
        if (bootVersion != null) {
            System.out.println(Ansi.bold("Spring Boot " + bootVersion));
        }

        List<PomEditor.Dep> deps = pomEditor.dependencies(pomXml);
        if (deps.isEmpty()) {
            Ansi.warn("No dependencies declared in " + pom.getFileName() + ".");
            return 0;
        }

        System.out.println();
        for (PomEditor.Dep d : deps) {
            System.out.printf("  %-55s %s%n", d.key(), annotation(d));
        }
        System.out.println("\n" + deps.size() + (deps.size() == 1 ? " dependency" : " dependencies"));
        return 0;
    }

    /** A short " (scope, optional)"-style suffix for a dependency, or empty when it's a plain compile dep. */
    private static String annotation(PomEditor.Dep d) {
        StringBuilder tags = new StringBuilder();
        if (d.scope() != null) {
            tags.append(d.scope());
        }
        if (d.optional()) {
            tags.append(tags.length() > 0 ? ", " : "").append("optional");
        }
        return tags.length() == 0 ? "" : Ansi.yellow("(" + tags + ")");
    }
}
