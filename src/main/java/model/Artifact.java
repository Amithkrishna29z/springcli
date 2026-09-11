package model;

/**
 * A resolved Maven artifact.
 *
 * @param scope Maven scope (compile, runtime, ...), or {@code null} when the pom doesn't say
 * @param via   {@code groupId:artifactId} of the direct dependency that pulled this one in, or
 *              {@code null} when it is itself a direct dependency
 */
public record Artifact(String groupId, String artifactId, String version, String scope, String via) {

    public String key() {
        return groupId + ":" + artifactId;
    }

    public String coordinates() {
        return key() + ":" + version;
    }
}
