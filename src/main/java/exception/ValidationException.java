package exception;

public class ValidationException extends SpringCliException {

    public ValidationException(String message) {
        super(message);
    }
}
