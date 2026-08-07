package service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import util.Ansi;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Best-effort "an update is available" notice shown at startup. Designed to never slow down or break
 * a command: the latest version is cached to disk and only refreshed at most once per {@code ttl};
 * the network refresh runs on a daemon thread with a hard timeout, and every failure is swallowed
 * (falling back to the last known value).
 */
public class UpdateNotifier {

    private static final Duration FETCH_TIMEOUT = Duration.ofMillis(2500);

    private final UpdateService updateService;
    private final Path cacheFile;
    private final Duration ttl;
    private final ObjectMapper mapper = new ObjectMapper();

    public UpdateNotifier(UpdateService updateService, Path cacheFile, Duration ttl) {
        this.updateService = updateService;
        this.cacheFile = cacheFile;
        this.ttl = ttl;
    }

    /** Prints a one-line notice to {@code out} if a newer version is known to be available. */
    public void maybeNotify(PrintStream out) {
        try {
            String latest = knownLatest();
            if (latest != null && UpdateService.isNewer(latest, updateService.currentVersion())) {
                out.println(Ansi.yellow("⬆ springcli " + latest + " is available "
                        + "(you have " + updateService.currentVersion() + ").")
                        + "  Run " + Ansi.cyan("springcli update") + " to upgrade.");
            }
        } catch (Exception ignored) {
            // A notice is never worth failing a command over.
        }
    }

    /** @return the latest version from a fresh (throttled) check or the cache, or null if unknown. */
    String knownLatest() {
        Cached cached = readCache();
        long now = System.currentTimeMillis();
        if (cached != null && cached.latest != null && (now - cached.lastCheck) < ttl.toMillis()) {
            return cached.latest;
        }
        String fetched = fetchWithTimeout();
        if (fetched != null) {
            writeCache(new Cached(now, fetched));
            return fetched;
        }
        // Network failed or timed out: fall back to whatever we last knew.
        return cached != null ? cached.latest : null;
    }

    private String fetchWithTimeout() {
        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "springcli-update-check");
            t.setDaemon(true);
            return t;
        });
        try {
            return exec.submit(updateService::latestVersion).get(FETCH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return null;
        } finally {
            exec.shutdownNow();
        }
    }

    private Cached readCache() {
        try {
            if (!Files.exists(cacheFile)) {
                return null;
            }
            return mapper.readValue(Files.readString(cacheFile), Cached.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeCache(Cached cached) {
        try {
            Files.createDirectories(cacheFile.getParent());
            Files.writeString(cacheFile, mapper.writeValueAsString(cached));
        } catch (Exception ignored) {
            // A stale/missing cache just means we re-check next time.
        }
    }

    /** Persisted check result. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Cached {
        public long lastCheck;
        public String latest;

        Cached() {
        }

        Cached(long lastCheck, String latest) {
            this.lastCheck = lastCheck;
            this.latest = latest;
        }
    }
}
