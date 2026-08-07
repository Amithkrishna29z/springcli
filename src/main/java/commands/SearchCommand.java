package commands;

import cli.ServiceFactory;
import model.Metadata;
import service.MetadataService;
import util.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "search", description = "Search available Spring Boot dependencies.")
public class SearchCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "1", paramLabel = "<term>", description = "Search term, e.g. 'web'.")
    private String term;

    private final MetadataService metadataService;

    public SearchCommand() {
        this(new ServiceFactory().metadataService());
    }

    public SearchCommand(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public Integer call() {
        Ansi.info("Fetching Spring metadata...");
        List<Metadata.Dependency> results = metadataService.searchDependencies(term);
        if (results.isEmpty()) {
            Ansi.warn("No dependencies match '" + term + "'.");
            return 0;
        }
        System.out.println("\nResults for \"" + term + "\":\n");
        for (Metadata.Dependency d : results) {
            System.out.println(Ansi.green("✔ ") + Ansi.bold(d.name()) + "  [" + d.id() + "]");
            if (d.description() != null && !d.description().isBlank()) {
                System.out.println("    " + d.description());
            }
        }
        System.out.println("\n" + results.size() + " match(es).");
        return 0;
    }
}
