package config;

import java.util.List;

/**
 * Application-wide defaults. Centralising them here keeps the wizard and the flag-driven path in
 * agreement and makes the policy easy to find and change.
 */
public final class Defaults {

    private Defaults() {
    }

    /**
     * Dependencies pre-selected for every new project, by Spring Initializr id. Used as the initial
     * selection in the interactive wizard (where they can be toggled off) and as the fallback when
     * {@code springcli new --yes} is run without an explicit {@code --deps} list.
     */
    public static final List<String> DEPENDENCIES =
            List.of("web", "data-jpa", "devtools", "validation", "lombok");
}
