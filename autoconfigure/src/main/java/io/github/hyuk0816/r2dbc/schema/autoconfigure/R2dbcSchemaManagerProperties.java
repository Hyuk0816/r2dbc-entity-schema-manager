package io.github.hyuk0816.r2dbc.schema.autoconfigure;

import io.github.hyuk0816.r2dbc.schema.naming.NameCase;
import io.github.hyuk0816.r2dbc.schema.policy.SchemaApplyOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("r2dbc-schema-manager")
public class R2dbcSchemaManagerProperties {

    private boolean enabled;
    private Mode mode = Mode.DRY_RUN;
    private Dialect dialect = Dialect.MARIADB;
    private String schema;
    private NameCaseOption nameCase = NameCaseOption.SPRING;
    private boolean syncExistingColumnTypes;
    private boolean applyIndexes = true;
    private boolean applyUniqueIndexes = true;
    private boolean applyForeignKeys;
    private boolean failOnDangerousDiff;
    private Duration executionTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Dialect getDialect() {
        return dialect;
    }

    public void setDialect(Dialect dialect) {
        this.dialect = dialect;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public NameCaseOption getNameCase() {
        return nameCase;
    }

    public void setNameCase(NameCaseOption nameCase) {
        this.nameCase = nameCase;
    }

    public boolean isSyncExistingColumnTypes() {
        return syncExistingColumnTypes;
    }

    public void setSyncExistingColumnTypes(boolean syncExistingColumnTypes) {
        this.syncExistingColumnTypes = syncExistingColumnTypes;
    }

    public boolean isApplyIndexes() {
        return applyIndexes;
    }

    public void setApplyIndexes(boolean applyIndexes) {
        this.applyIndexes = applyIndexes;
    }

    public boolean isApplyUniqueIndexes() {
        return applyUniqueIndexes;
    }

    public void setApplyUniqueIndexes(boolean applyUniqueIndexes) {
        this.applyUniqueIndexes = applyUniqueIndexes;
    }

    public boolean isApplyForeignKeys() {
        return applyForeignKeys;
    }

    public void setApplyForeignKeys(boolean applyForeignKeys) {
        this.applyForeignKeys = applyForeignKeys;
    }

    public boolean isFailOnDangerousDiff() {
        return failOnDangerousDiff;
    }

    public void setFailOnDangerousDiff(boolean failOnDangerousDiff) {
        this.failOnDangerousDiff = failOnDangerousDiff;
    }

    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    public void setExecutionTimeout(Duration executionTimeout) {
        this.executionTimeout = executionTimeout;
    }

    public SchemaApplyOptions toApplyOptions() {
        return new SchemaApplyOptions(
                syncExistingColumnTypes,
                applyIndexes,
                applyUniqueIndexes,
                applyForeignKeys,
                failOnDangerousDiff
        );
    }

    public enum Mode {
        DRY_RUN,
        VALIDATE,
        APPLY
    }

    public enum Dialect {
        MARIADB
    }

    public enum NameCaseOption {
        SPRING(NameCase.SPRING),
        SNAKE_CASE(NameCase.SNAKE_CASE),
        LOWER_CAMEL(NameCase.LOWER_CAMEL),
        UPPER_CAMEL(NameCase.UPPER_CAMEL),
        LOWER(NameCase.LOWER),
        UPPER(NameCase.UPPER),
        AS_IS(NameCase.AS_IS);

        private final NameCase coreNameCase;

        NameCaseOption(NameCase coreNameCase) {
            this.coreNameCase = coreNameCase;
        }

        public NameCase toCoreNameCase() {
            return coreNameCase;
        }
    }
}
