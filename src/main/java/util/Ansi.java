package util;

/**
 * Minimal ANSI colour helper for console messaging. Colours are disabled automatically when output
 * is not a terminal (piped/redirected) or when the {@code NO_COLOR} convention is set, so the tool
 * degrades gracefully in CI logs and pipes.
 */
public final class Ansi {

    private static final boolean ENABLED = colorSupported();

    private static final String RESET = "[0m";
    private static final String RED = "[31m";
    private static final String GREEN = "[32m";
    private static final String YELLOW = "[33m";
    private static final String CYAN = "[36m";
    private static final String BOLD = "[1m";

    private Ansi() {
    }

    private static boolean colorSupported() {
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        // System.console() is null when stdout is redirected or piped.
        return System.console() != null;
    }

    private static String wrap(String code, String text) {
        return ENABLED ? code + text + RESET : text;
    }

    public static String green(String text) { return wrap(GREEN, text); }
    public static String red(String text) { return wrap(RED, text); }
    public static String yellow(String text) { return wrap(YELLOW, text); }
    public static String cyan(String text) { return wrap(CYAN, text); }
    public static String bold(String text) { return wrap(BOLD, text); }

    /** Prints a green success line prefixed with a check mark. */
    public static void success(String text) {
        System.out.println(green("✔ " + text));
    }

    /** Prints a yellow warning line. */
    public static void warn(String text) {
        System.out.println(yellow("! " + text));
    }

    /** Prints a red error line to stderr. */
    public static void error(String text) {
        System.err.println(red("✗ " + text));
    }

    /** Prints a cyan informational/progress line. */
    public static void info(String text) {
        System.out.println(cyan(text));
    }
}
