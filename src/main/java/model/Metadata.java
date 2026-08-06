package model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * In-memory representation of the Spring Initializr client metadata document served at
 * {@code https://start.spring.io/metadata/client}.
 *
 * <p>Only the fields consumed by this CLI are mapped; unknown properties are ignored so the model
 * stays resilient to server-side additions. The document mixes several shapes (single-select,
 * hierarchical-multi-select, text) which are represented here by the nested records below.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Metadata(
        @JsonProperty("dependencies") DependencyGroupContainer dependencies,
        @JsonProperty("type") SingleSelect type,
        @JsonProperty("packaging") SingleSelect packaging,
        @JsonProperty("javaVersion") SingleSelect javaVersion,
        @JsonProperty("language") SingleSelect language,
        @JsonProperty("bootVersion") SingleSelect bootVersion,
        @JsonProperty("groupId") TextField groupId,
        @JsonProperty("artifactId") TextField artifactId,
        @JsonProperty("version") TextField version,
        @JsonProperty("name") TextField name,
        @JsonProperty("description") TextField description,
        @JsonProperty("packageName") TextField packageName
) {

    /** A free-text field with a server-provided default (e.g. groupId, artifactId). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextField(@JsonProperty("default") String defaultValue) {
        public String orDefault(String value) {
            return (value == null || value.isBlank()) ? defaultValue : value;
        }
    }

    /** A single-select field: a default id plus the list of allowed values. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SingleSelect(
            @JsonProperty("default") String defaultValue,
            @JsonProperty("values") List<Option> values
    ) {
        public SingleSelect {
            values = values == null ? List.of() : List.copyOf(values);
        }

        /** @return {@code true} if {@code id} is one of the allowed option ids. */
        public boolean isValid(String id) {
            return values.stream().anyMatch(o -> o.id().equals(id));
        }
    }

    /** A selectable option with an id and human-readable name. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name
    ) {}

    /** Wrapper around the hierarchical dependency list ({@code values} = categories). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DependencyGroupContainer(@JsonProperty("values") List<DependencyGroup> values) {
        public DependencyGroupContainer {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    /** A named category of dependencies (e.g. "Web", "SQL"). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DependencyGroup(
            @JsonProperty("name") String name,
            @JsonProperty("values") List<Dependency> values
    ) {
        public DependencyGroup {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    /** A single Spring Initializr dependency ("starter"). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dependency(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("versionRange") String versionRange
    ) {}
}
