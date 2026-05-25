package org.example.util;

import java.util.Locale;

public final class CurrencyInputParser {
    private CurrencyInputParser() {
    }

    public static double parseRequiredAmount(String text) {
        String normalized = normalizeAmountText(text);
        if (normalized.isBlank()) {
            throw new NumberFormatException("Amount is blank.");
        }

        double amount = Double.parseDouble(normalized);
        if (!Double.isFinite(amount)) {
            throw new NumberFormatException("Amount must be finite.");
        }
        return amount;
    }

    public static double parseOptionalAmount(String text) {
        return normalizeAmountText(text).isBlank() ? 0.0 : parseRequiredAmount(text);
    }

    public static String formatAmountInput(double amount) {
        return String.format(Locale.US, "%.2f", MoneyUtils.roundCurrency(amount));
    }

    private static String normalizeAmountText(String text) {
        return text == null
                ? ""
                : text.trim()
                        .replace("$", "")
                        .replace(",", "")
                        .replace("_", "");
    }
}
