package inc.skt.quicklink.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Base62Encoder utility class.
 * Tests encoding, decoding, and edge cases.
 */
class Base62EncoderTest {
    
    @Test
    void should_encodeZero_when_idIsZero() {
        String result = Base62Encoder.encode(0);
        assertEquals("0000000", result);
    }
    
    @Test
    void should_encodeOne_when_idIsOne() {
        String result = Base62Encoder.encode(1);
        assertEquals("0000001", result);
    }
    
    @Test
    void should_encodeSingleDigit_when_idIsLessThan62() {
        assertEquals("000000f", Base62Encoder.encode(15));
        assertEquals("000000z", Base62Encoder.encode(35));
        assertEquals("000000Z", Base62Encoder.encode(61));
    }
    
    @Test
    void should_encodeTwoDigits_when_idIs62() {
        String result = Base62Encoder.encode(62);
        assertEquals("0000010", result);
    }
    
    @Test
    void should_encodeMaxValue_when_idIsMaxFor7Chars() {
        String result = Base62Encoder.encode(3521614606207L);
        assertEquals("ZZZZZZZ", result);
    }
    
    @Test
    void should_decodeToOriginalId_when_validShortCode() {
        assertEquals(0, Base62Encoder.decode("0000000"));
        assertEquals(1, Base62Encoder.decode("0000001"));
        assertEquals(15, Base62Encoder.decode("000000f"));
        assertEquals(62, Base62Encoder.decode("0000010"));
        assertEquals(3521614606207L, Base62Encoder.decode("ZZZZZZZ"));
    }
    
    @Test
    void should_encodeAndDecodeCorrectly_when_roundTrip() {
        long[] testIds = {0, 1, 62, 100, 1000, 10000, 1000000, 3521614606207L};
        
        for (long id : testIds) {
            String encoded = Base62Encoder.encode(id);
            long decoded = Base62Encoder.decode(encoded);
            assertEquals(id, decoded, "Round trip failed for ID: " + id);
        }
    }
    
    @Test
    void should_returnSevenCharacters_when_anyValidId() {
        assertEquals(7, Base62Encoder.encode(0).length());
        assertEquals(7, Base62Encoder.encode(1).length());
        assertEquals(7, Base62Encoder.encode(1000).length());
        assertEquals(7, Base62Encoder.encode(3521614606207L).length());
    }
}
