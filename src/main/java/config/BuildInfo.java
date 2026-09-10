package config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Build-time facts about springcli. The version is read from {@code springcli.properties}, which
 * Maven fills in from the pom, so {@code pom.xml} is the only place it is maintained.
 */
public final class BuildInfo {

    public static final String VERSION = load("version");

    public static final String USER_AGENT = "springcli/" + VERSION;

    private BuildInfo() {
    }

    private static String load(String key) {
        Properties props = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/springcli.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // fall through to "unknown"
        }
        String value = props.getProperty(key, "");
        // Unfiltered placeholder: running from classes Maven didn't process (e.g. some IDE setups).
        return value.isBlank() || value.startsWith("${") ? "unknown" : value;
    }
}
