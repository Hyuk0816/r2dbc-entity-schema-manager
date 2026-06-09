package io.github.hyuk0816.r2dbc.schema.model;

import java.util.List;
import java.util.Optional;

public record TableDefinition(
        String name,
        List<ColumnDefinition> columns,
        List<String> primaryKeyColumns,
        List<IndexDefinition> indexes,
        List<ForeignKeyDefinition> foreignKeys
) {

    public TableDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        columns = columns == null ? List.of() : List.copyOf(columns);
        primaryKeyColumns = primaryKeyColumns == null ? List.of() : List.copyOf(primaryKeyColumns);
        indexes = indexes == null ? List.of() : List.copyOf(indexes);
        foreignKeys = foreignKeys == null ? List.of() : List.copyOf(foreignKeys);
    }

    public Optional<ColumnDefinition> findColumn(String columnName) {
        return columns.stream()
                .filter(column -> column.name().equalsIgnoreCase(columnName))
                .findFirst();
    }

    public Optional<IndexDefinition> findIndex(String indexName) {
        return indexes.stream()
                .filter(index -> index.name().equalsIgnoreCase(indexName))
                .findFirst();
    }

    public Optional<ForeignKeyDefinition> findForeignKey(String foreignKeyName) {
        return foreignKeys.stream()
                .filter(foreignKey -> foreignKey.name().equalsIgnoreCase(foreignKeyName))
                .findFirst();
    }
}
