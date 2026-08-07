package commands;

import config.Defaults;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the default-dependency policy applied to every new project. */
class NewCommandDefaultsTest {

    @Test
    void defaultSetMatchesPolicy() {
        assertEquals(List.of("web", "data-jpa", "devtools", "validation", "lombok"), Defaults.DEPENDENCIES);
    }

    @Test
    void resolveUsesDefaultsWhenNoneProvided() {
        assertEquals(Defaults.DEPENDENCIES, NewCommand.resolveDependencies(List.of()));
        assertEquals(Defaults.DEPENDENCIES, NewCommand.resolveDependencies(null));
    }

    @Test
    void resolveUsesExplicitDepsWhenProvided() {
        List<String> explicit = List.of("web", "actuator");
        assertEquals(explicit, NewCommand.resolveDependencies(explicit));
    }
}
