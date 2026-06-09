package io.github.hyuk0816.r2dbc.schema.naming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameCaseConverterTest {

    private final NameCaseConverter converter = new NameCaseConverter();

    @Test
    void convertsJavaFieldNameToConfiguredColumnName() {
        assertThat(converter.convert("userName", NameCase.SNAKE_CASE)).isEqualTo("user_name");
        assertThat(converter.convert("userName", NameCase.LOWER_CAMEL)).isEqualTo("userName");
        assertThat(converter.convert("userName", NameCase.UPPER_CAMEL)).isEqualTo("UserName");
        assertThat(converter.convert("userName", NameCase.LOWER)).isEqualTo("username");
        assertThat(converter.convert("userName", NameCase.UPPER)).isEqualTo("USERNAME");
        assertThat(converter.convert("userName", NameCase.AS_IS)).isEqualTo("userName");
    }
}
