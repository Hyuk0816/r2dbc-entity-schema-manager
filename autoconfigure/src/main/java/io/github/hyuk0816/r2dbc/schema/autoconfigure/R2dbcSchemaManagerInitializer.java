package io.github.hyuk0816.r2dbc.schema.autoconfigure;

import org.springframework.beans.factory.SmartInitializingSingleton;

public final class R2dbcSchemaManagerInitializer implements SmartInitializingSingleton {

    private final R2dbcSchemaManager manager;
    private final R2dbcSchemaManagerProperties properties;

    public R2dbcSchemaManagerInitializer(
            R2dbcSchemaManager manager,
            R2dbcSchemaManagerProperties properties
    ) {
        this.manager = manager;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (properties.isEnabled()) {
            manager.synchronize();
        }
    }
}
