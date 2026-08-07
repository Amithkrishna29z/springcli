package service;

import exception.NetworkException;
import model.ProjectRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitializrClientEdgeTest {

    private ProjectRequest request(List<String> deps) {
        return ProjectRequest.builder()
                .type("maven-project").language("java").bootVersion("3.3.2")
                .groupId("com.example").artifactId("demo").name("demo").packageName("com.example.demo")
                .packaging("jar").javaVersion("21").dependencies(deps).build();
    }

    @Test
    void queryOmitsDependenciesWhenEmpty() {
        InitializrClient c = new InitializrClient(mock(HttpClient.class), "https://start.spring.io");
        assertFalse(c.toQuery(request(List.of())).contains("dependencies="));
    }

    @Test
    void queryEncodesSpecialCharactersInGroupId() {
        InitializrClient c = new InitializrClient(mock(HttpClient.class), "https://start.spring.io");
        ProjectRequest r = ProjectRequest.builder().bootVersion("3.3.2").groupId("com.a b").artifactId("demo").build();
        assertTrue(c.toQuery(r).contains("groupId=com.a+b"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void fetchMetadataTargetsMetadataEndpointWithAcceptHeader() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{}");
        doReturn(resp).when(http).send(any(HttpRequest.class), any());

        new InitializrClient(http, "https://start.spring.io").fetchMetadata();

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any());
        HttpRequest sent = captor.getValue();
        assertTrue(sent.uri().toString().endsWith("/metadata/client"));
        assertTrue(sent.headers().firstValue("Accept").isPresent());
    }

    @SuppressWarnings("unchecked")
    @Test
    void downloadStarterTargetsStarterZipEndpoint() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<byte[]> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn(new byte[]{1, 2, 3});
        doReturn(resp).when(http).send(any(HttpRequest.class), any());

        byte[] out = new InitializrClient(http, "https://start.spring.io").downloadStarter(request(List.of("web")));
        assertArrayEquals(new byte[]{1, 2, 3}, out);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any());
        String uri = captor.getValue().uri().toString();
        assertTrue(uri.contains("/starter.zip?"));
        assertTrue(uri.contains("dependencies=web"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void downloadStarterNonSuccessThrows() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<byte[]> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(404);
        doReturn(resp).when(http).send(any(HttpRequest.class), any());

        InitializrClient c = new InitializrClient(http, "https://start.spring.io");
        assertThrows(NetworkException.class, () -> c.downloadStarter(request(List.of())));
    }
}
