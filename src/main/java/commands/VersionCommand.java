package commands;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "version", description = "Show the springcli version.")
public class VersionCommand implements Callable<Integer> {

    public static final String VERSION = "1.2.0";

    @Override
    public Integer call() {
        System.out.println("springcli " + VERSION);
        System.out.println("Java " + System.getProperty("java.version")
                + " (" + System.getProperty("os.name") + ")");
        return 0;
    }
}
