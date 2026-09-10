package util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProcessUtils {

    /** How a captured process finished: its exit code and combined stdout/stderr. */
    public record Result(int exitCode, String output) {
    }

    private ProcessUtils() {
    }

    public static boolean isWindows() {
        return File.separatorChar == '\\';
    }

    public static List<String> platformCommand(String... command) {
        if (!isWindows()) {
            return List.of(command);
        }
        List<String> wrapped = new ArrayList<>(command.length + 2);
        wrapped.add("cmd.exe");
        wrapped.add("/c");
        for (String arg : command) {
            wrapped.add(arg);
        }
        return wrapped;
    }

    /**
     * Runs {@code command} with the terminal attached and waits for it to finish.
     *
     * @param workingDir the directory to run in, or {@code null} for the current one
     * @return the exit code
     * @throws IOException if the command can't be started (e.g. it isn't on the PATH)
     */
    public static int runInteractive(Path workingDir, String... command) throws IOException, InterruptedException {
        return new ProcessBuilder(platformCommand(command))
                .directory(workingDir == null ? null : workingDir.toFile())
                .inheritIO()
                .start()
                .waitFor();
    }

    /**
     * Runs {@code command}, capturing its combined output, and waits for it to finish.
     *
     * @throws IOException if the command can't be started (e.g. it isn't on the PATH)
     */
    public static Result runCapturing(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(platformCommand(command)).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        return new Result(process.waitFor(), output);
    }
}
