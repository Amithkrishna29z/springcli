package cli;

import service.InitializrClient;
import service.MetadataService;
import service.ProjectGenerator;
import service.ZipExtractor;

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
