package cli;

import audit.Auditor;
import audit.MavenArtifactResolver;
import config.BuildInfo;
import service.ArchitectureScaffolder;
import service.ConfigService;
import service.DependencyResolver;
import service.HttpSupport;
import service.InitializrClient;
import service.MetadataCache;
import service.MetadataService;
import service.ProjectGenerator;
import service.UpdateNotifier;
import service.UpdateService;
import service.VulnerabilityService;
import service.ZipExtractor;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Composition root: builds every service once, wired together and sharing one {@link HttpClient},
 * and is the only place that reads the environment-driven settings (Initializr URL, cache opt-out,
 * the {@code ~/.springcli} directory). {@link CommandFactory} hands these instances to commands.
 */
public class ServiceFactory {

    private final MetadataService metadataService;
    private final ProjectGenerator projectGenerator;
    private final DependencyResolver dependencyResolver;
    private final ConfigService configService;
    private final Auditor auditor;
    private final UpdateService updateService;
    private final UpdateNotifier updateNotifier;

    public ServiceFactory() {
        Path home = Path.of(System.getProperty("user.home"), ".springcli");
        HttpClient http = HttpSupport.newClient();

        String baseUrl = System.getenv().getOrDefault("SPRINGCLI_BASE_URL", InitializrClient.DEFAULT_BASE_URL);
        InitializrClient initializrClient = new InitializrClient(http, baseUrl);
        MetadataCache cache = System.getenv("SPRINGCLI_NO_CACHE") != null
                ? MetadataCache.disabled()
                : MetadataCache.onDisk(home.resolve("metadata-cache.json"), Duration.ofHours(24));

        this.metadataService = new MetadataService(initializrClient, cache);
        this.projectGenerator = new ProjectGenerator(initializrClient, new ZipExtractor(), new ArchitectureScaffolder());
        this.dependencyResolver = new DependencyResolver(metadataService, initializrClient);
        this.configService = new ConfigService(home.resolve("config.json"));
        this.auditor = new Auditor(new MavenArtifactResolver(),
                new VulnerabilityService(http, VulnerabilityService.DEFAULT_API), metadataService);
        this.updateService = new UpdateService(
                http, UpdateService.DEFAULT_API, UpdateService.DEFAULT_REPO, BuildInfo.VERSION);
        this.updateNotifier = new UpdateNotifier(
                updateService, home.resolve("update-check.json"), Duration.ofHours(24));
    }

    public MetadataService metadataService() {
        return metadataService;
    }

    public ProjectGenerator projectGenerator() {
        return projectGenerator;
    }

    public DependencyResolver dependencyResolver() {
        return dependencyResolver;
    }

    public ConfigService configService() {
        return configService;
    }

    public Auditor auditor() {
        return auditor;
    }

    public UpdateService updateService() {
        return updateService;
    }

    public UpdateNotifier updateNotifier() {
        return updateNotifier;
    }
}
