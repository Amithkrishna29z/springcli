package service;

import exception.NetworkException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateNotifierTest {

    private String notify(UpdateNotifier notifier) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        notifier.maybeNotify(new PrintStream(buf, true, StandardCharsets.UTF_8));
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void notifiesWhenANewerVersionIsAvailable(@TempDir Path dir) {
        UpdateService svc = mock(UpdateService.class);
        when(svc.currentVersion()).thenReturn("1.2.0");
        when(svc.latestVersion()).thenReturn("1.3.0");

        String out = notify(new UpdateNotifier(svc, dir.resolve("check.json"), Duration.ofHours(24)));
        assertTrue(out.contains("1.3.0"));
        assertTrue(out.contains("springcli update"));
    }

    @Test
    void staysSilentWhenUpToDate(@TempDir Path dir) {
        UpdateService svc = mock(UpdateService.class);
        when(svc.currentVersion()).thenReturn("1.2.0");
        when(svc.latestVersion()).thenReturn("1.2.0");

        assertTrue(notify(new UpdateNotifier(svc, dir.resolve("check.json"), Duration.ofHours(24))).isEmpty());
    }

    @Test
    void freshCacheAvoidsAnotherNetworkCall(@TempDir Path dir) throws Exception {
        Path cache = dir.resolve("check.json");
        Files.writeString(cache, "{\"lastCheck\":" + System.currentTimeMillis() + ",\"latest\":\"1.4.0\"}");

        UpdateService svc = mock(UpdateService.class);
        when(svc.currentVersion()).thenReturn("1.2.0");

        String out = notify(new UpdateNotifier(svc, cache, Duration.ofHours(24)));
        assertTrue(out.contains("1.4.0"));
        verify(svc, never()).latestVersion();
    }

    @Test
    void staleCacheTriggersRefreshAndPersists(@TempDir Path dir) throws Exception {
        Path cache = dir.resolve("check.json");
        Files.writeString(cache, "{\"lastCheck\":0,\"latest\":\"1.3.0\"}");

        UpdateService svc = mock(UpdateService.class);
        when(svc.currentVersion()).thenReturn("1.2.0");
        when(svc.latestVersion()).thenReturn("1.5.0");

        String out = notify(new UpdateNotifier(svc, cache, Duration.ofHours(24)));
        assertTrue(out.contains("1.5.0"));
        assertTrue(Files.readString(cache).contains("1.5.0"));
    }

    @Test
    void networkFailureFallsBackToStaleCache(@TempDir Path dir) throws Exception {
        Path cache = dir.resolve("check.json");
        Files.writeString(cache, "{\"lastCheck\":0,\"latest\":\"1.6.0\"}");

        UpdateService svc = mock(UpdateService.class);
        when(svc.currentVersion()).thenReturn("1.2.0");
        when(svc.latestVersion()).thenThrow(new NetworkException("offline"));

        assertTrue(notify(new UpdateNotifier(svc, cache, Duration.ofHours(24))).contains("1.6.0"));
    }

    @Test
    void noCacheAndNetworkFailureIsSilent(@TempDir Path dir) {
        UpdateService svc = mock(UpdateService.class);
        when(svc.currentVersion()).thenReturn("1.2.0");
        when(svc.latestVersion()).thenThrow(new NetworkException("offline"));

        assertFalse(notify(new UpdateNotifier(svc, dir.resolve("missing.json"), Duration.ofHours(24))).contains("available"));
    }
}
