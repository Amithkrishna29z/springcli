package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import exception.SpringCliException;
import exception.ValidationException;
import model.Metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class MetadataService {

    private final InitializrClient client;
    private final ObjectMapper objectMapper;

    private Metadata cached;

    public MetadataService(InitializrClient client) {
        this.client = client;
        this.objectMapper = new ObjectMapper();
    }

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
