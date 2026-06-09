package io.github.hyuk0816.r2dbc.schema.model;

import java.util.List;

public record ForeignKeyDefinition(
        String name,
        List<String> columns,
        String referencedTable,
        List<String> referencedColumns
) {

    public ForeignKeyDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("columns must not be empty");
        }
        if (referencedTable == null || referencedTable.isBlank()) {
            throw new IllegalArgumentException("referencedTable must not be blank");
        }
        if (referencedColumns == null || referencedColumns.isEmpty()) {
            throw new IllegalArgumentException("referencedColumns must not be empty");
        }
        columns = List.copyOf(columns);
        referencedColumns = List.copyOf(referencedColumns);
    }
}
