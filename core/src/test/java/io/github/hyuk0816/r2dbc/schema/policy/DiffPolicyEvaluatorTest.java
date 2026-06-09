package io.github.hyuk0816.r2dbc.schema.policy;

import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiff;
import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiffType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiffPolicyEvaluatorTest {

    private final DiffPolicyEvaluator evaluator = new DiffPolicyEvaluator();

    @Test
    void appliesForeignKeysOnlyWhenExplicitlyEnabled() {
        SchemaDiff diff = new SchemaDiff(SchemaDiffType.ADD_FOREIGN_KEY, "order_master", "fk_order_user", "");

        assertThat(evaluator.evaluate(diff, SchemaApplyOptions.defaults()).action())
                .isEqualTo(DiffAction.REPORT_ONLY);

        SchemaApplyOptions enabled = new SchemaApplyOptions(true, true, true, true, false);
        assertThat(evaluator.evaluate(diff, enabled).action())
                .isEqualTo(DiffAction.APPLY);
    }

    @Test
    void appliesConfiguredSafeDiffsAndReportsUnsafeDiffs() {
        SchemaApplyOptions defaults = SchemaApplyOptions.defaults();

        assertThat(evaluator.evaluate(diff(SchemaDiffType.CREATE_TABLE), defaults).action()).isEqualTo(DiffAction.APPLY);
        assertThat(evaluator.evaluate(diff(SchemaDiffType.ADD_COLUMN), defaults).action()).isEqualTo(DiffAction.APPLY);
        assertThat(evaluator.evaluate(diff(SchemaDiffType.ADD_INDEX), defaults).action()).isEqualTo(DiffAction.APPLY);
        assertThat(evaluator.evaluate(diff(SchemaDiffType.ADD_UNIQUE_KEY), defaults).action()).isEqualTo(DiffAction.APPLY);
        assertThat(evaluator.evaluate(diff(SchemaDiffType.MODIFY_COLUMN_TYPE), defaults).action()).isEqualTo(DiffAction.APPLY);
        assertThat(evaluator.evaluate(diff(SchemaDiffType.EXTRA_COLUMN), defaults).action()).isEqualTo(DiffAction.REPORT_ONLY);
        assertThat(evaluator.evaluate(diff(SchemaDiffType.PRIMARY_KEY_MISMATCH), defaults).action()).isEqualTo(DiffAction.REPORT_ONLY);
    }

    private static SchemaDiff diff(SchemaDiffType type) {
        return new SchemaDiff(type, "sample", "sample", "");
    }
}
