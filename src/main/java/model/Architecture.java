package model;

import exception.ValidationException;

import java.util.List;

/**
 * Optional package skeleton created inside the generated project. Spring Initializr always emits a
 * flat package containing only the application class; picking anything other than {@link #NONE}
 * adds the empty package directories for that style so a project starts out with a shape.
 */
public enum Architecture {

    NONE("none", "None (flat) - just the Initializr layout", List.of()),

    LAYERED("layered", "Layered - controller / service / repository", List.of(
            "config",
            "controller",
            "dto",
            "exception",
            "model",
            "repository",
            "service")),

    CLEAN("clean", "Clean - domain / application / infrastructure / presentation", List.of(
            "application/dto",
            "application/service",
            "domain/model",
            "domain/repository",
            "infrastructure/config",
            "infrastructure/persistence",
            "presentation/controller")),

    HEXAGONAL("hexagonal", "Hexagonal - ports and adapters", List.of(
            "adapter/inbound/web",
            "adapter/outbound/persistence",
            "application/port/inbound",
            "application/port/outbound",
            "application/service",
            "config",
            "domain/model"));

    private final String id;
    private final String displayName;
    private final List<String> packages;

    Architecture(String id, String displayName, List<String> packages) {
        this.id = id;
        this.displayName = displayName;
        this.packages = packages;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** @return package paths relative to the project's base package, using '/' as separator. */
    public List<String> packages() {
        return packages;
    }

    /**
     * @param id an architecture id, case-insensitive
     * @throws ValidationException if {@code id} is not a known architecture
     */
    public static Architecture fromId(String id) {
        for (Architecture a : values()) {
            if (a.id.equalsIgnoreCase(id)) {
                return a;
            }
        }
        throw new ValidationException("Unknown architecture '" + id + "'. Valid values: " + ids() + ".");
    }

    /** @return the ids as a comma-separated list, for help and error messages. */
    public static String ids() {
        StringBuilder sb = new StringBuilder();
        for (Architecture a : values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(a.id);
        }
        return sb.toString();
    }

    /** @return the architectures as a {@link Metadata.SingleSelect} so the wizard can list them. */
    public static Metadata.SingleSelect asSelect() {
        return new Metadata.SingleSelect(NONE.id,
                java.util.Arrays.stream(values())
                        .map(a -> new Metadata.Option(a.id, a.displayName))
                        .toList());
    }
}
