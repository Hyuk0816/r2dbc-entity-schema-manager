package io.github.hyuk0816.r2dbc.schema.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
@Repeatable(DdlUniques.class)
public @interface DdlUnique {

    String name() default "";

    String[] columns() default {};
}
