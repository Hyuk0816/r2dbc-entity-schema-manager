package io.github.hyuk0816.r2dbc.schema.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class R2dbcSchemaManagerPropertiesTest {

    @Test
    void doesNotSyncExistingColumnTypesByDefault() {
        R2dbcSchemaManagerProperties properties = new R2dbcSchemaManagerProperties();

        assertThat(properties.isSyncExistingColumnTypes()).isFalse();
        assertThat(properties.toApplyOptions().syncExistingColumnTypes()).isFalse();
    }
}
