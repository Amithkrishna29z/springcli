package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import exception.SpringCliException;
import exception.ValidationException;
import model.Metadata;
import util.Ansi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class MetadataService {

    private final InitializrClient client;
    private final MetadataCache cache;
    private final ObjectMapper objectMapper;

    private Metadata cached;

    public MetadataService(InitializrClient client) {
        this(client, MetadataCache.disabled());
    }

    public MetadataService(InitializrClient client, MetadataCache cache) {
        this.client = client;
        this.cache = cache;
        this.objectMapper = new ObjectMapper();
    }

    public Metadata getMetadata() {
        if (cached == null) {
            String json = loadJson();
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

    /**
     * Resolves the metadata JSON: a fresh disk cache if available, otherwise a network fetch (which
     * is written back to the cache). If the network is unreachable, falls back to a stale cache entry
     * so the tool keeps working offline.
     */
    private String loadJson() {
        Optional<String> fresh = cache.readFresh();
        if (fresh.isPresent()) {
            return fresh.get();
        }
        try {
            String json = client.fetchMetadata();
            cache.write(json);
            return json;
        } catch (SpringCliException networkError) {
            Optional<String> stale = cache.readAny();
            if (stale.isPresent()) {
                Ansi.warn("Couldn't reach Spring Initializr; using cached metadata.");
                return stale.get();
            }
            throw networkError;
        }
    }

    public List<Metadata.Dependency> allDependencies() {
        List<Metadata.Dependency> all = new ArrayList<>();
        for (Metadata.DependencyGroup group : getMetadata().dependencies().values()) {
            all.addAll(group.values());
        }
        return all;
    }

    public List<Metadata.DependencyGroup> dependencyGroups() {
        return getMetadata().dependencies().values();
    }

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

    public Optional<Metadata.Dependency> findDependency(String id) {
        return allDependencies().stream().filter(d -> d.id().equals(id)).findFirst();
    }

    public void validateDependency(String id) {
        if (findDependency(id).isEmpty()) {
            throw new ValidationException("Unknown dependency: '" + id + "'. Run 'springcli search " + id + "' to find valid ids.");
        }
    }

    public void validateBootVersion(String version) {
        if (!getMetadata().bootVersion().isValid(version)) {
            throw new ValidationException("Invalid Spring Boot version: '" + version + "'.");
        }
    }

    public void validateJavaVersion(String version) {
        if (!getMetadata().javaVersion().isValid(version)) {
            throw new ValidationException("Invalid Java version: '" + version + "'.");
        }
    }

    private static boolean contains(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }
}
