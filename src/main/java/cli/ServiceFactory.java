package cli;

import service.InitializrClient;
import service.MetadataService;
import service.ProjectGenerator;
import service.ZipExtractor;

/**
 * Tiny composition root that constructs and wires the service graph. Centralising construction here
 * keeps commands decoupled from concrete wiring and makes it easy to swap the Initializr base URL
 * (e.g. via {@code SPRINGCLI_BASE_URL}) for testing against a stub.
 */
public class ServiceFactory {

    private final InitializrClient initializrClient;
    private final MetadataService metadataService;
    private final ProjectGenerator projectGenerator;

    public ServiceFactory() {
        String baseUrl = System.getenv().getOrDefault("SPRINGCLI_BASE_URL", InitializrClient.DEFAULT_BASE_URL);
        this.initializrClient = new InitializrClient(
                java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(15))
                        .build(),
                baseUrl);
        this.metadataService = new MetadataService(initializrClient);
        this.projectGenerator = new ProjectGenerator(initializrClient, new ZipExtractor());
    }

    public MetadataService metadataService() {
        return metadataService;
    }

    public ProjectGenerator projectGenerator() {
        return projectGenerator;
    }
}
