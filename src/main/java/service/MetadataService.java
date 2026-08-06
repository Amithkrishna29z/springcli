package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import exception.SpringCliException;
import exception.ValidationException;
import model.Metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Fetches, parses and caches the Spring Initializr metadata for the lifetime of a single CLI
 * execution, and exposes convenience queries over it (dependency search, listing, validation).
 *
 * <p>Depends on {@link InitializrClient} for transport and Jackson for parsing, so it can be unit
 * tested with a mocked client. The parsed {@link Metadata} is cached in memory; repeated calls
 * within one run do not hit the network.</p>
 */
public class MetadataService {

    private final InitializrClient client;
    private final ObjectMapper objectMapper;

    /** Lazily-populated in-memory cache, valid for the current execution only. */
    private Metadata cached;

    public MetadataService(InitializrClient client) {
        this.client = client;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Returns the parsed metadata, fetching and caching it on first use.
     *
     * @throws SpringCliException if the metadata cannot be fetched or parsed
     */
    public Metadata getMetadata() {
        if (cached == null) {
            String json = client.fetchMetadata();
            try {
                cached = objectMapper.readValue(json, Metadata.class);
            } catch (Exception e) {
                throw new SpringCliException(
                        "Received malformed metadata from Spring Initializr; the service contract may have changed.", e);
            }
            if (cached == null || cached.bootVersion() == null || cached.dependencies() == null) {
                throw new SpringCliException("Spring Initializr metadata is missing required fields.");
            }
        }
        return cached;
    }

    /** @return every dependency across all categories, flattened. */
    public List<Metadata.Dependency> allDependencies() {
        List<Metadata.Dependency> all = new ArrayList<>();
        for (Metadata.DependencyGroup group : getMetadata().dependencies().values()) {
            all.addAll(group.values());
        }
        return all;
    }

    /** @return the dependency categories in their original grouped form (for {@code list}). */
    public List<Metadata.DependencyGroup> dependencyGroups() {
        return getMetadata().dependencies().values();
    }

    /**
     * Case-insensitive substring search over dependency id, name and description.
     *
     * @param query the search term; blank returns all dependencies
     * @return matching dependencies, preserving metadata order
     */
    public List<Metadata.Dependency> searchDependencies(String query) {
        if (query == null || query.isBlank()) {
            return allDependencies();
        }
        String q = query.toLowerCase(Locale.ROOT);
        List<Metadata.Dependency> matches = new ArrayList<>();
        for (Metadata.Dependency d : allDependencies()) {
            if (contains(d.id(), q) || contains(d.name(), q) || contains(d.description(), q)) {
                matches.add(d);
            }
        }
        return matches;
    }

    /** @return the dependency with the given id, if present. */
    public Optional<Metadata.Dependency> findDependency(String id) {
        return allDependencies().stream().filter(d -> d.id().equals(id)).findFirst();
    }

    /**
     * Validates that a dependency id is known.
     *
     * @throws ValidationException if the id is not a recognised dependency
     */
    public void validateDependency(String id) {
        if (findDependency(id).isEmpty()) {
            throw new ValidationException("Unknown dependency: '" + id + "'. Run 'springcli search " + id + "' to find valid ids.");
        }
    }

    /**
     * Validates a Spring Boot version id against the metadata.
     *
     * @throws ValidationException if the version is not offered by Initializr
     */
    public void validateBootVersion(String version) {
        if (!getMetadata().bootVersion().isValid(version)) {
            throw new ValidationException("Invalid Spring Boot version: '" + version + "'.");
        }
    }

    /**
     * Validates a Java version id against the metadata.
     *
     * @throws ValidationException if the version is not offered by Initializr
     */
    public void validateJavaVersion(String version) {
        if (!getMetadata().javaVersion().isValid(version)) {
            throw new ValidationException("Invalid Java version: '" + version + "'.");
        }
    }

    private static boolean contains(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }
}
