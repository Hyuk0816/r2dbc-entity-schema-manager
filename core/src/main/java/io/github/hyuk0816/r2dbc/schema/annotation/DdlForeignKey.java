package io.github.hyuk0816.r2dbc.schema.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DdlForeignKey {

    String name() default "";

    String referencedTable();

    String referencedColumn();
}
