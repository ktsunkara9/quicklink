package inc.skt.quicklink.exception;

/**
 * Exception thrown when short code is not found.
 * Maps to HTTP 404 Not Found.
 */
public class UrlNotFoundException extends RuntimeException {
    
    public UrlNotFoundException(String message) {
        super(message);
    }
}
