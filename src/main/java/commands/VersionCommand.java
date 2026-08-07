package commands;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * {@code springcli version} — prints the CLI version and runtime information.
 */
@Command(name = "version", description = "Show the springcli version.")
public class VersionCommand implements Callable<Integer> {

    /** Kept in sync with the Maven project version. */
    public static final String VERSION = "1.1.0";

    @Override
    public Integer call() {
        System.out.println("springcli " + VERSION);
        System.out.println("Java " + System.getProperty("java.version")
                + " (" + System.getProperty("os.name") + ")");
        return 0;
    }
}
