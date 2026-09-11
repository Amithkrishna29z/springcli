package audit;

import model.Artifact;
import service.PomFile;

import java.util.List;

/** Lists the artifacts a project really depends on: direct, managed and transitive. */
public interface ArtifactResolver {

    /**
     * @return the non-test artifacts {@code pom} resolves to
     * @throws exception.UsageException if the dependencies can't be resolved
     */
    List<Artifact> resolve(PomFile pom);

    /**
     * Like {@link #resolve}, but as if the Spring Boot parent were {@code springBootVersion}. The
     * project itself is not modified.
     *
     * @throws exception.UsageException if the dependencies can't be resolved
     */
    List<Artifact> resolveWithSpringBoot(PomFile pom, String springBootVersion);
}
