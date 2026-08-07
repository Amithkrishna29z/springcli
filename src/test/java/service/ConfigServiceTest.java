package service;

import model.UserConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigServiceTest {

    @Test
    void loadReturnsEmptyWhenFileMissing(@TempDir Path dir) {
        ConfigService svc = new ConfigService(dir.resolve("config.json"));
        UserConfig cfg = svc.load();
        assertNull(cfg.getGroupId());
        assertNull(cfg.getDependencies());
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path dir) {
        ConfigService svc = new ConfigService(dir.resolve("nested/config.json"));
        UserConfig cfg = new UserConfig();
        cfg.setGroupId("com.acme");
        cfg.setJavaVersion("21");
        cfg.setDependencies(List.of("web", "data-jpa"));
        svc.save(cfg);

        UserConfig loaded = svc.load();
        assertEquals("com.acme", loaded.getGroupId());
        assertEquals("21", loaded.getJavaVersion());
        assertEquals(List.of("web", "data-jpa"), loaded.getDependencies());
    }

    @Test
    void saveCreatesParentDirectories(@TempDir Path dir) {
        Path file = dir.resolve("a/b/config.json");
        new ConfigService(file).save(new UserConfig());
        assertTrue(Files.exists(file));
    }

    @Test
    void corruptFileLoadsAsEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{ this is not json");
        assertNull(new ConfigService(file).load().getGroupId());
    }

    @Test
    void onlySetKeysArePersisted(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        UserConfig cfg = new UserConfig();
        cfg.setGroupId("com.acme");
        new ConfigService(file).save(cfg);

        String json = Files.readString(file);
        assertTrue(json.contains("groupId"));
        assertTrue(!json.contains("javaVersion"));
    }
}
