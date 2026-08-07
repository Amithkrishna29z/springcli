package util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessUtilsTest {

    @Test
    void platformCommandAdaptsToCurrentOs() {
        List<String> r = ProcessUtils.platformCommand("mvn", "-v");
        if (ProcessUtils.isWindows()) {
            assertEquals(List.of("cmd.exe", "/c", "mvn", "-v"), r);
        } else {
            assertEquals(List.of("mvn", "-v"), r);
        }
    }

    @Test
    void argumentOrderIsPreserved() {
        List<String> r = ProcessUtils.platformCommand("a", "b", "c");
        assertEquals("a", r.get(r.size() - 3));
        assertEquals("b", r.get(r.size() - 2));
        assertEquals("c", r.get(r.size() - 1));
    }

    @Test
    void singleCommandIsHandled() {
        List<String> r = ProcessUtils.platformCommand("git");
        assertEquals("git", r.get(r.size() - 1));
    }
}
