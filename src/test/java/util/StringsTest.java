package util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StringsTest {

    @Test
    void firstNonBlankSkipsNullAndBlank() {
        assertEquals("b", Strings.firstNonBlank(null, "  ", "b", "c"));
        assertNull(Strings.firstNonBlank(null, ""));
    }

    @Test
    void splitCsvTrimsAndDropsEmptyTokens() {
        assertEquals(List.of("web", "data-jpa"), Strings.splitCsv(" web, ,data-jpa,"));
        assertEquals(List.of(), Strings.splitCsv(""));
    }
}
