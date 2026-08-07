package exception;

public class SpringCliException extends RuntimeException {

    public SpringCliException(String message) {
        super(message);
    }

    public SpringCliException(String message, Throwable cause) {
        super(message, cause);
    }
}
