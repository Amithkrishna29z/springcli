package util;

import java.util.ArrayList;
import java.util.List;

public final class Strings {

    private Strings() {
    }

    /** @return the first argument that is non-null and non-blank, or {@code null} if there is none. */
    public static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /** @return the trimmed, non-empty comma-separated tokens of {@code value}, in order. */
    public static List<String> splitCsv(String value) {
        List<String> out = new ArrayList<>();
        for (String token : value.split(",")) {
            String s = token.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }
}
