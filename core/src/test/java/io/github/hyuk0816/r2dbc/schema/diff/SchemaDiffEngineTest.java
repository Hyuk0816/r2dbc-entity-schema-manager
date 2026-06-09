package io.github.hyuk0816.r2dbc.schema.diff;

import io.github.hyuk0816.r2dbc.schema.model.ColumnDefinition;
import io.github.hyuk0816.r2dbc.schema.model.ForeignKeyDefinition;
import io.github.hyuk0816.r2dbc.schema.model.IndexDefinition;
import io.github.hyuk0816.r2dbc.schema.model.SchemaDefinition;
import io.github.hyuk0816.r2dbc.schema.model.TableDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaDiffEngineTest {

    private final SchemaDiffEngine engine = new SchemaDiffEngine();

    @Test
    void detectsMissingTableAsCreateTableWithoutDuplicatingColumnDiffs() {
        SchemaDefinition expected = new SchemaDefinition(List.of(userTable()));
        SchemaDefinition actual = new SchemaDefinition(List.of());

        List<SchemaDiff> diffs = engine.diff(expected, actual);

        assertThat(diffs)
                .extracting(SchemaDiff::type)
                .containsExactly(SchemaDiffType.CREATE_TABLE);
    }

    @Test
    void detectsColumnIndexUniqueForeignKeyAndExtraColumnDiffsForExistingTable() {
        TableDefinition expected = userTable();
        TableDefinition actual = new TableDefinition(
                "user_master",
                List.of(
                        new ColumnDefinition("id", "varchar(36)", false, null, null),
                        new ColumnDefinition("legacy_name", "varchar(255)", true, null, null)
                ),
                List.of("id"),
                List.of(),
                List.of()
        );

        List<SchemaDiff> diffs = engine.diff(
                new SchemaDefinition(List.of(expected)),
                new SchemaDefinition(List.of(actual))
        );

        assertThat(diffs)
                .extracting(SchemaDiff::type)
                .containsExactlyInAnyOrder(
                        SchemaDiffType.ADD_COLUMN,
                        SchemaDiffType.ADD_COLUMN,
                        SchemaDiffType.ADD_INDEX,
                        SchemaDiffType.ADD_UNIQUE_KEY,
                        SchemaDiffType.ADD_FOREIGN_KEY,
                        SchemaDiffType.EXTRA_COLUMN
                );
        assertThat(diffs)
                .filteredOn(diff -> diff.type() == SchemaDiffType.ADD_COLUMN)
                .extracting(SchemaDiff::objectName)
                .containsExactlyInAnyOrder("user_name", "group_id");
    }

    @Test
    void detectsExistingColumnMetadataMismatches() {
        TableDefinition expected = new TableDefinition(
                "user_master",
                List.of(new ColumnDefinition("user_name", "varchar(100)", false, "''", "사용자명")),
                List.of(),
                List.of(),
                List.of()
        );
        TableDefinition actual = new TableDefinition(
                "user_master",
                List.of(new ColumnDefinition("user_name", "varchar(50)", true, null, null)),
                List.of(),
                List.of(),
                List.of()
        );

        List<SchemaDiff> diffs = engine.diff(
                new SchemaDefinition(List.of(expected)),
                new SchemaDefinition(List.of(actual))
        );

        assertThat(diffs)
                .extracting(SchemaDiff::type)
                .containsExactlyInAnyOrder(
                        SchemaDiffType.MODIFY_COLUMN_TYPE,
                        SchemaDiffType.NULLABILITY_MISMATCH,
                        SchemaDiffType.DEFAULT_MISMATCH,
                        SchemaDiffType.COMMENT_MISMATCH
                );
    }

    private static TableDefinition userTable() {
        return new TableDefinition(
                "user_master",
                List.of(
                        new ColumnDefinition("id", "varchar(36)", false, null, null),
                        new ColumnDefinition("user_name", "varchar(100)", false, "''", "사용자명"),
                        new ColumnDefinition("group_id", "bigint", true, null, null)
                ),
                List.of("id"),
                List.of(
                        new IndexDefinition("idx_user_master_user_name", List.of("user_name"), false),
                        new IndexDefinition("uk_user_master_user_name", List.of("user_name"), true)
                ),
                List.of(new ForeignKeyDefinition(
                        "fk_user_master_group_id",
                        List.of("group_id"),
                        "group_master",
                        List.of("id")
                ))
        );
    }
}
