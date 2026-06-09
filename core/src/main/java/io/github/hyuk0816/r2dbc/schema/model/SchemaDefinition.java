package io.github.hyuk0816.r2dbc.schema.model;

import java.util.List;
import java.util.Optional;

public record SchemaDefinition(List<TableDefinition> tables) {

    public SchemaDefinition {
        tables = tables == null ? List.of() : List.copyOf(tables);
    }

    public Optional<TableDefinition> findTable(String tableName) {
        return tables.stream()
                .filter(table -> table.name().equalsIgnoreCase(tableName))
                .findFirst();
    }
}
