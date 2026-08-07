package cli;

import service.InitializrClient;
import service.MetadataCache;
import service.MetadataService;
import service.ProjectGenerator;
import service.ZipExtractor;

import java.nio.file.Path;
import java.time.Duration;

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
        MetadataCache cache = System.getenv("SPRINGCLI_NO_CACHE") != null
                ? MetadataCache.disabled()
                : MetadataCache.onDisk(
                        Path.of(System.getProperty("user.home"), ".springcli", "metadata-cache.json"),
                        Duration.ofHours(24));
        this.metadataService = new MetadataService(initializrClient, cache);
        this.projectGenerator = new ProjectGenerator(initializrClient, new ZipExtractor());
    }

    public MetadataService metadataService() {
        return metadataService;
    }

    public ProjectGenerator projectGenerator() {
        return projectGenerator;
    }
}
