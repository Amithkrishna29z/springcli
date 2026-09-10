package service;

import exception.NetworkException;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void latestVersionParsesTagName() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{\"tag_name\":\"v1.4.2\",\"html_url\":\"x\"}");
        doReturn(resp).when(http).send(any(HttpRequest.class), any());

        UpdateService svc = new UpdateService(http, "https://api.github.com", "owner/repo", "1.1.0");
        assertEquals("1.4.2", svc.latestVersion());
    }

    @SuppressWarnings("unchecked")
    @Test
    void nonSuccessStatusRaisesNetworkException() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(403);
        doReturn(resp).when(http).send(any(HttpRequest.class), any());

        UpdateService svc = new UpdateService(http, "https://api.github.com", "owner/repo", "1.1.0");
        assertThrows(NetworkException.class, svc::latestVersion);
    }

    @SuppressWarnings("unchecked")
    @Test
    void missingTagRaisesNetworkException() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{\"message\":\"Not Found\"}");
        doReturn(resp).when(http).send(any(HttpRequest.class), any());

        UpdateService svc = new UpdateService(http, "https://api.github.com", "owner/repo", "1.1.0");
        assertThrows(NetworkException.class, svc::latestVersion);
    }

    @Test
    void currentVersionStripsVPrefix() {
        UpdateService svc = new UpdateService(mock(HttpClient.class), "https://api.github.com", "owner/repo", "v1.1.0");
        assertEquals("1.1.0", svc.currentVersion());
    }

    @Test
    void downloadUrlUsesLatestPath() {
        UpdateService svc = new UpdateService(mock(HttpClient.class), "https://api.github.com", "owner/repo", "1.1.0");
        assertEquals("https://github.com/owner/repo/releases/latest/download/springcli-setup.exe",
                svc.downloadUrl("springcli-setup.exe"));
    }
}
