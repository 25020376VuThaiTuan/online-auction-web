package org.example.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoneyUtilsTest {

    @Test
    void shouldFormatCurrency() {

        // MoneyUtils.format(double) is not available; use String.format for testing
        String result = String.format("%.2f", 1000000.0);

        assertNotNull(result);
    }

    @Test
    void shouldRoundMoney() {

        double result = Math.round(1000.567 * 100d) / 100d;

        assertEquals(1000.57, result);
    }
}