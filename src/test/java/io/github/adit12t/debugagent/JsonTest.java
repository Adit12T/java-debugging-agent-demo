package io.github.adit12t.debugagent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTest {

    @Test
    void parsesNestedValuesAndEscapes() {
        Map<String, Object> doc = Json.parseObject(
                "{\"a\":\"line1\\n\\tat \\\"x\\\" \\u0041\",\"n\":1.5,\"ok\":true,\"none\":null,\"list\":[1,{\"k\":\"v\"}]}");

        assertEquals("line1\n\tat \"x\" A", doc.get("a"));
        assertEquals(1.5, doc.get("n"));
        assertEquals(true, doc.get("ok"));
        assertEquals(null, doc.get("none"));
        assertEquals(List.of(1.0, Map.of("k", "v")), doc.get("list"));
    }

    @Test
    void quoteRoundTripsThroughParse() {
        String original = "He said \"hi\"\n\\path\ttab";
        assertEquals(original, Json.parse(Json.quote(original)));
    }

    @Test
    void rejectsBrokenInput() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{} extra"));
    }
}
