package commands;

import org.junit.jupiter.api.Test;
import service.UpdateService;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModifyCommandTest {

    @SuppressWarnings("unchecked")
    private UpdateService serviceReturningTag(String tag, String current) throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{\"tag_name\":\"" + tag + "\"}");
        doReturn(resp).when(http).send(any(HttpRequest.class), any());
        return new UpdateService(http, "https://api.github.com", "owner/repo", current);
    }

    private Object[] run(UpdateService svc) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        int code;
        try {
            code = new ModifyCommand(svc).call();
        } finally {
            System.setOut(original);
        }
        return new Object[]{code, buf.toString(StandardCharsets.UTF_8)};
    }

    @Test
    void showsInstallInfoAndReportsUpToDate() throws Exception {
        Object[] r = run(serviceReturningTag("v1.2.0", "1.2.0"));
        assertEquals(0, r[0]);
        String out = (String) r[1];
        assertTrue(out.contains("installation"));
        assertTrue(out.contains("1.2.0"));
        assertTrue(out.toLowerCase().contains("latest version"));
    }

    @Test
    void showsInstallInfoAndAnnouncesNewerVersion() throws Exception {
        Object[] r = run(serviceReturningTag("v1.9.0", "1.2.0"));
        assertEquals(0, r[0]);
        String out = (String) r[1];
        assertTrue(out.contains("installation"));
        assertTrue(out.contains("1.9.0"));
        assertTrue(out.toLowerCase().contains("new version"));
    }
}
