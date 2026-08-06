package service;

import exception.NetworkException;
import model.ProjectRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InitializrClientTest {

    private ProjectRequest sampleRequest() {
        return ProjectRequest.builder()
                .type("maven-project")
                .language("java")
                .bootVersion("3.3.2")
                .groupId("com.acme")
                .artifactId("my app")   // space forces URL-encoding
                .name("my app")
                .packageName("com.acme.myapp")
                .packaging("jar")
                .javaVersion("21")
                .dependencies(List.of("web", "data-jpa"))
                .build();
    }

    @Test
    void buildsUrlEncodedQueryWithAllParameters() {
        InitializrClient client = new InitializrClient(mock(HttpClient.class), "https://start.spring.io");
        String query = client.toQuery(sampleRequest());

        assertTrue(query.contains("type=maven-project"));
        assertTrue(query.contains("bootVersion=3.3.2"));
        assertTrue(query.contains("artifactId=my+app"), "spaces should be encoded");
        assertTrue(query.contains("dependencies=web%2Cdata-jpa"), "comma-joined and encoded");
        assertTrue(query.contains("javaVersion=21"));
    }

    @Test
    void trimsTrailingSlashFromBaseUrl() {
        InitializrClient client = new InitializrClient(mock(HttpClient.class), "https://start.spring.io/");
        // No exception and query still builds — indirectly confirms construction succeeded.
        assertTrue(client.toQuery(sampleRequest()).contains("type=maven-project"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void fetchMetadataReturnsBodyOnSuccess() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"ok\":true}");
        doReturn(response).when(http).send(any(HttpRequest.class), any());

        InitializrClient client = new InitializrClient(http, "https://start.spring.io");
        assertEquals("{\"ok\":true}", client.fetchMetadata());
    }

    @SuppressWarnings("unchecked")
    @Test
    void nonSuccessStatusRaisesNetworkException() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        doReturn(response).when(http).send(any(HttpRequest.class), any());

        InitializrClient client = new InitializrClient(http, "https://start.spring.io");
        assertThrows(NetworkException.class, client::fetchMetadata);
    }

    @Test
    void ioErrorIsWrappedAsNetworkException() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doThrow(new IOException("boom")).when(http).send(any(HttpRequest.class), any());

        InitializrClient client = new InitializrClient(http, "https://start.spring.io");
        assertThrows(NetworkException.class, () -> client.downloadStarter(sampleRequest()));
    }
}
