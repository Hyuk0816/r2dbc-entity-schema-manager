package io.github.hyuk0816.r2dbc.schema.policy;

import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiff;

public record DiffDecision(SchemaDiff diff, DiffAction action) {

    public boolean applyable() {
        return action == DiffAction.APPLY;
    }
}
