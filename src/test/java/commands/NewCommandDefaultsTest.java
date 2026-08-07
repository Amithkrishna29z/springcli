package commands;

import config.Defaults;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewCommandDefaultsTest {

    @Test
    void defaultSetMatchesPolicy() {
        assertEquals(List.of("web", "data-jpa", "devtools", "validation", "lombok"), Defaults.DEPENDENCIES);
    }

    @Test
    void resolveUsesBuiltInDefaultsWhenNothingProvided() {
        assertEquals(Defaults.DEPENDENCIES, NewCommand.resolveDependencies(List.of(), null));
        assertEquals(Defaults.DEPENDENCIES, NewCommand.resolveDependencies(null, null));
        assertEquals(Defaults.DEPENDENCIES, NewCommand.resolveDependencies(List.of(), List.of()));
    }

    @Test
    void resolveUsesConfigDefaultWhenNoExplicitDeps() {
        assertEquals(List.of("web"), NewCommand.resolveDependencies(List.of(), List.of("web")));
        assertEquals(List.of("web"), NewCommand.resolveDependencies(null, List.of("web")));
    }

    @Test
    void explicitDepsTakePrecedenceOverConfigAndDefaults() {
        List<String> explicit = List.of("web", "actuator");
        assertEquals(explicit, NewCommand.resolveDependencies(explicit, null));
        assertEquals(explicit, NewCommand.resolveDependencies(explicit, List.of("data-jpa")));
    }
}
