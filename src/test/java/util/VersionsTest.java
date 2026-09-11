package util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionsTest {

    @Test
    void isNewerComparesSemanticVersions() {
        assertTrue(Versions.isNewer("1.2.0", "1.1.0"));
        assertTrue(Versions.isNewer("1.1.1", "1.1.0"));
        assertTrue(Versions.isNewer("2.0.0", "1.9.9"));
        assertFalse(Versions.isNewer("1.1.0", "1.1.0"));
        assertFalse(Versions.isNewer("1.0.0", "1.1.0"));
    }

    @Test
    void isNewerHandlesVPrefixAndUnevenLengths() {
        assertTrue(Versions.isNewer("v1.2", "1.1.9"));
        assertFalse(Versions.isNewer("1.2", "1.2.0"));
        assertTrue(Versions.isNewer("1.2.1", "v1.2"));
    }

    @Test
    void isNewerIgnoresPreReleaseSuffix() {
        assertTrue(Versions.isNewer("1.3.0-rc1", "1.2.0"));
        assertFalse(Versions.isNewer("1.2.0-rc1", "1.2.0"));
    }

    @Test
    void compareSortsOldestFirst() {
        List<String> versions = new ArrayList<>(List.of("3.4.1", "3.3.10", "3.3.2"));
        versions.sort(Versions::compare);
        assertEquals(List.of("3.3.2", "3.3.10", "3.4.1"), versions);
    }

    @Test
    void normalizeStripsVPrefixAndWhitespace() {
        assertEquals("1.2.0", Versions.normalize(" v1.2.0 "));
        assertEquals("", Versions.normalize(null));
    }
}
