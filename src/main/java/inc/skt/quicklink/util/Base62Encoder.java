package inc.skt.quicklink.util;

/**
 * Base62 encoder/decoder for converting numeric IDs to URL-safe short codes.
 * Uses 62 characters: 0-9, a-z, A-Z (no special characters).
 * 
 * Examples:
 * - ID 1 → "0000001"
 * - ID 15 → "000000f"
 * - ID 62 → "0000010"
 * - ID 3521614606207 → "zzzzzzz" (max 7-char value)
 */
public class Base62Encoder {
    
    // Base62 alphabet: 0-9 (indices 0-9), a-z (indices 10-35), A-Z (indices 36-61)
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;
    private static final int SHORT_CODE_LENGTH = 7;
    
    /**
     * Converts a numeric ID to a 7-character base62 short code.
     * 
     * Algorithm:
     * 1. Repeatedly divide ID by 62
     * 2. Take remainder as index into BASE62 alphabet
     * 3. Reverse the result (division gives digits in reverse order)
     * 4. Pad with leading zeros to 7 characters
     * 
     * @param id Numeric identifier (0 to 3,521,614,606,207)
     * @return 7-character short code
     */
    public static String encode(long id) {
        // Special case: ID 0 → "0000000"
        if (id == 0) {
            return "0".repeat(SHORT_CODE_LENGTH);
        }
        
        StringBuilder sb = new StringBuilder();
        
        // Convert to base62 (similar to converting decimal to binary)
        // Example: ID 15 → 15 % 62 = 15 → BASE62[15] = 'f'
        while (id > 0) {
            sb.append(BASE62.charAt((int)(id % BASE)));
            id /= BASE;
        }
        
        // Reverse because division produces digits in reverse order
        String encoded = sb.reverse().toString();
        
        // Pad to 7 characters: "f" → "000000f"
        return padLeft(encoded, SHORT_CODE_LENGTH);
    }
    
    /**
     * Converts a base62 short code back to the original numeric ID.
     * 
     * Algorithm:
     * 1. For each character, find its index in BASE62 alphabet
     * 2. Multiply running total by 62 and add the index
     * 3. Similar to converting binary to decimal
     * 
     * @param shortCode 7-character base62 code
     * @return Original numeric ID
     */
    public static long decode(String shortCode) {
        long id = 0;
        
        // Convert from base62 to decimal
        // Example: "000000f" → 0*62^6 + 0*62^5 + ... + 15*62^0 = 15
        for (char c : shortCode.toCharArray()) {
            id = id * BASE + BASE62.indexOf(c);
        }
        return id;
    }
    
    /**
     * Pads a string with leading zeros to reach the specified length.
     * 
     * @param str String to pad
     * @param length Target length
     * @return Padded string
     */
    private static String padLeft(String str, int length) {
        return "0".repeat(Math.max(0, length - str.length())) + str;
    }
}
