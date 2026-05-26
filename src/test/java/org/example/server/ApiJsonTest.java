package org.example.server;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiJsonTest {
    @Test
    void parseObjectAllowsTrailingWhitespace() {
        Map<String, Object> parsed = ApiJson.parseObject(" {\"amount\":12.5} \n");

        assertEquals(12.5, ((Number) parsed.get("amount")).doubleValue());
    }

    @Test
    void parseObjectRejectsTrailingContent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ApiJson.parseObject("{\"username\":\"bidder\"} invalid")
        );
    }
}
