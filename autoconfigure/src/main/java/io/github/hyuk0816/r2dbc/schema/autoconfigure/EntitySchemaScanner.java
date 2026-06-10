package io.github.hyuk0816.r2dbc.schema.autoconfigure;

import io.github.hyuk0816.r2dbc.schema.annotation.DdlColumn;
import io.github.hyuk0816.r2dbc.schema.annotation.DdlForeignKey;
import io.github.hyuk0816.r2dbc.schema.annotation.DdlIndex;
import io.github.hyuk0816.r2dbc.schema.annotation.DdlUnique;
import io.github.hyuk0816.r2dbc.schema.model.ColumnDefinition;
import io.github.hyuk0816.r2dbc.schema.model.ForeignKeyDefinition;
import io.github.hyuk0816.r2dbc.schema.model.IndexDefinition;
import io.github.hyuk0816.r2dbc.schema.model.SchemaDefinition;
import io.github.hyuk0816.r2dbc.schema.model.TableDefinition;
import io.github.hyuk0816.r2dbc.schema.naming.NameCaseConverter;
import io.github.hyuk0816.r2dbc.schema.type.JavaToMariaDbTypeMapper;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.repository.support.Repositories;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EntitySchemaScanner {

    private final RelationalMappingContext mappingContext;
    private final R2dbcSchemaManagerProperties properties;
    private final ListableBeanFactory beanFactory;
    private final JavaToMariaDbTypeMapper typeMapper = new JavaToMariaDbTypeMapper();
    private final NameCaseConverter nameCaseConverter = new NameCaseConverter();

    public EntitySchemaScanner(
            RelationalMappingContext mappingContext,
            R2dbcSchemaManagerProperties properties
    ) {
        this(mappingContext, properties, null);
    }

    public EntitySchemaScanner(
            RelationalMappingContext mappingContext,
            R2dbcSchemaManagerProperties properties,
            ListableBeanFactory beanFactory
    ) {
        this.mappingContext = mappingContext;
        this.properties = properties;
        this.beanFactory = beanFactory;
    }

    public SchemaDefinition scan() {
        List<TableDefinition> tables = new ArrayList<>();
        for (RelationalPersistentEntity<?> entity : persistentEntities()) {
            tables.add(scanEntity(entity));
        }
        return new SchemaDefinition(tables);
    }

    private List<RelationalPersistentEntity<?>> persistentEntities() {
        Map<Class<?>, RelationalPersistentEntity<?>> entities = new LinkedHashMap<>();
        Set<Class<?>> repositoryDomainTypes = repositoryDomainTypes();
        for (Class<?> domainType : repositoryDomainTypes) {
            mappingContext.getPersistentEntity(domainType);
        }
        for (RelationalPersistentEntity<?> entity : mappingContext.getPersistentEntities()) {
            if (isSchemaManagedType(entity.getType())) {
                entities.put(entity.getType(), entity);
            }
        }
        return List.copyOf(entities.values());
    }

    private Set<Class<?>> repositoryDomainTypes() {
        Set<Class<?>> domainTypes = new LinkedHashSet<>();
        if (beanFactory != null) {
            Repositories repositories = new Repositories(beanFactory);
            for (Class<?> domainType : repositories) {
                domainTypes.add(domainType);
            }
        }
        return domainTypes;
    }

    private static boolean isSchemaManagedType(Class<?> type) {
        return hasAnnotation(type, "org.springframework.data.relational.core.mapping.Table")
                || hasAnnotation(type, "jakarta.persistence.Table")
                || hasAnnotation(type, "javax.persistence.Table");
    }

    private static boolean hasAnnotation(Class<?> type, String annotationName) {
        for (java.lang.annotation.Annotation annotation : type.getDeclaredAnnotations()) {
            if (annotation.annotationType().getName().equals(annotationName)) {
                return true;
            }
        }
        return false;
    }

    private TableDefinition scanEntity(RelationalPersistentEntity<?> entity) {
        String tableName = entity.getTableName().getReference();
        List<ColumnDefinition> columns = new ArrayList<>();
        List<String> primaryKeyColumns = new ArrayList<>();
        List<IndexDefinition> indexes = new ArrayList<>();
        List<ForeignKeyDefinition> foreignKeys = new ArrayList<>();

        for (RelationalPersistentProperty property : entity) {
            if (property.isTransient() || !property.isReadable()) {
                continue;
            }
            String columnName = resolveColumnName(property);
            DdlColumn ddlColumn = property.findAnnotation(DdlColumn.class);
            boolean primaryKey = property.isIdProperty();
            columns.add(new ColumnDefinition(
                    columnName,
                    resolveColumnType(property, ddlColumn),
                    primaryKey ? false : resolveNullable(property, ddlColumn),
                    ddlColumn == null ? null : ddlColumn.defaultValue(),
                    ddlColumn == null ? null : ddlColumn.comment()
            ));

            if (primaryKey) {
                primaryKeyColumns.add(columnName);
            }

            addFieldIndexes(tableName, property, columnName, indexes);
            addFieldUniqueIndexes(tableName, property, columnName, indexes);
            addForeignKey(tableName, property, columnName, foreignKeys);
        }

        addTypeIndexes(tableName, entity.getType(), indexes);
        addTypeUniqueIndexes(tableName, entity.getType(), indexes);

        return new TableDefinition(tableName, columns, primaryKeyColumns, indexes, foreignKeys);
    }

    private String resolveColumnName(RelationalPersistentProperty property) {
        DdlColumn ddlColumn = property.findAnnotation(DdlColumn.class);
        if (property.hasExplicitColumnName()) {
            return property.getColumnName().getReference();
        }
        if (ddlColumn != null && !ddlColumn.name().isBlank()) {
            return ddlColumn.name();
        }
        if (properties.getNameCase() == R2dbcSchemaManagerProperties.NameCaseOption.SPRING) {
            return property.getColumnName().getReference();
        }
        return nameCaseConverter.convert(property.getName(), properties.getNameCase().toCoreNameCase());
    }

    private String resolveColumnType(RelationalPersistentProperty property, DdlColumn ddlColumn) {
        String type = ddlColumn == null || ddlColumn.type().isBlank()
                ? typeMapper.map(property.getActualType())
                : ddlColumn.type();
        if (ddlColumn == null) {
            return type;
        }
        if (ddlColumn.length() > -1) {
            return typeWithoutArguments(type) + "(" + ddlColumn.length() + ")";
        }
        if (ddlColumn.precision() > -1 && ddlColumn.scale() > -1) {
            return typeWithoutArguments(type) + "(" + ddlColumn.precision() + "," + ddlColumn.scale() + ")";
        }
        return type;
    }

    private static String typeWithoutArguments(String type) {
        int argumentsStart = type.indexOf('(');
        if (argumentsStart == -1) {
            return type;
        }
        return type.substring(0, argumentsStart);
    }

    private boolean resolveNullable(RelationalPersistentProperty property, DdlColumn ddlColumn) {
        if (ddlColumn != null) {
            return ddlColumn.nullable();
        }
        return !property.getActualType().isPrimitive();
    }

    private static void addFieldIndexes(
            String tableName,
            RelationalPersistentProperty property,
            String columnName,
            List<IndexDefinition> indexes
    ) {
        Field field = property.getField();
        if (field == null) {
            return;
        }
        for (DdlIndex annotation : field.getAnnotationsByType(DdlIndex.class)) {
            List<String> columns = annotation.columns().length == 0 ? List.of(columnName) : List.of(annotation.columns());
            indexes.add(new IndexDefinition(defaultName(annotation.name(), "idx", tableName, columns), columns, false));
        }
    }

    private static void addFieldUniqueIndexes(
            String tableName,
            RelationalPersistentProperty property,
            String columnName,
            List<IndexDefinition> indexes
    ) {
        Field field = property.getField();
        if (field == null) {
            return;
        }
        for (DdlUnique annotation : field.getAnnotationsByType(DdlUnique.class)) {
            List<String> columns = annotation.columns().length == 0 ? List.of(columnName) : List.of(annotation.columns());
            indexes.add(new IndexDefinition(defaultName(annotation.name(), "uk", tableName, columns), columns, true));
        }
    }

    private static void addForeignKey(
            String tableName,
            RelationalPersistentProperty property,
            String columnName,
            List<ForeignKeyDefinition> foreignKeys
    ) {
        DdlForeignKey annotation = property.findAnnotation(DdlForeignKey.class);
        if (annotation == null) {
            return;
        }
        List<String> columns = List.of(columnName);
        foreignKeys.add(new ForeignKeyDefinition(
                defaultName(annotation.name(), "fk", tableName, columns),
                columns,
                annotation.referencedTable(),
                List.of(annotation.referencedColumn())
        ));
    }

    private static void addTypeIndexes(String tableName, Class<?> type, List<IndexDefinition> indexes) {
        for (DdlIndex annotation : type.getAnnotationsByType(DdlIndex.class)) {
            List<String> columns = List.of(annotation.columns());
            indexes.add(new IndexDefinition(defaultName(annotation.name(), "idx", tableName, columns), columns, false));
        }
    }

    private static void addTypeUniqueIndexes(String tableName, Class<?> type, List<IndexDefinition> indexes) {
        for (DdlUnique annotation : type.getAnnotationsByType(DdlUnique.class)) {
            List<String> columns = List.of(annotation.columns());
            indexes.add(new IndexDefinition(defaultName(annotation.name(), "uk", tableName, columns), columns, true));
        }
    }

    private static String defaultName(String configuredName, String prefix, String tableName, List<String> columns) {
        if (configuredName != null && !configuredName.isBlank()) {
            return configuredName;
        }
        return prefix + "_" + tableName + "_" + String.join("_", columns);
    }
}
