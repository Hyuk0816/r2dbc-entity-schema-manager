package io.github.hyuk0816.r2dbc.schema.ddl;

import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiff;
import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiffType;
import io.github.hyuk0816.r2dbc.schema.model.ColumnDefinition;
import io.github.hyuk0816.r2dbc.schema.model.ForeignKeyDefinition;
import io.github.hyuk0816.r2dbc.schema.model.IndexDefinition;
import io.github.hyuk0816.r2dbc.schema.model.SchemaDefinition;
import io.github.hyuk0816.r2dbc.schema.model.TableDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MariaDbDdlGeneratorTest {

    private final MariaDbDdlGenerator generator = new MariaDbDdlGenerator();

    @Test
    void createsMariaDbCreateTableSqlWithPrimaryKeyAndColumnOptions() {
        assertThat(generator.createTable(userTable())).isEqualTo("""
                CREATE TABLE `user_master` (
                    `id` varchar(36) NOT NULL,
                    `user_name` varchar(100) NOT NULL DEFAULT '' COMMENT '사용자명',
                    `group_id` bigint,
                    PRIMARY KEY (`id`)
                );""");
    }

    @Test
    void generatesOrderedStatementsWithForeignKeysLast() {
        SchemaDefinition expected = new SchemaDefinition(List.of(userTable()));
        List<SchemaDiff> diffs = List.of(new SchemaDiff(
                SchemaDiffType.CREATE_TABLE,
                "user_master",
                "user_master",
                "table missing"
        ));

        List<DdlStatement> statements = generator.generate(expected, diffs);

        assertThat(statements).extracting(DdlStatement::sql).containsExactly(
                """
                        CREATE TABLE `user_master` (
                            `id` varchar(36) NOT NULL,
                            `user_name` varchar(100) NOT NULL DEFAULT '' COMMENT '사용자명',
                            `group_id` bigint,
                            PRIMARY KEY (`id`)
                        );""",
                "CREATE INDEX `idx_user_master_user_name` ON `user_master` (`user_name`);",
                "CREATE UNIQUE INDEX `uk_user_master_user_name` ON `user_master` (`user_name`);",
                """
                        ALTER TABLE `user_master`
                            ADD CONSTRAINT `fk_user_master_group_id`
                            FOREIGN KEY (`group_id`) REFERENCES `group_master` (`id`);"""
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
