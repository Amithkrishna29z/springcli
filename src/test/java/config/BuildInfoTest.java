package config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildInfoTest {

    @Test
    void versionIsFilledInFromThePom() {
        // "unknown" would mean springcli.properties wasn't filtered by Maven.
        assertTrue(BuildInfo.VERSION.matches("\\d+\\.\\d+\\.\\d+.*"), BuildInfo.VERSION);
    }

    @Test
    void userAgentCarriesTheVersion() {
        assertEquals("springcli/" + BuildInfo.VERSION, BuildInfo.USER_AGENT);
    }
}
