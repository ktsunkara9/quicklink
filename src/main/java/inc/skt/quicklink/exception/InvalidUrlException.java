package inc.skt.quicklink.exception;

/**
 * Exception thrown when URL validation fails.
 * Maps to HTTP 400 Bad Request.
 */
public class InvalidUrlException extends RuntimeException {
    
    public InvalidUrlException(String message) {
        super(message);
    }
}
