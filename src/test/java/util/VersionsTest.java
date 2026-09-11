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
    void latestReleaseSkipsPreReleasesAndOlderVersions() {
        List<String> lombok = List.of("1.18.28", "1.18.30", "1.18.38", "2.0.0-beta1", "2.0.0-RC1", "2.0.0-M1");
        assertEquals("1.18.38", Versions.latestRelease(lombok, "1.18.30").orElse(null));
        assertTrue(Versions.latestRelease(lombok, "1.18.38").isEmpty());
    }

    @Test
    void latestReleaseKeepsTheQualifier() {
        List<String> guava = List.of("32.1.3-jre", "33.4.8-android", "33.4.8-jre", "33.5.0-android");
        assertEquals("33.4.8-jre", Versions.latestRelease(guava, "32.1.3-jre").orElse(null));
        List<String> netty = List.of("4.1.100.Final", "4.1.111.Final", "5.0.0.Alpha2");
        assertEquals("4.1.111.Final", Versions.latestRelease(netty, "4.1.100.Final").orElse(null));
    }

    @Test
    void recognisesPreReleases() {
        for (String v : List.of("1.0.0-SNAPSHOT", "3.0.0-M1", "1.0.0-RC2", "3.0.0-rc.1", "6.0.0.CR1",
                "2.0.0-beta.1", "5.0.0.Alpha2", "21-ea+3")) {
            assertTrue(Versions.isPreRelease(v), v);
        }
        for (String v : List.of("1.18.38", "33.4.8-jre", "33.4.8-android", "4.1.111.Final", "2.0.0.RELEASE",
                "8.0.0-mariadb")) {
            assertFalse(Versions.isPreRelease(v), v);
        }
    }

    @Test
    void majorUpgradeMeansANewMajorOrA0xMinor() {
        assertFalse(Versions.isMajorUpgrade("1.18.30", "1.18.38"));
        assertFalse(Versions.isMajorUpgrade("3.2.8", "3.3.2"));
        assertTrue(Versions.isMajorUpgrade("32.1.3-jre", "33.4.8-jre"));
        assertTrue(Versions.isMajorUpgrade("0.11.5", "0.12.6"));
        assertFalse(Versions.isMajorUpgrade("0.12.5", "0.12.6"));
    }

    @Test
    void normalizeStripsVPrefixAndWhitespace() {
        assertEquals("1.2.0", Versions.normalize(" v1.2.0 "));
        assertEquals("", Versions.normalize(null));
    }
}
