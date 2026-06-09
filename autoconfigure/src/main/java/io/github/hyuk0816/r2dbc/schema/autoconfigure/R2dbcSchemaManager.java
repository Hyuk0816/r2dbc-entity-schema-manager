package io.github.hyuk0816.r2dbc.schema.autoconfigure;

import io.github.hyuk0816.r2dbc.schema.ddl.DdlStatement;
import io.github.hyuk0816.r2dbc.schema.ddl.MariaDbDdlGenerator;
import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiff;
import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiffEngine;
import io.github.hyuk0816.r2dbc.schema.diff.SchemaDiffType;
import io.github.hyuk0816.r2dbc.schema.model.ColumnDefinition;
import io.github.hyuk0816.r2dbc.schema.model.SchemaDefinition;
import io.github.hyuk0816.r2dbc.schema.policy.DiffDecision;
import io.github.hyuk0816.r2dbc.schema.policy.DiffPolicyEvaluator;
import io.github.hyuk0816.r2dbc.schema.policy.SchemaApplyOptions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

public final class R2dbcSchemaManager {

    private static final Log log = LogFactory.getLog(R2dbcSchemaManager.class);

    private final EntitySchemaScanner entitySchemaScanner;
    private final DatabaseSchemaReader databaseSchemaReader;
    private final DdlExecutor ddlExecutor;
    private final R2dbcSchemaManagerProperties properties;
    private final SchemaDiffEngine diffEngine = new SchemaDiffEngine();
    private final DiffPolicyEvaluator policyEvaluator = new DiffPolicyEvaluator();
    private final MariaDbDdlGenerator ddlGenerator = new MariaDbDdlGenerator();

    public R2dbcSchemaManager(
            EntitySchemaScanner entitySchemaScanner,
            DatabaseSchemaReader databaseSchemaReader,
            DdlExecutor ddlExecutor,
            R2dbcSchemaManagerProperties properties
    ) {
        this.entitySchemaScanner = entitySchemaScanner;
        this.databaseSchemaReader = databaseSchemaReader;
        this.ddlExecutor = ddlExecutor;
        this.properties = properties;
    }

    public R2dbcSchemaManagerReport synchronize() {
        SchemaDefinition expected = entitySchemaScanner.scan();
        String schema = properties.getSchema();
        if (schema == null || schema.isBlank()) {
            schema = databaseSchemaReader.currentSchema();
        }
        SchemaDefinition actual = databaseSchemaReader.read(schema);
        List<SchemaDiff> diffs = diffEngine.diff(expected, actual);
        SchemaApplyOptions options = properties.toApplyOptions();
        List<SchemaDiff> applyableDiffs = diffs.stream()
                .map(diff -> policyEvaluator.evaluate(diff, options))
                .filter(DiffDecision::applyable)
                .map(DiffDecision::diff)
                .filter(diff -> isSafeToApply(expected, diff))
                .toList();
        List<DdlStatement> statements = ddlGenerator.generate(expected, applyableDiffs);

        if (properties.getMode() == R2dbcSchemaManagerProperties.Mode.DRY_RUN) {
            logStatements("dry-run", statements);
            return new R2dbcSchemaManagerReport(schema, diffs, statements);
        }
        if (properties.getMode() == R2dbcSchemaManagerProperties.Mode.VALIDATE) {
            if (!diffs.isEmpty()) {
                throw new IllegalStateException("R2DBC schema validation failed with " + diffs.size() + " diff(s).");
            }
            return new R2dbcSchemaManagerReport(schema, diffs, List.of());
        }

        logStatements("apply", statements);
        ddlExecutor.execute(statements);
        return new R2dbcSchemaManagerReport(schema, diffs, statements);
    }

    private static void logStatements(String mode, List<DdlStatement> statements) {
        for (DdlStatement statement : statements) {
            log.info("R2DBC schema manager " + mode + " SQL: " + statement.sql());
        }
    }

    private static boolean isSafeToApply(SchemaDefinition expected, SchemaDiff diff) {
        if (diff.type() != SchemaDiffType.ADD_COLUMN) {
            return true;
        }
        ColumnDefinition column = expected.findTable(diff.tableName())
                .flatMap(table -> table.findColumn(diff.objectName()))
                .orElse(null);
        if (column == null) {
            return false;
        }
        return column.nullable() || column.defaultValue() != null;
    }
}
