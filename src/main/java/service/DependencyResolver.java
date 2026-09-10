package service;

import model.ProjectRequest;
import util.Ansi;

import java.util.List;

/**
 * Turns Initializr dependency ids (e.g. {@code postgresql}) into exact Maven coordinates. The client
 * metadata doesn't carry coordinates, so they're read from a reference {@code pom.xml} that
 * Initializr generates for just those ids.
 */
public class DependencyResolver {

    private final MetadataService metadataService;
    private final InitializrClient initializrClient;
    private final PomEditor pomEditor = new PomEditor();

    public DependencyResolver(MetadataService metadataService, InitializrClient initializrClient) {
        this.metadataService = metadataService;
        this.initializrClient = initializrClient;
    }

    /**
     * @return the Maven dependencies Initializr uses for {@code ids}
     * @throws exception.ValidationException if an id isn't a known Initializr dependency
     */
    public List<PomEditor.Dep> resolve(List<String> ids) {
        for (String id : ids) {
            metadataService.validateDependency(id);
        }

        Ansi.info("Resolving dependency coordinates...");
        ProjectRequest request = ProjectRequest.builder()
                .type("maven-project")
                .bootVersion(metadataService.getMetadata().bootVersion().defaultValue())
                .dependencies(ids)
                .build();
        return pomEditor.dependencies(initializrClient.fetchPom(request));
    }
}
