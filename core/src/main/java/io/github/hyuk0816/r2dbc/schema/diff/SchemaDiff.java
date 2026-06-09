package io.github.hyuk0816.r2dbc.schema.diff;

public record SchemaDiff(
        SchemaDiffType type,
        String tableName,
        String objectName,
        String message
) {
}
