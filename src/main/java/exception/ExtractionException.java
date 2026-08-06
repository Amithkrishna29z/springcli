package exception;

/** Raised when the downloaded starter archive cannot be extracted to disk. */
public class ExtractionException extends SpringCliException {

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
