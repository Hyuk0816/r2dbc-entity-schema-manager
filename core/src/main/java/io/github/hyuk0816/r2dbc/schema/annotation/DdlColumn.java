package io.github.hyuk0816.r2dbc.schema.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DdlColumn {

    String name() default "";

    String type() default "";

    int length() default -1;

    int precision() default -1;

    int scale() default -1;

    boolean nullable() default true;

    String defaultValue() default "";

    String comment() default "";
}
