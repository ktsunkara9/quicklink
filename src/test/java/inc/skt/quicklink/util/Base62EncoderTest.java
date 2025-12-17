package inc.skt.quicklink.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Base62EncoderTest {

    @Test
    void should_encodeZero_when_idIsZero() {
        String result = Base62Encoder.encode(0);
        assertEquals("0000000", result);
    }

    @Test
    void should_encodeSingleDigit_when_idIsLessThan62() {
        assertEquals("0000001", Base62Encoder.encode(1));
        assertEquals("0000009", Base62Encoder.encode(9));
        assertEquals("000000a", Base62Encoder.encode(10));
        assertEquals("000000f", Base62Encoder.encode(15));
        assertEquals("000000z", Base62Encoder.encode(35));
        assertEquals("000000A", Base62Encoder.encode(36));
        assertEquals("000000Z", Base62Encoder.encode(61));
    }

    @Test
    void should_encodeTwoDigits_when_idIs62OrMore() {
        assertEquals("0000010", Base62Encoder.encode(62));
        assertEquals("0000011", Base62Encoder.encode(63));
    }

    @Test
    void should_encodeMaxValue_when_idIsMaxFor7Chars() {
        String result = Base62Encoder.encode(3521614606207L);
        assertEquals("zzzzzzz", result);
    }

    @Test
    void should_decodeToOriginalId_when_validShortCode() {
        assertEquals(0, Base62Encoder.decode("0000000"));
        assertEquals(1, Base62Encoder.decode("0000001"));
        assertEquals(15, Base62Encoder.decode("000000f"));
        assertEquals(62, Base62Encoder.decode("0000010"));
        assertEquals(3521614606207L, Base62Encoder.decode("zzzzzzz"));
    }

    @Test
    void should_beReversible_when_encodingAndDecoding() {
        long[] testIds = {0, 1, 15, 62, 1000, 1000000, 3521614606207L};
        
        for (long id : testIds) {
            String encoded = Base62Encoder.encode(id);
            long decoded = Base62Encoder.decode(encoded);
            assertEquals(id, decoded, "Failed for ID: " + id);
        }
    }
}
