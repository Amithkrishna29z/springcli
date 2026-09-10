package model;

import exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureTest {

    @Test
    void fromIdIsCaseInsensitive() {
        assertEquals(Architecture.LAYERED, Architecture.fromId("layered"));
        assertEquals(Architecture.CLEAN, Architecture.fromId("CLEAN"));
        assertEquals(Architecture.HEXAGONAL, Architecture.fromId("Hexagonal"));
    }

    @Test
    void unknownIdIsRejectedWithTheValidValues() {
        ValidationException e = assertThrows(ValidationException.class, () -> Architecture.fromId("onion"));
        assertTrue(e.getMessage().contains("none, layered, clean, hexagonal"));
    }

    @Test
    void noneScaffoldsNothingAndTheRestScaffoldSomething() {
        assertTrue(Architecture.NONE.packages().isEmpty());
        assertFalse(Architecture.LAYERED.packages().isEmpty());
        assertFalse(Architecture.CLEAN.packages().isEmpty());
        assertFalse(Architecture.HEXAGONAL.packages().isEmpty());
    }

    @Test
    void packagesAreValidJavaIdentifiersSoGeneratedFoldersAreUsable() {
        for (Architecture a : Architecture.values()) {
            for (String pkg : a.packages()) {
                for (String segment : pkg.split("/")) {
                    assertTrue(segment.matches("[a-z][a-z0-9]*"),
                            a.id() + " package segment '" + segment + "' should be a lowercase identifier");
                }
            }
        }
    }

    @Test
    void asSelectListsEveryArchitectureAndDefaultsToNone() {
        Metadata.SingleSelect select = Architecture.asSelect();
        assertEquals("none", select.defaultValue());
        assertEquals(Architecture.values().length, select.values().size());
        assertTrue(select.isValid("clean"));
    }
}
