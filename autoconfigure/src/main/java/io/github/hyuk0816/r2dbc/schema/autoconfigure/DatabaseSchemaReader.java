package io.github.hyuk0816.r2dbc.schema.autoconfigure;

import io.github.hyuk0816.r2dbc.schema.model.ColumnDefinition;
import io.github.hyuk0816.r2dbc.schema.model.ForeignKeyDefinition;
import io.github.hyuk0816.r2dbc.schema.model.IndexDefinition;
import io.github.hyuk0816.r2dbc.schema.model.SchemaDefinition;
import io.github.hyuk0816.r2dbc.schema.model.TableDefinition;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DatabaseSchemaReader {

    private final DatabaseClient databaseClient;
    private final Duration timeout;

    public DatabaseSchemaReader(DatabaseClient databaseClient, Duration timeout) {
        this.databaseClient = databaseClient;
        this.timeout = timeout;
    }

    public String currentSchema() {
        return databaseClient.sql("SELECT DATABASE()")
                .map((row, metadata) -> row.get(0, String.class))
                .one()
                .block(timeout);
    }

    public SchemaDefinition read(String schema) {
        List<String> tableNames = readTableNames(schema);
        Map<String, List<ColumnDefinition>> columns = readColumns(schema);
        Map<String, List<IndexDefinition>> indexes = readIndexes(schema);
        ConstraintMetadata constraints = readConstraints(schema);

        List<TableDefinition> tables = new ArrayList<>();
        for (String tableName : tableNames) {
            tables.add(new TableDefinition(
                    tableName,
                    columns.getOrDefault(tableName, List.of()),
                    constraints.primaryKeys().getOrDefault(tableName, List.of()),
                    indexes.getOrDefault(tableName, List.of()),
                    constraints.foreignKeys().getOrDefault(tableName, List.of())
            ));
        }
        return new SchemaDefinition(tables);
    }

    private List<String> readTableNames(String schema) {
        return databaseClient.sql("""
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = :schema
                          AND table_type = 'BASE TABLE'
                        ORDER BY table_name
                        """)
                .bind("schema", schema)
                .map((row, metadata) -> row.get("table_name", String.class))
                .all()
                .collectList()
                .block(timeout);
    }

    private Map<String, List<ColumnDefinition>> readColumns(String schema) {
        List<ColumnRow> rows = databaseClient.sql("""
                        SELECT table_name,
                               column_name,
                               data_type,
                               character_maximum_length,
                               numeric_precision,
                               numeric_scale,
                               is_nullable,
                               column_default,
                               column_comment
                        FROM information_schema.columns
                        WHERE table_schema = :schema
                        ORDER BY table_name, ordinal_position
                        """)
                .bind("schema", schema)
                .map((row, metadata) -> new ColumnRow(
                        row.get("table_name", String.class),
                        row.get("column_name", String.class),
                        row.get("data_type", String.class),
                        row.get("character_maximum_length", Number.class),
                        row.get("numeric_precision", Number.class),
                        row.get("numeric_scale", Number.class),
                        row.get("is_nullable", String.class),
                        row.get("column_default", String.class),
                        row.get("column_comment", String.class)
                ))
                .all()
                .collectList()
                .block(timeout);

        Map<String, List<ColumnDefinition>> columns = new LinkedHashMap<>();
        for (ColumnRow row : rows) {
            columns.computeIfAbsent(row.tableName(), ignored -> new ArrayList<>())
                    .add(new ColumnDefinition(
                            row.columnName(),
                            toColumnType(row),
                            "YES".equalsIgnoreCase(row.nullable()),
                            row.defaultValue(),
                            row.comment()
                    ));
        }
        return columns;
    }

    private Map<String, List<IndexDefinition>> readIndexes(String schema) {
        List<IndexRow> rows = databaseClient.sql("""
                        SELECT table_name,
                               index_name,
                               non_unique,
                               column_name,
                               seq_in_index
                        FROM information_schema.statistics
                        WHERE table_schema = :schema
                          AND index_name <> 'PRIMARY'
                        ORDER BY table_name, index_name, seq_in_index
                        """)
                .bind("schema", schema)
                .map((row, metadata) -> new IndexRow(
                        row.get("table_name", String.class),
                        row.get("index_name", String.class),
                        row.get("non_unique", Number.class),
                        row.get("column_name", String.class),
                        row.get("seq_in_index", Number.class)
                ))
                .all()
                .collectList()
                .block(timeout);

        Map<String, Map<String, MutableIndex>> grouped = new LinkedHashMap<>();
        for (IndexRow row : rows) {
            grouped.computeIfAbsent(row.tableName(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(row.indexName(), ignored -> new MutableIndex(row.indexName(), row.nonUnique().intValue() == 0))
                    .add(row.columnName(), row.sequence().intValue());
        }

        Map<String, List<IndexDefinition>> indexes = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, MutableIndex>> tableEntry : grouped.entrySet()) {
            List<IndexDefinition> tableIndexes = tableEntry.getValue().values().stream()
                    .map(MutableIndex::toDefinition)
                    .toList();
            indexes.put(tableEntry.getKey(), tableIndexes);
        }
        return indexes;
    }

    private ConstraintMetadata readConstraints(String schema) {
        List<ConstraintRow> rows = databaseClient.sql("""
                        SELECT tc.table_name,
                               tc.constraint_name,
                               tc.constraint_type,
                               kcu.column_name,
                               kcu.referenced_table_name,
                               kcu.referenced_column_name,
                               kcu.ordinal_position
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON tc.constraint_schema = kcu.constraint_schema
                         AND tc.table_name = kcu.table_name
                         AND tc.constraint_name = kcu.constraint_name
                        WHERE tc.constraint_schema = :schema
                        ORDER BY tc.table_name, tc.constraint_name, kcu.ordinal_position
                        """)
                .bind("schema", schema)
                .map((row, metadata) -> new ConstraintRow(
                        row.get("table_name", String.class),
                        row.get("constraint_name", String.class),
                        row.get("constraint_type", String.class),
                        row.get("column_name", String.class),
                        row.get("referenced_table_name", String.class),
                        row.get("referenced_column_name", String.class),
                        row.get("ordinal_position", Number.class)
                ))
                .all()
                .collectList()
                .block(timeout);

        Map<String, List<String>> primaryKeys = new LinkedHashMap<>();
        Map<String, Map<String, MutableForeignKey>> foreignKeys = new LinkedHashMap<>();
        for (ConstraintRow row : rows) {
            if ("PRIMARY KEY".equalsIgnoreCase(row.type())) {
                primaryKeys.computeIfAbsent(row.tableName(), ignored -> new ArrayList<>()).add(row.columnName());
            } else if ("FOREIGN KEY".equalsIgnoreCase(row.type())) {
                foreignKeys.computeIfAbsent(row.tableName(), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(row.constraintName(), ignored -> new MutableForeignKey(
                                row.constraintName(),
                                row.referencedTable()
                        ))
                        .add(row.columnName(), row.referencedColumn(), row.ordinalPosition().intValue());
            }
        }

        Map<String, List<ForeignKeyDefinition>> resolvedForeignKeys = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, MutableForeignKey>> tableEntry : foreignKeys.entrySet()) {
            resolvedForeignKeys.put(tableEntry.getKey(), tableEntry.getValue().values().stream()
                    .map(MutableForeignKey::toDefinition)
                    .toList());
        }
        return new ConstraintMetadata(primaryKeys, resolvedForeignKeys);
    }

    private static String toColumnType(ColumnRow row) {
        String dataType = row.dataType().toLowerCase();
        if (row.characterLength() != null && needsCharacterLength(dataType)) {
            return dataType + "(" + row.characterLength().longValue() + ")";
        }
        if (row.numericPrecision() != null && row.numericScale() != null && needsPrecisionAndScale(dataType)) {
            return dataType + "(" + row.numericPrecision().longValue() + "," + row.numericScale().longValue() + ")";
        }
        return dataType;
    }

    private static boolean needsCharacterLength(String dataType) {
        return dataType.contains("char") || "binary".equals(dataType) || "varbinary".equals(dataType);
    }

    private static boolean needsPrecisionAndScale(String dataType) {
        return "decimal".equals(dataType) || "numeric".equals(dataType);
    }

    private record ColumnRow(
            String tableName,
            String columnName,
            String dataType,
            Number characterLength,
            Number numericPrecision,
            Number numericScale,
            String nullable,
            String defaultValue,
            String comment
    ) {
    }

    private record IndexRow(
            String tableName,
            String indexName,
            Number nonUnique,
            String columnName,
            Number sequence
    ) {
    }

    private record ConstraintRow(
            String tableName,
            String constraintName,
            String type,
            String columnName,
            String referencedTable,
            String referencedColumn,
            Number ordinalPosition
    ) {
    }

    private record ConstraintMetadata(
            Map<String, List<String>> primaryKeys,
            Map<String, List<ForeignKeyDefinition>> foreignKeys
    ) {
    }

    private static final class MutableIndex {

        private final String name;
        private final boolean unique;
        private final List<OrderedColumn> columns = new ArrayList<>();

        private MutableIndex(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }

        private void add(String columnName, int order) {
            columns.add(new OrderedColumn(columnName, order));
        }

        private IndexDefinition toDefinition() {
            return new IndexDefinition(
                    name,
                    columns.stream()
                            .sorted(Comparator.comparingInt(OrderedColumn::order))
                            .map(OrderedColumn::name)
                            .toList(),
                    unique
            );
        }
    }

    private static final class MutableForeignKey {

        private final String name;
        private final String referencedTable;
        private final List<OrderedColumn> columns = new ArrayList<>();
        private final List<OrderedColumn> referencedColumns = new ArrayList<>();

        private MutableForeignKey(String name, String referencedTable) {
            this.name = name;
            this.referencedTable = referencedTable;
        }

        private void add(String columnName, String referencedColumnName, int order) {
            columns.add(new OrderedColumn(columnName, order));
            referencedColumns.add(new OrderedColumn(referencedColumnName, order));
        }

        private ForeignKeyDefinition toDefinition() {
            return new ForeignKeyDefinition(
                    name,
                    orderedNames(columns),
                    referencedTable,
                    orderedNames(referencedColumns)
            );
        }

        private static List<String> orderedNames(List<OrderedColumn> columns) {
            return columns.stream()
                    .sorted(Comparator.comparingInt(OrderedColumn::order))
                    .map(OrderedColumn::name)
                    .toList();
        }
    }

    private record OrderedColumn(String name, int order) {
    }
}
