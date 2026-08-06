package exception;

/** Raised when user-supplied input is invalid (bad boot/java version, existing target folder, ...). */
public class ValidationException extends SpringCliException {

    public ValidationException(String message) {
        super(message);
    }
}
