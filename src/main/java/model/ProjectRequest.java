package model;

import java.util.List;
import java.util.Objects;

/**
 * The fully-resolved set of options used to generate a project. Instances are immutable and built
 * via {@link Builder}; every field maps directly to a query parameter of the Initializr
 * {@code /starter.zip} endpoint.
 */
public final class ProjectRequest {

    private final String type;        // e.g. maven-project, gradle-project
    private final String language;    // java, kotlin, groovy
    private final String bootVersion;
    private final String groupId;
    private final String artifactId;
    private final String name;
    private final String description;
    private final String packageName;
    private final String packaging;   // jar, war
    private final String javaVersion;
    private final List<String> dependencies;

    private ProjectRequest(Builder b) {
        this.type = b.type;
        this.language = b.language;
        this.bootVersion = b.bootVersion;
        this.groupId = b.groupId;
        this.artifactId = b.artifactId;
        this.name = b.name;
        this.description = b.description;
        this.packageName = b.packageName;
        this.packaging = b.packaging;
        this.javaVersion = b.javaVersion;
        this.dependencies = List.copyOf(b.dependencies);
    }

    public String type() { return type; }
    public String language() { return language; }
    public String bootVersion() { return bootVersion; }
    public String groupId() { return groupId; }
    public String artifactId() { return artifactId; }
    public String name() { return name; }
    public String description() { return description; }
    public String packageName() { return packageName; }
    public String packaging() { return packaging; }
    public String javaVersion() { return javaVersion; }
    public List<String> dependencies() { return dependencies; }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder for {@link ProjectRequest}. */
    public static final class Builder {
        private String type = "maven-project";
        private String language = "java";
        private String bootVersion;
        private String groupId = "com.example";
        private String artifactId = "demo";
        private String name = "demo";
        private String description = "Demo project for Spring Boot";
        private String packageName;
        private String packaging = "jar";
        private String javaVersion = "21";
        private List<String> dependencies = List.of();

        public Builder type(String v) { this.type = v; return this; }
        public Builder language(String v) { this.language = v; return this; }
        public Builder bootVersion(String v) { this.bootVersion = v; return this; }
        public Builder groupId(String v) { this.groupId = v; return this; }
        public Builder artifactId(String v) { this.artifactId = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder packageName(String v) { this.packageName = v; return this; }
        public Builder packaging(String v) { this.packaging = v; return this; }
        public Builder javaVersion(String v) { this.javaVersion = v; return this; }
        public Builder dependencies(List<String> v) { this.dependencies = v == null ? List.of() : v; return this; }

        public ProjectRequest build() {
            Objects.requireNonNull(bootVersion, "bootVersion must be set");
            if (packageName == null || packageName.isBlank()) {
                packageName = groupId + "." + artifactId.replaceAll("[^a-zA-Z0-9]", "");
            }
            return new ProjectRequest(this);
        }
    }
}
