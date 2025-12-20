package inc.skt.quicklink.exception;

/**
 * Exception thrown when custom alias validation fails.
 * Maps to HTTP 400 Bad Request.
 */
public class InvalidAliasException extends RuntimeException {
    
    public InvalidAliasException(String message) {
        super(message);
    }
}
