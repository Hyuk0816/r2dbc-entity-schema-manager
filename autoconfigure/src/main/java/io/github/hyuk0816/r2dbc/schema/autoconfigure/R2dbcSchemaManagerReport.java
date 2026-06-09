package io.github.hyuk0816.r2dbc.schema.autoconfigure;

import io.github.hyuk0816.r2dbc.schema.ddl.DdlStatement;
import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiff;

import java.util.List;

public record R2dbcSchemaManagerReport(
        String schema,
        List<SchemaDiff> diffs,
        List<DdlStatement> statements
) {

    public R2dbcSchemaManagerReport {
        diffs = diffs == null ? List.of() : List.copyOf(diffs);
        statements = statements == null ? List.of() : List.copyOf(statements);
    }
}
