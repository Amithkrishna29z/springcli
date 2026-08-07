package util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ProcessUtils {

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
}
