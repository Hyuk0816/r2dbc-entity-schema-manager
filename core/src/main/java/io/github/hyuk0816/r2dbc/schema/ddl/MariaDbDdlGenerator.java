package io.github.hyuk0816.r2dbc.schema.ddl;

import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiff;
import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiffType;
import io.github.hyuk0816.r2dbc.schema.model.ColumnDefinition;
import io.github.hyuk0816.r2dbc.schema.model.ForeignKeyDefinition;
import io.github.hyuk0816.r2dbc.schema.model.IndexDefinition;
import io.github.hyuk0816.r2dbc.schema.model.SchemaDefinition;
import io.github.hyuk0816.r2dbc.schema.model.TableDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public final class MariaDbDdlGenerator {

    public String createTable(TableDefinition table) {
        Objects.requireNonNull(table, "table must not be null");
        List<String> lines = new ArrayList<>();
        for (ColumnDefinition column : table.columns()) {
            lines.add("    " + columnSql(column));
        }
        if (!table.primaryKeyColumns().isEmpty()) {
            lines.add("    PRIMARY KEY (" + quotedList(table.primaryKeyColumns()) + ")");
        }

        return "CREATE TABLE " + quote(table.name()) + " (\n"
                + String.join(",\n", lines)
                + "\n);";
    }

    public List<DdlStatement> generate(SchemaDefinition expected, List<SchemaDiff> diffs) {
        Objects.requireNonNull(expected, "expected must not be null");
        Objects.requireNonNull(diffs, "diffs must not be null");

        List<DdlStatement> createTableStatements = new ArrayList<>();
        List<DdlStatement> addColumnStatements = new ArrayList<>();
        List<DdlStatement> modifyColumnStatements = new ArrayList<>();
        List<DdlStatement> addIndexStatements = new ArrayList<>();
        List<DdlStatement> addUniqueStatements = new ArrayList<>();
        List<DdlStatement> addForeignKeyStatements = new ArrayList<>();

        for (SchemaDiff diff : diffs) {
            TableDefinition table = expected.findTable(diff.tableName())
                    .orElseThrow(() -> new IllegalArgumentException("Expected table not found: " + diff.tableName()));
            switch (diff.type()) {
                case CREATE_TABLE -> {
                    createTableStatements.add(new DdlStatement(diff.type(), createTable(table)));
                    for (IndexDefinition index : table.indexes()) {
                        DdlStatement statement = createIndex(table, index);
                        if (index.unique()) {
                            addUniqueStatements.add(statement);
                        } else {
                            addIndexStatements.add(statement);
                        }
                    }
                    for (ForeignKeyDefinition foreignKey : table.foreignKeys()) {
                        addForeignKeyStatements.add(addForeignKey(table, foreignKey));
                    }
                }
                case ADD_COLUMN -> {
                    ColumnDefinition column = table.findColumn(diff.objectName())
                            .orElseThrow(() -> new IllegalArgumentException("Expected column not found: " + diff.objectName()));
                    addColumnStatements.add(new DdlStatement(diff.type(), addColumn(table, column)));
                }
                case MODIFY_COLUMN_TYPE -> {
                    ColumnDefinition column = table.findColumn(diff.objectName())
                            .orElseThrow(() -> new IllegalArgumentException("Expected column not found: " + diff.objectName()));
                    modifyColumnStatements.add(new DdlStatement(diff.type(), modifyColumn(table, column)));
                }
                case ADD_INDEX, ADD_UNIQUE_KEY -> {
                    IndexDefinition index = table.findIndex(diff.objectName())
                            .orElseThrow(() -> new IllegalArgumentException("Expected index not found: " + diff.objectName()));
                    DdlStatement statement = createIndex(table, index);
                    if (index.unique()) {
                        addUniqueStatements.add(statement);
                    } else {
                        addIndexStatements.add(statement);
                    }
                }
                case ADD_FOREIGN_KEY -> {
                    ForeignKeyDefinition foreignKey = table.findForeignKey(diff.objectName())
                            .orElseThrow(() -> new IllegalArgumentException("Expected foreign key not found: " + diff.objectName()));
                    addForeignKeyStatements.add(addForeignKey(table, foreignKey));
                }
                case NULLABILITY_MISMATCH,
                     DEFAULT_MISMATCH,
                     COMMENT_MISMATCH,
                     PRIMARY_KEY_MISMATCH,
                     FOREIGN_KEY_MISMATCH,
                     UNIQUE_KEY_MISMATCH,
                     INDEX_MISMATCH,
                     EXTRA_COLUMN,
                     EXTRA_TABLE -> {
                }
            }
        }

        List<DdlStatement> ordered = new ArrayList<>();
        ordered.addAll(createTableStatements);
        ordered.addAll(addColumnStatements);
        ordered.addAll(modifyColumnStatements);
        ordered.addAll(addIndexStatements);
        ordered.addAll(addUniqueStatements);
        ordered.addAll(addForeignKeyStatements);
        return List.copyOf(ordered);
    }

    private static String addColumn(TableDefinition table, ColumnDefinition column) {
        return "ALTER TABLE " + quote(table.name()) + " ADD COLUMN " + columnSql(column) + ";";
    }

    private static String modifyColumn(TableDefinition table, ColumnDefinition column) {
        return "ALTER TABLE " + quote(table.name()) + " MODIFY COLUMN " + columnSql(column) + ";";
    }

    private static DdlStatement createIndex(TableDefinition table, IndexDefinition index) {
        SchemaDiffType type = index.unique() ? SchemaDiffType.ADD_UNIQUE_KEY : SchemaDiffType.ADD_INDEX;
        String prefix = index.unique() ? "CREATE UNIQUE INDEX " : "CREATE INDEX ";
        String sql = prefix + quote(index.name())
                + " ON " + quote(table.name())
                + " (" + quotedList(index.columns()) + ");";
        return new DdlStatement(type, sql);
    }

    private static DdlStatement addForeignKey(TableDefinition table, ForeignKeyDefinition foreignKey) {
        String sql = "ALTER TABLE " + quote(table.name()) + "\n"
                + "    ADD CONSTRAINT " + quote(foreignKey.name()) + "\n"
                + "    FOREIGN KEY (" + quotedList(foreignKey.columns()) + ") REFERENCES "
                + quote(foreignKey.referencedTable())
                + " (" + quotedList(foreignKey.referencedColumns()) + ");";
        return new DdlStatement(SchemaDiffType.ADD_FOREIGN_KEY, sql);
    }

    private static String columnSql(ColumnDefinition column) {
        StringBuilder sql = new StringBuilder();
        sql.append(quote(column.name())).append(' ').append(column.type());
        if (!column.nullable()) {
            sql.append(" NOT NULL");
        }
        if (column.defaultValue() != null) {
            sql.append(" DEFAULT ").append(column.defaultValue());
        }
        if (column.comment() != null) {
            sql.append(" COMMENT '").append(escapeString(column.comment())).append("'");
        }
        return sql.toString();
    }

    private static String quotedList(List<String> identifiers) {
        StringJoiner joiner = new StringJoiner(", ");
        for (String identifier : identifiers) {
            joiner.add(quote(identifier));
        }
        return joiner.toString();
    }

    private static String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private static String escapeString(String value) {
        return value.replace("'", "''");
    }
}
