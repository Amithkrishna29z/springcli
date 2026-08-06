package exception;

/**
 * Base type for all recoverable, user-facing errors raised by the CLI. Carrying a single root type
 * lets the command layer catch application errors distinctly from unexpected runtime failures and
 * print a clean message instead of a stack trace.
 */
public class SpringCliException extends RuntimeException {

    public SpringCliException(String message) {
        super(message);
    }

    public SpringCliException(String message, Throwable cause) {
        super(message, cause);
    }
}
