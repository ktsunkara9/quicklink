package inc.skt.quicklink.exception;

/**
 * Exception thrown when URL has expired.
 * Maps to HTTP 410 Gone.
 */
public class UrlExpiredException extends RuntimeException {
    
    public UrlExpiredException(String message) {
        super(message);
    }
}
