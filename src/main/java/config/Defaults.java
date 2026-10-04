package config;

import java.util.List;

public final class Defaults {

    private Defaults() {
    }

    public static final String TYPE = "maven-project";

    public static final String JAVA_VERSION = "21";

    public static final List<String> DEPENDENCIES =
            List.of("web", "data-jpa", "devtools", "validation", "lombok");
}
