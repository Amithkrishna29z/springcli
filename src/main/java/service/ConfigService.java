package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import exception.SpringCliException;
import model.UserConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and saves the {@link UserConfig} from {@code ~/.springcli/config.json}. The file path is
 * injectable so tests can use a temporary location instead of the real home directory.
 *
 * <p>{@link #load()} is intentionally forgiving: a missing or unreadable/corrupt file yields an empty
 * config rather than an error, so a bad file never blocks project generation.</p>
 */
public class ConfigService {

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConfigService() {
        this(defaultPath());
    }

    public ConfigService(Path file) {
        this.file = file;
    }

    /** @return the default config location, {@code ~/.springcli/config.json}. */
    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".springcli", "config.json");
    }

    public Path path() {
        return file;
    }

    /** @return the persisted config, or an empty config if none exists or it cannot be parsed. */
    public UserConfig load() {
        if (!Files.exists(file)) {
            return new UserConfig();
        }
        try {
            UserConfig config = mapper.readValue(Files.readString(file), UserConfig.class);
            return config == null ? new UserConfig() : config;
        } catch (IOException e) {
            return new UserConfig();
        }
    }

    /**
     * Persists {@code config}, creating the parent directory if needed.
     *
     * @throws SpringCliException if the file cannot be written
     */
    public void save(UserConfig config) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
        } catch (IOException e) {
            throw new SpringCliException("Could not write config to " + file, e);
        }
    }
}
