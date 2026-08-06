package exception;

/** Raised when communication with the Spring Initializr service fails (I/O, timeout, non-2xx). */
public class NetworkException extends SpringCliException {

    public NetworkException(String message) {
        super(message);
    }

    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
