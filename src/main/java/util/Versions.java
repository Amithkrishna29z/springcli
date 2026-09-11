package util;

import java.util.ArrayList;
import java.util.List;

/**
 * Lenient comparison of dotted version strings, as used by springcli release tags ({@code v1.2.0})
 * and Spring Boot versions ({@code 3.3.2}). Only the leading numeric components are compared; a
 * pre-release suffix such as {@code -rc1} is ignored.
 */
public final class Versions {

    private Versions() {
    }

    /** @return {@code true} if {@code candidate} is a strictly higher version than {@code current}. */
    public static boolean isNewer(String candidate, String current) {
        return compare(candidate, current) > 0;
    }

    /** Orders versions oldest first; usable as a {@link java.util.Comparator}. */
    public static int compare(String a, String b) {
        int[] x = parse(a);
        int[] y = parse(b);
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int xi = i < x.length ? x[i] : 0;
            int yi = i < y.length ? y[i] : 0;
            if (xi != yi) {
                return Integer.compare(xi, yi);
            }
        }
        return 0;
    }

    /** @return {@code v} trimmed and without a leading {@code v}; empty for {@code null}. */
    public static String normalize(String v) {
        if (v == null) {
            return "";
        }
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        return v;
    }

    /** Parses the leading numeric dotted components (e.g. "1.2.0-rc1" -> [1,2,0]). */
    private static int[] parse(String v) {
        List<Integer> nums = new ArrayList<>();
        for (String part : normalize(v).split("[.\\-+]")) {
            try {
                nums.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                break;
            }
        }
        int[] out = new int[nums.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = nums.get(i);
        }
        return out;
    }
}
