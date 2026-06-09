package io.github.hyuk0816.r2dbc.schema.naming;

import java.util.Locale;
import java.util.Objects;

public final class NameCaseConverter {

    public String convert(String fieldName, NameCase nameCase) {
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        Objects.requireNonNull(nameCase, "nameCase must not be null");
        return switch (nameCase) {
            case SPRING, SNAKE_CASE -> toSnakeCase(fieldName);
            case LOWER_CAMEL, AS_IS -> fieldName;
            case UPPER_CAMEL -> capitalize(fieldName);
            case LOWER -> fieldName.toLowerCase(Locale.ROOT);
            case UPPER -> fieldName.toUpperCase(Locale.ROOT);
        };
    }

    private static String toSnakeCase(String value) {
        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isUpperCase(current) && i > 0) {
                char previous = value.charAt(i - 1);
                boolean nextIsLower = i + 1 < value.length() && Character.isLowerCase(value.charAt(i + 1));
                if (Character.isLowerCase(previous) || Character.isDigit(previous) || nextIsLower) {
                    result.append('_');
                }
            }
            result.append(Character.toLowerCase(current));
        }
        return result.toString();
    }

    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
