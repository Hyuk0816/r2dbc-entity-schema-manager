package io.github.hyuk0816.r2dbc.schema.type;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public final class JavaToMariaDbTypeMapper {

    public String map(Class<?> javaType) {
        Objects.requireNonNull(javaType, "javaType must not be null");
        if (javaType == String.class) {
            return "varchar(255)";
        }
        if (javaType == Long.class || javaType == long.class) {
            return "bigint";
        }
        if (javaType == Integer.class || javaType == int.class) {
            return "int";
        }
        if (javaType == Boolean.class || javaType == boolean.class) {
            return "tinyint(1)";
        }
        if (javaType == LocalDate.class) {
            return "date";
        }
        if (javaType == LocalDateTime.class) {
            return "datetime";
        }
        if (javaType == BigDecimal.class) {
            return "decimal(19,2)";
        }
        if (javaType == byte[].class) {
            return "blob";
        }
        if (javaType.isEnum()) {
            return "varchar(50)";
        }
        return "varchar(255)";
    }
}
