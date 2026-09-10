package exception;

/**
 * The command can't run in the current context — e.g. it needs a {@code pom.xml} and there isn't
 * one. Maps to exit code 2, picocli's code for usage errors.
 */
public class UsageException extends SpringCliException {

    public UsageException(String message) {
        super(message);
    }

    @Override
    public int exitCode() {
        return 2;
    }
}
