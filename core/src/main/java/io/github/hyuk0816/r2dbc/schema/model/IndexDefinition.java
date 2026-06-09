package io.github.hyuk0816.r2dbc.schema.model;

import java.util.List;

public record IndexDefinition(
        String name,
        List<String> columns,
        boolean unique
) {

    public IndexDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }
        columns = List.copyOf(columns);
    }
}
