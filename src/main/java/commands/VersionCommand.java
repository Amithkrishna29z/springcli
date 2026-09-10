package commands;

import config.BuildInfo;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "version", description = "Show the springcli version.")
public class VersionCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("springcli " + BuildInfo.VERSION);
        System.out.println("Java " + System.getProperty("java.version")
                + " (" + System.getProperty("os.name") + ")");
        return 0;
    }
}
