package io.github.hyuk0816.r2dbc.schema.diff;

import io.github.hyuk0816.r2dbc.schema.model.ColumnDefinition;
import io.github.hyuk0816.r2dbc.schema.model.ForeignKeyDefinition;
import io.github.hyuk0816.r2dbc.schema.model.IndexDefinition;
import io.github.hyuk0816.r2dbc.schema.model.SchemaDefinition;
import io.github.hyuk0816.r2dbc.schema.model.TableDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SchemaDiffEngine {

    public List<SchemaDiff> diff(SchemaDefinition expected, SchemaDefinition actual) {
        Objects.requireNonNull(expected, "expected must not be null");
        Objects.requireNonNull(actual, "actual must not be null");

        List<SchemaDiff> diffs = new ArrayList<>();
        for (TableDefinition expectedTable : expected.tables()) {
            TableDefinition actualTable = actual.findTable(expectedTable.name()).orElse(null);
            if (actualTable == null) {
                diffs.add(new SchemaDiff(
                        SchemaDiffType.CREATE_TABLE,
                        expectedTable.name(),
                        expectedTable.name(),
                        "Table is missing"
                ));
                continue;
            }
            compareTable(expectedTable, actualTable, diffs);
        }

        for (TableDefinition actualTable : actual.tables()) {
            if (expected.findTable(actualTable.name()).isEmpty()) {
                diffs.add(new SchemaDiff(
                        SchemaDiffType.EXTRA_TABLE,
                        actualTable.name(),
                        actualTable.name(),
                        "Table exists in database but not in entity metadata"
                ));
            }
        }
        return List.copyOf(diffs);
    }

    private static void compareTable(
            TableDefinition expectedTable,
            TableDefinition actualTable,
            List<SchemaDiff> diffs
    ) {
        compareColumns(expectedTable, actualTable, diffs);
        comparePrimaryKey(expectedTable, actualTable, diffs);
        compareIndexes(expectedTable, actualTable, diffs);
        compareForeignKeys(expectedTable, actualTable, diffs);
    }

    private static void compareColumns(
            TableDefinition expectedTable,
            TableDefinition actualTable,
            List<SchemaDiff> diffs
    ) {
        for (ColumnDefinition expectedColumn : expectedTable.columns()) {
            ColumnDefinition actualColumn = actualTable.findColumn(expectedColumn.name()).orElse(null);
            if (actualColumn == null) {
                diffs.add(new SchemaDiff(
                        SchemaDiffType.ADD_COLUMN,
                        expectedTable.name(),
                        expectedColumn.name(),
                        "Column is missing"
                ));
                continue;
            }
            if (!expectedColumn.hasSameType(actualColumn)) {
                diffs.add(new SchemaDiff(
                        SchemaDiffType.MODIFY_COLUMN_TYPE,
                        expectedTable.name(),
                        expectedColumn.name(),
                        "Column type differs"
                ));
            }
            if (expectedColumn.nullable() != actualColumn.nullable()) {
                diffs.add(new SchemaDiff(
                        SchemaDiffType.NULLABILITY_MISMATCH,
                        expectedTable.name(),
                        expectedColumn.name(),
                        "Column nullability differs"
                ));
            }
            if (!Objects.equals(expectedColumn.defaultValue(), actualColumn.defaultValue())) {
                diffs.add(new SchemaDiff(
                        SchemaDiffType.DEFAULT_MISMATCH,
                        expectedTable.name(),
                        expectedColumn.name(),
                        "Column default differs"
                ));
            }
            if (!Objects.equals(expectedColumn.comment(), actualColumn.comment())) {
                diffs.add(new SchemaDiff(
                        SchemaDiffType.COMMENT_MISMATCH,
                        expectedTable.name(),
                        expectedColumn.name(),
                        "Column comment differs"
                ));
            }
        }

        for (ColumnDefinition actualColumn : actualTable.columns()) {
            if (expectedTable.findColumn(actualColumn.name()).isEmpty()) {
                diffs.add(new SchemaDiff(
                        SchemaDiffType.EXTRA_COLUMN,
                        expectedTable.name(),
                        actualColumn.name(),
                        "Column exists in database but not in entity metadata"
                ));
            }
        }
    }

    private static void comparePrimaryKey(
            TableDefinition expectedTable,
            TableDefinition actualTable,
            List<SchemaDiff> diffs
    ) {
        if (!sameNames(expectedTable.primaryKeyColumns(), actualTable.primaryKeyColumns())) {
            diffs.add(new SchemaDiff(
                    SchemaDiffType.PRIMARY_KEY_MISMATCH,
                    expectedTable.name(),
                    "PRIMARY",
                    "Primary key differs"
            ));
        }
    }

    private static void compareIndexes(
            TableDefinition expectedTable,
            TableDefinition actualTable,
            List<SchemaDiff> diffs
    ) {
        for (IndexDefinition expectedIndex : expectedTable.indexes()) {
            IndexDefinition actualIndex = actualTable.findIndex(expectedIndex.name()).orElse(null);
            if (actualIndex == null) {
                diffs.add(new SchemaDiff(
                        expectedIndex.unique() ? SchemaDiffType.ADD_UNIQUE_KEY : SchemaDiffType.ADD_INDEX,
                        expectedTable.name(),
                        expectedIndex.name(),
                        "Index is missing"
                ));
                continue;
            }
            boolean columnsDiffer = !sameNames(expectedIndex.columns(), actualIndex.columns());
            if (expectedIndex.unique() != actualIndex.unique() || columnsDiffer) {
                diffs.add(new SchemaDiff(
                        expectedIndex.unique() ? SchemaDiffType.UNIQUE_KEY_MISMATCH : SchemaDiffType.INDEX_MISMATCH,
                        expectedTable.name(),
                        expectedIndex.name(),
                        "Index definition differs"
                ));
            }
        }
    }

    private static void compareForeignKeys(
            TableDefinition expectedTable,
            TableDefinition actualTable,
            List<SchemaDiff> diffs
    ) {
        for (ForeignKeyDefinition expectedForeignKey : expectedTable.foreignKeys()) {
            ForeignKeyDefinition actualForeignKey = actualTable.findForeignKey(expectedForeignKey.name()).orElse(null);
            if (actualForeignKey == null) {
                diffs.add(new SchemaDiff(
                        SchemaDiffType.ADD_FOREIGN_KEY,
                        expectedTable.name(),
                        expectedForeignKey.name(),
                        "Foreign key is missing"
                ));
                continue;
            }
            if (!sameForeignKey(expectedForeignKey, actualForeignKey)) {
                diffs.add(new SchemaDiff(
                        SchemaDiffType.FOREIGN_KEY_MISMATCH,
                        expectedTable.name(),
                        expectedForeignKey.name(),
                        "Foreign key definition differs"
                ));
            }
        }
    }

    private static boolean sameForeignKey(ForeignKeyDefinition left, ForeignKeyDefinition right) {
        return sameNames(left.columns(), right.columns())
                && left.referencedTable().equalsIgnoreCase(right.referencedTable())
                && sameNames(left.referencedColumns(), right.referencedColumns());
    }

    private static boolean sameNames(List<String> left, List<String> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).equalsIgnoreCase(right.get(i))) {
                return false;
            }
        }
        return true;
    }
}
