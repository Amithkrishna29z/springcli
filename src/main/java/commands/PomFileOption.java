package commands;

import exception.UsageException;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import service.PomFile;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The {@code -f/--file} option shared by every command that works on an existing Maven project, and
 * the lookup that turns it into a {@link PomFile}. Add it to a command with {@code @Mixin}.
 */
public class PomFileOption {

    @Spec(Spec.Target.MIXEE)
    private CommandSpec command;

    @Option(names = {"-f", "--file"}, paramLabel = "<pom>",
            description = "Path to the project's pom.xml (default: ./pom.xml).")
    private Path file;

    /**
     * @return the pom given by {@code --file}, or {@code ./pom.xml}
     * @throws UsageException if that file doesn't exist
     */
    public PomFile load() {
        Path pom = file != null ? file : Path.of("pom.xml");
        if (!Files.isRegularFile(pom)) {
            if (file == null && Files.isRegularFile(Path.of("build.gradle"))) {
                throw new UsageException("Found build.gradle but '" + command.name()
                        + "' supports Maven (pom.xml) only for now.");
            }
            throw new UsageException("No pom.xml found at " + pom.toAbsolutePath()
                    + ". Run inside a Maven project or pass --file <pom>.");
        }
        return PomFile.read(pom);
    }
}
