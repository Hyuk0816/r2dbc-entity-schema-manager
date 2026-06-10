package io.github.hyuk0816.r2dbc.schema.autoconfigure;

import io.github.hyuk0816.r2dbc.schema.annotation.DdlColumn;
import io.github.hyuk0816.r2dbc.schema.annotation.DdlForeignKey;
import io.github.hyuk0816.r2dbc.schema.annotation.DdlIndex;
import io.github.hyuk0816.r2dbc.schema.annotation.DdlUnique;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class R2dbcSchemaManagerMariaDbIntegrationTest {

    @Container
    static final MariaDBContainer<?> mariaDb = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("schema_manager_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @Test
    void applyModeCreatesTablesIndexesUniqueKeysAndForeignKeysInMariaDb() {
        contextRunner(GroupMaster.class, UserMaster.class)
                .withPropertyValues(
                        "r2dbc-schema-manager.enabled=true",
                        "r2dbc-schema-manager.mode=apply",
                        "r2dbc-schema-manager.apply-foreign-keys=true",
                        "r2dbc-schema-manager.execution-timeout=30s"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    DatabaseClient databaseClient = DatabaseClient.create(context.getBean(ConnectionFactory.class));
                    assertThat(tableExists(databaseClient, "group_master")).isTrue();
                    assertThat(tableExists(databaseClient, "user_master")).isTrue();
                    assertThat(column(databaseClient, "user_master", "user_name"))
                            .containsEntry("data_type", "varchar")
                            .containsEntry("character_maximum_length", 100L)
                            .containsEntry("is_nullable", "NO");
                    assertThat(indexExists(databaseClient, "user_master", "idx_user_master_user_name", false)).isTrue();
                    assertThat(indexExists(databaseClient, "user_master", "uk_user_master_user_name", true)).isTrue();
                    assertThat(foreignKeyExists(
                            databaseClient,
                            "user_master",
                            "fk_user_master_group_id",
                            "group_id",
                            "group_master",
                            "id"
                    )).isTrue();
                });
    }

    @Test
    void applyModeCreatesCompositeTypeIndexesAndUsesDdlColumnLengthWithInferredType() {
        DatabaseClient databaseClient = DatabaseClient.create(connectionFactory());
        execute(databaseClient, "DROP TABLE IF EXISTS `r2dbc_entity_test`;");

        contextRunner(TestTable.class)
                .withPropertyValues(
                        "r2dbc-schema-manager.enabled=true",
                        "r2dbc-schema-manager.mode=apply",
                        "r2dbc-schema-manager.execution-timeout=30s"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(tableExists(databaseClient, "r2dbc_entity_test")).isTrue();
                    assertThat(column(databaseClient, "r2dbc_entity_test", "description"))
                            .containsEntry("data_type", "varchar")
                            .containsEntry("character_maximum_length", 500L);
                    assertThat(indexColumns(databaseClient, "r2dbc_entity_test", "idx_test"))
                            .containsExactly("name", "description");
                    assertThat(indexColumns(databaseClient, "r2dbc_entity_test", "uk_test"))
                            .containsExactly("name", "description");
                    assertThat(indexExists(databaseClient, "r2dbc_entity_test", "idx_test", false)).isTrue();
                    assertThat(indexExists(databaseClient, "r2dbc_entity_test", "uk_test", true)).isTrue();
                });
    }

    @Test
    void applyModeReportsRequiredColumnWithoutDefaultForExistingRowsWithoutAddingIt() {
        DatabaseClient databaseClient = DatabaseClient.create(connectionFactory());
        execute(databaseClient, "DROP TABLE IF EXISTS `legacy_account`;");
        execute(databaseClient, "CREATE TABLE `legacy_account` (`id` bigint NOT NULL, PRIMARY KEY (`id`));");
        execute(databaseClient, "INSERT INTO `legacy_account` (`id`) VALUES (1);");

        contextRunner(LegacyAccount.class)
                .withPropertyValues(
                        "r2dbc-schema-manager.enabled=true",
                        "r2dbc-schema-manager.mode=apply",
                        "r2dbc-schema-manager.execution-timeout=30s"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(columnExists(databaseClient, "legacy_account", "required_name")).isFalse();
                });
    }

    @Test
    void dryRunModeReportsSqlWithoutChangingMariaDbSchema() {
        DatabaseClient databaseClient = DatabaseClient.create(connectionFactory());
        execute(databaseClient, "DROP TABLE IF EXISTS `dry_run_account`;");

        contextRunner(DryRunAccount.class)
                .withPropertyValues(
                        "r2dbc-schema-manager.enabled=true",
                        "r2dbc-schema-manager.mode=dry-run",
                        "r2dbc-schema-manager.execution-timeout=30s"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(tableExists(databaseClient, "dry_run_account")).isFalse();
                });
    }

    @Test
    void validateModeFailsStartupWhenSchemaDiffExists() {
        DatabaseClient databaseClient = DatabaseClient.create(connectionFactory());
        execute(databaseClient, "DROP TABLE IF EXISTS `validate_account`;");

        contextRunner(ValidateAccount.class)
                .withPropertyValues(
                        "r2dbc-schema-manager.enabled=true",
                        "r2dbc-schema-manager.mode=validate",
                        "r2dbc-schema-manager.execution-timeout=30s"
                )
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("R2DBC schema validation failed"));
    }

    private static ApplicationContextRunner contextRunner(Class<?>... entityTypes) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(R2dbcSchemaManagerAutoConfiguration.class))
                .withBean(ConnectionFactory.class, R2dbcSchemaManagerMariaDbIntegrationTest::connectionFactory)
                .withBean(RelationalMappingContext.class, () -> mappingContext(entityTypes));
    }

    private static ConnectionFactory connectionFactory() {
        ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
                .option(DRIVER, "mariadb")
                .option(HOST, mariaDb.getHost())
                .option(PORT, mariaDb.getMappedPort(3306))
                .option(USER, mariaDb.getUsername())
                .option(PASSWORD, mariaDb.getPassword())
                .option(DATABASE, mariaDb.getDatabaseName())
                .build();
        return ConnectionFactories.get(options);
    }

    private static RelationalMappingContext mappingContext(Class<?>... entityTypes) {
        RelationalMappingContext mappingContext = new RelationalMappingContext();
        mappingContext.setInitialEntitySet(Set.copyOf(Arrays.asList(entityTypes)));
        return mappingContext;
    }

    private static void execute(DatabaseClient databaseClient, String sql) {
        databaseClient.sql(sql)
                .fetch()
                .rowsUpdated()
                .block(Duration.ofSeconds(10));
    }

    private static boolean tableExists(DatabaseClient databaseClient, String tableName) {
        Long count = databaseClient.sql("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                          AND table_name = :tableName
                        """)
                .bind("tableName", tableName)
                .map((row, metadata) -> row.get(0, Number.class).longValue())
                .one()
                .block(Duration.ofSeconds(10));
        return count != null && count == 1L;
    }

    private static boolean columnExists(DatabaseClient databaseClient, String tableName, String columnName) {
        Long count = databaseClient.sql("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = :tableName
                          AND column_name = :columnName
                        """)
                .bind("tableName", tableName)
                .bind("columnName", columnName)
                .map((row, metadata) -> row.get(0, Number.class).longValue())
                .one()
                .block(Duration.ofSeconds(10));
        return count != null && count == 1L;
    }

    private static Map<String, Object> column(DatabaseClient databaseClient, String tableName, String columnName) {
        return databaseClient.sql("""
                        SELECT data_type,
                               character_maximum_length,
                               is_nullable
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = :tableName
                          AND column_name = :columnName
                        """)
                .bind("tableName", tableName)
                .bind("columnName", columnName)
                .map((row, metadata) -> Map.<String, Object>of(
                        "data_type", row.get("data_type", String.class),
                        "character_maximum_length", row.get("character_maximum_length", Number.class).longValue(),
                        "is_nullable", row.get("is_nullable", String.class)
                ))
                .one()
                .block(Duration.ofSeconds(10));
    }

    private static boolean indexExists(DatabaseClient databaseClient, String tableName, String indexName, boolean unique) {
        Long count = databaseClient.sql("""
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = :tableName
                          AND index_name = :indexName
                          AND non_unique = :nonUnique
                        """)
                .bind("tableName", tableName)
                .bind("indexName", indexName)
                .bind("nonUnique", unique ? 0 : 1)
                .map((row, metadata) -> row.get(0, Number.class).longValue())
                .one()
                .block(Duration.ofSeconds(10));
        return count != null && count >= 1L;
    }

    private static List<String> indexColumns(DatabaseClient databaseClient, String tableName, String indexName) {
        return databaseClient.sql("""
                        SELECT column_name
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = :tableName
                          AND index_name = :indexName
                        ORDER BY seq_in_index
                        """)
                .bind("tableName", tableName)
                .bind("indexName", indexName)
                .map((row, metadata) -> row.get("column_name", String.class))
                .all()
                .collectList()
                .block(Duration.ofSeconds(10));
    }

    private static boolean foreignKeyExists(
            DatabaseClient databaseClient,
            String tableName,
            String constraintName,
            String columnName,
            String referencedTableName,
            String referencedColumnName
    ) {
        Long count = databaseClient.sql("""
                        SELECT COUNT(*)
                        FROM information_schema.key_column_usage
                        WHERE constraint_schema = DATABASE()
                          AND table_name = :tableName
                          AND constraint_name = :constraintName
                          AND column_name = :columnName
                          AND referenced_table_name = :referencedTableName
                          AND referenced_column_name = :referencedColumnName
                        """)
                .bind("tableName", tableName)
                .bind("constraintName", constraintName)
                .bind("columnName", columnName)
                .bind("referencedTableName", referencedTableName)
                .bind("referencedColumnName", referencedColumnName)
                .map((row, metadata) -> row.get(0, Number.class).longValue())
                .one()
                .block(Duration.ofSeconds(10));
        return count != null && count == 1L;
    }

    @Table("group_master")
    static final class GroupMaster {

        @Id
        Long id;

        @DdlColumn(type = "varchar", length = 100, nullable = false, defaultValue = "'general'")
        String groupName;
    }

    @Table("user_master")
    @DdlUnique(name = "uk_user_master_user_name", columns = "user_name")
    static final class UserMaster {

        @Id
        Long id;

        @DdlIndex(name = "idx_user_master_user_name")
        @DdlColumn(type = "varchar", length = 100, nullable = false, defaultValue = "'anonymous'")
        String userName;

        @DdlForeignKey(
                name = "fk_user_master_group_id",
                referencedTable = "group_master",
                referencedColumn = "id"
        )
        Long groupId;
    }

    @Table("r2dbc_entity_test")
    @DdlIndex(name = "idx_test", columns = {"name", "description"})
    @DdlUnique(name = "uk_test", columns = {"name", "description"})
    static final class TestTable {

        @Id
        String id;

        String name;

        @DdlColumn(name = "description", length = 500)
        String description;

        String test;
    }

    @Table("legacy_account")
    static final class LegacyAccount {

        @Id
        Long id;

        @DdlColumn(type = "varchar", length = 100, nullable = false)
        String requiredName;
    }

    @Table("dry_run_account")
    static final class DryRunAccount {

        @Id
        Long id;
    }

    @Table("validate_account")
    static final class ValidateAccount {

        @Id
        Long id;
    }
}
