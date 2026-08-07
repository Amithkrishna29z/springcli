package model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * User-level defaults persisted to {@code ~/.springcli/config.json}. Every field is optional; an
 * unset field means "fall back to the built-in/metadata default". Only non-null fields are written,
 * keeping the file minimal.
 *
 * <p>The keyed {@link #get}/{@link #set}/{@link #unset} accessors back the {@code springcli config}
 * command so key handling lives in one place.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class UserConfig {

    /** Configurable keys, in display order. */
    public static final List<String> KEYS =
            List.of("groupId", "javaVersion", "language", "packaging", "type", "dependencies");

    private String groupId;
    private String javaVersion;
    private String language;
    private String packaging;
    private String type;
    private List<String> dependencies;

    public String getGroupId() { return groupId; }
    public void setGroupId(String v) { this.groupId = v; }

    public String getJavaVersion() { return javaVersion; }
    public void setJavaVersion(String v) { this.javaVersion = v; }

    public String getLanguage() { return language; }
    public void setLanguage(String v) { this.language = v; }

    public String getPackaging() { return packaging; }
    public void setPackaging(String v) { this.packaging = v; }

    public String getType() { return type; }
    public void setType(String v) { this.type = v; }

    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> v) { this.dependencies = v; }

    public static boolean isKey(String key) {
        return KEYS.contains(key);
    }

    /** @return the value for {@code key} as a display string, or {@code null} if unset. */
    public String get(String key) {
        return switch (key) {
            case "groupId" -> groupId;
            case "javaVersion" -> javaVersion;
            case "language" -> language;
            case "packaging" -> packaging;
            case "type" -> type;
            case "dependencies" -> dependencies == null ? null : String.join(",", dependencies);
            default -> throw new IllegalArgumentException("Unknown key: " + key);
        };
    }

    /** Sets {@code key} to {@code value} (dependencies accept a comma-separated list). */
    public void set(String key, String value) {
        switch (key) {
            case "groupId" -> groupId = value;
            case "javaVersion" -> javaVersion = value;
            case "language" -> language = value;
            case "packaging" -> packaging = value;
            case "type" -> type = value;
            case "dependencies" -> dependencies = splitCsv(value);
            default -> throw new IllegalArgumentException("Unknown key: " + key);
        }
    }

    /** Clears {@code key}. */
    public void unset(String key) {
        set(key, null);
    }

    private static List<String> splitCsv(String value) {
        if (value == null) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (String token : value.split(",")) {
            String s = token.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }
}
