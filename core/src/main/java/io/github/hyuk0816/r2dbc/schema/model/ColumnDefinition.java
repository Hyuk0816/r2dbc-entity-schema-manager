package io.github.hyuk0816.r2dbc.schema.model;

import java.util.Locale;
import java.util.Objects;

public record ColumnDefinition(
        String name,
        String type,
        boolean nullable,
        String defaultValue,
        String comment
) {

    public ColumnDefinition {
        name = requireText(name, "name");
        type = requireText(type, "type");
        defaultValue = blankToNull(defaultValue);
        comment = blankToNull(comment);
    }

    public String normalizedType() {
        return type.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public boolean hasSameType(ColumnDefinition other) {
        Objects.requireNonNull(other, "other must not be null");
        return normalizedType().equals(other.normalizedType());
    }
}
