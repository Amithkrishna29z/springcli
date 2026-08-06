package util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Helpers for launching external processes portably across Windows and POSIX shells. */
public final class ProcessUtils {

    private ProcessUtils() {
    }

    public static boolean isWindows() {
        return File.separatorChar == '\\';
    }

    /**
     * Adapts a command for the current platform. On Windows many developer tools are batch scripts
     * ({@code mvn.cmd}, {@code code.cmd}) that {@link ProcessBuilder} cannot locate directly because
     * it does not honour {@code PATHEXT}; wrapping them in {@code cmd.exe /c} lets Windows resolve
     * the extension. POSIX commands are returned unchanged.
     *
     * @param command the logical command and its arguments
     * @return the platform-adjusted command list
     */
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
}
