package model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextField(@JsonProperty("default") String defaultValue) {
        public String orDefault(String value) {
            return (value == null || value.isBlank()) ? defaultValue : value;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SingleSelect(
            @JsonProperty("default") String defaultValue,
            @JsonProperty("values") List<Option> values
    ) {
        public SingleSelect {
            values = values == null ? List.of() : List.copyOf(values);
        }

        public boolean isValid(String id) {
            return values.stream().anyMatch(o -> o.id().equals(id));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DependencyGroupContainer(@JsonProperty("values") List<DependencyGroup> values) {
        public DependencyGroupContainer {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DependencyGroup(
            @JsonProperty("name") String name,
            @JsonProperty("values") List<Dependency> values
    ) {
        public DependencyGroup {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dependency(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("versionRange") String versionRange
    ) {}
}
