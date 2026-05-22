package org.example.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtils {
    public static final double EPSILON = 0.000001;

    private MoneyUtils() {
    }

    public static double roundCurrency(double amount) {
        if (!Double.isFinite(amount)) {
            return 0.0;
        }
        return Math.round(Math.max(0.0, amount) * 100.0) / 100.0;
    }

    public static boolean canCover(double available, double amount) {
        return roundCurrency(available) + EPSILON >= roundCurrency(amount);
    }

    public static BigDecimal toDatabaseAmount(double amount) {
        return BigDecimal.valueOf(roundCurrency(amount)).setScale(2, RoundingMode.HALF_UP);
    }

    public static double fromDatabaseAmount(BigDecimal amount) {
        return amount == null ? 0.0 : roundCurrency(amount.doubleValue());
    }
}
