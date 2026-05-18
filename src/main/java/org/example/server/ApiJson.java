package org.example.server;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ApiJson {
    private ApiJson() {
    }

    public static Map<String, Object> parseObject(String rawJson) {
        String safeJson = rawJson == null ? "" : rawJson.trim();
        if (safeJson.isEmpty()) {
            return Map.of();
        }

        Object parsed = new Parser(safeJson).parse();
        if (!(parsed instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("Expected a JSON object.");
        }

        Map<String, Object> object = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            object.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return object;
    }

    public static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return "\"" + escape(text) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Enum<?> enumValue) {
            return stringify(enumValue.name());
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(stringify(String.valueOf(entry.getKey())));
                builder.append(':');
                builder.append(stringify(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(stringify(item));
            }
            return builder.append(']').toString();
        }
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                values.add(java.lang.reflect.Array.get(value, index));
            }
            return stringify(values);
        }
        return stringify(String.valueOf(value));
    }

    public static String requireString(Map<String, Object> source, String fieldName) {
        Object value = source.get(fieldName);
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        throw new IllegalArgumentException("Field '" + fieldName + "' is required.");
    }

    public static double requireDouble(Map<String, Object> source, String fieldName) {
        Object value = source.get(fieldName);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text.trim());
        }
        throw new IllegalArgumentException("Field '" + fieldName + "' must be numeric.");
    }

    private static String escape(String text) {
        StringBuilder builder = new StringBuilder(text.length() + 16);
        for (char current : text.toCharArray()) {
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (current < 0x20) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private Object parse() {
            Object value = parseValue();
            skipWhitespace();
            if (index != text.length()) {
                throw new IllegalArgumentException("Unexpected trailing JSON content near index " + index + ".");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON input.");
            }

            char current = text.charAt(index);
            return switch (current) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                expect('}');
                return result;
            }

            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                if (peek('}')) {
                    expect('}');
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> values = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                expect(']');
                return values;
            }

            while (true) {
                values.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    expect(']');
                    return values;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == '"') {
                    return builder.toString();
                }
                if (current != '\\') {
                    builder.append(current);
                    continue;
                }

                if (index >= text.length()) {
                    throw new IllegalArgumentException("Invalid JSON string escape.");
                }

                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> builder.append(escaped);
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> builder.append(parseUnicode());
                    default -> throw new IllegalArgumentException("Unsupported JSON escape: \\" + escaped);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string.");
        }

        private char parseUnicode() {
            if (index + 4 > text.length()) {
                throw new IllegalArgumentException("Invalid unicode escape.");
            }
            String hex = text.substring(index, index + 4);
            index += 4;
            return (char) Integer.parseInt(hex, 16);
        }

        private Object parseLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) {
                throw new IllegalArgumentException("Invalid JSON literal near index " + index + ".");
            }
            index += literal.length();
            return value;
        }

        private Number parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (peek('.')) {
                index++;
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (peek('e') || peek('E')) {
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            String numberText = text.substring(start, index);
            BigDecimal decimal = new BigDecimal(numberText);
            return decimal.scale() <= 0 ? decimal.longValueExact() : decimal.doubleValue();
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at index " + index + ".");
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }
    }
}
