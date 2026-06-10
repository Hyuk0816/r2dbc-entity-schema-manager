package io.github.hyuk0816.r2dbc.schema.policy;

public record SchemaApplyOptions(
        boolean syncExistingColumnTypes,
        boolean applyIndexes,
        boolean applyUniqueIndexes,
        boolean applyForeignKeys,
        boolean failOnDangerousDiff
) {

    public static SchemaApplyOptions defaults() {
        return new SchemaApplyOptions(false, true, true, false, false);
    }
}
