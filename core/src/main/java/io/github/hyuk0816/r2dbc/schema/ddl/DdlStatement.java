package io.github.hyuk0816.r2dbc.schema.ddl;

import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiffType;

public record DdlStatement(SchemaDiffType sourceType, String sql) {
}
