package commands;

import cli.ServiceFactory;
import model.Metadata;
import service.MetadataService;
import util.Ansi;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "list", description = "List all available dependencies grouped by category.")
public class ListCommand implements Callable<Integer> {

    private final MetadataService metadataService;

    public ListCommand() {
        this(new ServiceFactory().metadataService());
    }

    public ListCommand(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public Integer call() {
        Ansi.info("Fetching Spring metadata...");
        System.out.println();
        for (Metadata.DependencyGroup group : metadataService.dependencyGroups()) {
            System.out.println(Ansi.bold(group.name()));
            for (Metadata.Dependency d : group.values()) {
                System.out.printf("  %-28s %s%n", d.id(), d.name());
            }
            System.out.println();
        }
        return 0;
    }
}
