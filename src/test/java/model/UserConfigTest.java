package model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserConfigTest {

    @Test
    void setAndGetSimpleKeys() {
        UserConfig c = new UserConfig();
        c.set("groupId", "com.acme");
        c.set("javaVersion", "21");
        assertEquals("com.acme", c.get("groupId"));
        assertEquals("21", c.get("javaVersion"));
    }

    @Test
    void dependenciesRoundTripAsCsv() {
        UserConfig c = new UserConfig();
        c.set("dependencies", "web, data-jpa ,lombok");
        assertEquals(List.of("web", "data-jpa", "lombok"), c.getDependencies());
        assertEquals("web,data-jpa,lombok", c.get("dependencies"));
    }

    @Test
    void unsetClearsValue() {
        UserConfig c = new UserConfig();
        c.set("groupId", "com.acme");
        c.unset("groupId");
        assertNull(c.get("groupId"));
    }

    @Test
    void unknownKeyThrows() {
        UserConfig c = new UserConfig();
        assertThrows(IllegalArgumentException.class, () -> c.set("bogus", "x"));
        assertThrows(IllegalArgumentException.class, () -> c.get("bogus"));
    }

    @Test
    void keyMembership() {
        assertTrue(UserConfig.isKey("groupId"));
        assertFalse(UserConfig.isKey("bogus"));
    }
}
