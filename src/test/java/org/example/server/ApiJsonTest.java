package org.example.server;

import org.example.auction.AuctionStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiJsonTest {
    @Test
    void parseObjectTreatsNullAndBlankInputAsEmptyObject() {
        assertTrue(ApiJson.parseObject(null).isEmpty());
        assertTrue(ApiJson.parseObject(" \n\t ").isEmpty());
    }

    @Test
    void parseObjectAllowsTrailingWhitespace() {
        Map<String, Object> parsed = ApiJson.parseObject(" {\"amount\":12.5} \n");

        assertEquals(12.5, ((Number) parsed.get("amount")).doubleValue());
    }

    @Test
    void parseObjectHandlesNestedArraysLiteralsNumbersAndEscapes() {
        Map<String, Object> parsed = ApiJson.parseObject("""
                {
                  "text": "quote: \\" slash: \\/ backslash: \\\\ bell: \\b form: \\f line: \\n return: \\r tab: \\t unicode: \\u0041",
                  "truthy": true,
                  "falsey": false,
                  "missing": null,
                  "longNumber": 42,
                  "decimalNumber": -12.5,
                  "exponentNumber": 3e2,
                  "items": [1, "two", {"nested": "yes"}]
                }
                """);

        assertEquals("quote: \" slash: / backslash: \\ bell: \b form: \f line: \n return: \r tab: \t unicode: A",
                parsed.get("text"));
        assertEquals(true, parsed.get("truthy"));
        assertEquals(false, parsed.get("falsey"));
        assertEquals(null, parsed.get("missing"));
        assertEquals(42.0, ((Number) parsed.get("longNumber")).doubleValue());
        assertEquals(-12.5, ((Number) parsed.get("decimalNumber")).doubleValue());
        assertEquals(300.0, ((Number) parsed.get("exponentNumber")).doubleValue());

        List<?> items = (List<?>) parsed.get("items");
        assertEquals(1.0, ((Number) items.get(0)).doubleValue());
        assertEquals("two", items.get(1));
        assertEquals(Map.of("nested", "yes"), items.get(2));
    }

    @Test
    void parseObjectRejectsTrailingContent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ApiJson.parseObject("{\"username\":\"bidder\"} invalid")
        );
    }

    @Test
    void parseObjectRejectsMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> ApiJson.parseObject("[1, 2]"));
        assertThrows(IllegalArgumentException.class, () -> ApiJson.parseObject("{\"flag\":tru}"));
        assertThrows(IllegalArgumentException.class, () -> ApiJson.parseObject("{\"bad\":\"\\q\"}"));
        assertThrows(IllegalArgumentException.class, () -> ApiJson.parseObject("{\"bad\":\"\\u12\"}"));
        assertThrows(IllegalArgumentException.class, () -> ApiJson.parseObject("{\"bad\":\"unterminated}"));
        assertThrows(IllegalArgumentException.class, () -> ApiJson.parseObject("{\"a\" 1}"));
        assertThrows(NumberFormatException.class, () -> ApiJson.parseObject("{\"amount\":-}"));
    }

    @Test
    void stringifyHandlesJsonTypesEnumsCollectionsArraysAndFallbackObjects() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("escaped", "line\nquote\"tab\tcontrol" + (char) 1);
        value.put("status", AuctionStatus.RUNNING);
        value.put("items", List.of("a", 2, true));
        value.put("array", new int[]{3, 4});
        value.put("fallback", new CustomJsonValue());
        value.put("nullValue", null);

        assertEquals(
                "{\"escaped\":\"line\\nquote\\\"tab\\tcontrol\\u0001\",\"status\":\"RUNNING\",\"items\":[\"a\",2,true],\"array\":[3,4],\"fallback\":\"custom-value\",\"nullValue\":null}",
                ApiJson.stringify(value)
        );
    }

    @Test
    void requiredFieldHelpersAcceptValidValuesAndRejectMissingValues() {
        Map<String, Object> values = Map.of(
                "name", " bidder ",
                "numericAmount", 12.5,
                "textAmount", " 99.75 "
        );

        assertEquals("bidder", ApiJson.requireString(values, "name"));
        assertEquals(12.5, ApiJson.requireDouble(values, "numericAmount"));
        assertEquals(99.75, ApiJson.requireDouble(values, "textAmount"));

        assertThrows(IllegalArgumentException.class, () -> ApiJson.requireString(values, "missing"));
        assertThrows(IllegalArgumentException.class, () -> ApiJson.requireString(Map.of("name", " "), "name"));
        assertThrows(IllegalArgumentException.class, () -> ApiJson.requireDouble(values, "missing"));
        assertThrows(NumberFormatException.class, () -> ApiJson.requireDouble(Map.of("amount", "abc"), "amount"));
    }

    private static final class CustomJsonValue {
        @Override
        public String toString() {
            return "custom-value";
        }
    }
}
