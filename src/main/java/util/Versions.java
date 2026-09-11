package util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Lenient comparison of dotted version strings, as used by springcli release tags ({@code v1.2.0}),
 * Spring Boot versions ({@code 3.3.2}) and Maven artifact versions ({@code 33.0.0-jre}). Only the
 * leading numeric components are compared; a pre-release suffix such as {@code -rc1} is ignored.
 */
public final class Versions {

    /** Snapshots, alphas, betas, milestones (-M1), release candidates (-RC1, .CR1) and early-access builds. */
    private static final Pattern PRE_RELEASE =
            Pattern.compile("(?i)snapshot|alpha|beta|preview|milestone|[.-](rc|cr|m|ea)[.-]?\\d*\\b");

    /** The leading numeric components, e.g. {@code 33.0.0} in {@code 33.0.0-jre}. */
    private static final Pattern NUMERIC_PREFIX = Pattern.compile("^\\d+(\\.\\d+)*");

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

    /** @return whether {@code v} is a pre-release rather than a stable release */
    public static boolean isPreRelease(String v) {
        return PRE_RELEASE.matcher(v).find();
    }

    /**
     * The newest stable release in {@code candidates} that is newer than {@code current} and carries the
     * same qualifier, so {@code 32.1.3-jre} only moves to another {@code -jre} release and
     * {@code 4.1.100.Final} to another {@code .Final} one.
     *
     * @return that release, or empty if there is none
     */
    public static Optional<String> latestRelease(Collection<String> candidates, String current) {
        String qualifier = qualifier(current);
        return candidates.stream()
                .filter(v -> !isPreRelease(v) && qualifier(v).equals(qualifier) && isNewer(v, current))
                .max(Versions::compare);
    }

    /**
     * @return whether going from {@code from} to {@code to} changes the major version, or for a 0.x
     * version the minor one (where 0.x libraries make their breaking changes)
     */
    public static boolean isMajorUpgrade(String from, String to) {
        int[] a = parse(from);
        int[] b = parse(to);
        int significant = component(a, 0) == 0 ? 2 : 1;
        for (int i = 0; i < significant; i++) {
            if (component(a, i) != component(b, i)) {
                return true;
            }
        }
        return false;
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

    /** What follows the leading numeric components, lower-cased (e.g. "-jre", ".final", or ""). */
    private static String qualifier(String v) {
        return NUMERIC_PREFIX.matcher(normalize(v)).replaceFirst("").toLowerCase(Locale.ROOT);
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

    private static int component(int[] v, int i) {
        return i < v.length ? v[i] : 0;
    }
}
