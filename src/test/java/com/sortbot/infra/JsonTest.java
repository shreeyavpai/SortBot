package com.sortbot.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {
    @Test
    void parsesNestedObjectsArraysAndScalars() {
        Object v = Json.parse("{\"a\":[1,2.5,{\"b\":\"x\"}],\"t\":true,\"n\":null,\"s\":\"q\\\"uote\\n\\u0041\"}");
        assertEquals(1L, Json.path(v, "a", 0));
        assertEquals(2.5, Json.path(v, "a", 1));
        assertEquals("x", Json.path(v, "a", 2, "b"));
        assertEquals(Boolean.TRUE, Json.path(v, "t"));
        assertNull(Json.path(v, "n"));
        assertEquals("q\"uote\nA", Json.path(v, "s"));
    }

    @Test
    void writeThenParseRoundTrips() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("path", "C:\\Users\\me\\a \"b\".txt");
        m.put("list", List.of("x", 3, true));
        m.put("empty", Map.of());
        assertEquals(m.get("path"), Json.path(Json.parse(Json.write(m)), "path"));
        assertEquals("x", Json.path(Json.parse(Json.write(m)), "list", 0));
    }

    @Test
    void pathReturnsNullForMissingKeysAndBadIndexes() {
        Object v = Json.parse("{\"a\":[1]}");
        assertNull(Json.path(v, "b"));
        assertNull(Json.path(v, "a", 5));
        assertNull(Json.path(v, "a", "x"));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("not json"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} trailing"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse(null));
    }
}
