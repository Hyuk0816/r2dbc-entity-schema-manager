package io.github.hyuk0816.r2dbc.schema.policy;

import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiff;
import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiffType;

import java.util.Objects;

public final class DiffPolicyEvaluator {

    public DiffDecision evaluate(SchemaDiff diff, SchemaApplyOptions options) {
        Objects.requireNonNull(diff, "diff must not be null");
        Objects.requireNonNull(options, "options must not be null");

        DiffAction action = switch (diff.type()) {
            case CREATE_TABLE, ADD_COLUMN -> DiffAction.APPLY;
            case ADD_INDEX -> options.applyIndexes() ? DiffAction.APPLY : DiffAction.REPORT_ONLY;
            case ADD_UNIQUE_KEY -> options.applyUniqueIndexes() ? DiffAction.APPLY : DiffAction.REPORT_ONLY;
            case ADD_FOREIGN_KEY -> options.applyForeignKeys() ? DiffAction.APPLY : DiffAction.REPORT_ONLY;
            case MODIFY_COLUMN_TYPE -> options.syncExistingColumnTypes() ? DiffAction.APPLY : DiffAction.REPORT_ONLY;
            case NULLABILITY_MISMATCH,
                 DEFAULT_MISMATCH,
                 COMMENT_MISMATCH,
                 PRIMARY_KEY_MISMATCH,
                 FOREIGN_KEY_MISMATCH,
                 UNIQUE_KEY_MISMATCH,
                 INDEX_MISMATCH,
                 EXTRA_COLUMN,
                 EXTRA_TABLE -> DiffAction.REPORT_ONLY;
        };
        return new DiffDecision(diff, action);
    }
}
