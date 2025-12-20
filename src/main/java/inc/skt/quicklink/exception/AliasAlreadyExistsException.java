package inc.skt.quicklink.exception;

/**
 * Exception thrown when custom alias already exists.
 * Maps to HTTP 409 Conflict.
 */
public class AliasAlreadyExistsException extends RuntimeException {
    
    public AliasAlreadyExistsException(String message) {
        super(message);
    }
}
