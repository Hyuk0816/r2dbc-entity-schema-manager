package io.github.hyuk0816.r2dbc.schema.sample;

import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class MariaDbSmokeApplicationTest {

    @Container
    static final MariaDBContainer<?> mariaDb = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("schema_manager_sample")
            .withUsername("sample_user")
            .withPassword("sample_password");

    @Autowired
    private ConnectionFactory connectionFactory;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", MariaDbSmokeApplicationTest::r2dbcUrl);
        registry.add("spring.r2dbc.username", mariaDb::getUsername);
        registry.add("spring.r2dbc.password", mariaDb::getPassword);
        registry.add("r2dbc-schema-manager.enabled", () -> "true");
        registry.add("r2dbc-schema-manager.mode", () -> "apply");
        registry.add("r2dbc-schema-manager.apply-foreign-keys", () -> "true");
        registry.add("r2dbc-schema-manager.execution-timeout", () -> "30s");
    }

    @Test
    void starterCreatesSchemaAgainstRealMariaDb() {
        DatabaseClient databaseClient = DatabaseClient.create(connectionFactory);

        assertThat(tableExists(databaseClient, "sample_group")).isTrue();
        assertThat(tableExists(databaseClient, "sample_user")).isTrue();
        assertThat(indexExists(databaseClient, "sample_user", "idx_sample_user_email", false)).isTrue();
        assertThat(indexExists(databaseClient, "sample_user", "uk_sample_user_email", true)).isTrue();
        assertThat(foreignKeyExists(databaseClient)).isTrue();
    }

    private static String r2dbcUrl() {
        return "r2dbc:mariadb://"
                + mariaDb.getHost()
                + ":"
                + mariaDb.getMappedPort(3306)
                + "/"
                + mariaDb.getDatabaseName();
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

    private static boolean foreignKeyExists(DatabaseClient databaseClient) {
        Long count = databaseClient.sql("""
                        SELECT COUNT(*)
                        FROM information_schema.key_column_usage
                        WHERE constraint_schema = DATABASE()
                          AND table_name = 'sample_user'
                          AND constraint_name = 'fk_sample_user_group_id'
                          AND column_name = 'group_id'
                          AND referenced_table_name = 'sample_group'
                          AND referenced_column_name = 'id'
                        """)
                .map((row, metadata) -> row.get(0, Number.class).longValue())
                .one()
                .block(Duration.ofSeconds(10));
        return count != null && count == 1L;
    }
}
