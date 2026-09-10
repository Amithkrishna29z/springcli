package util;

import org.junit.jupiter.api.Test;

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
    void normalizeStripsVPrefixAndWhitespace() {
        assertEquals("1.2.0", Versions.normalize(" v1.2.0 "));
        assertEquals("", Versions.normalize(null));
    }
}
