package io.github.hyuk0816.r2dbc.schema.type;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JavaToMariaDbTypeMapperTest {

    private final JavaToMariaDbTypeMapper mapper = new JavaToMariaDbTypeMapper();

    @Test
    void mapsCommonJavaTypesToMariaDbTypes() {
        assertThat(mapper.map(String.class)).isEqualTo("varchar(255)");
        assertThat(mapper.map(Long.class)).isEqualTo("bigint");
        assertThat(mapper.map(long.class)).isEqualTo("bigint");
        assertThat(mapper.map(Integer.class)).isEqualTo("int");
        assertThat(mapper.map(int.class)).isEqualTo("int");
        assertThat(mapper.map(Boolean.class)).isEqualTo("tinyint(1)");
        assertThat(mapper.map(boolean.class)).isEqualTo("tinyint(1)");
        assertThat(mapper.map(LocalDate.class)).isEqualTo("date");
        assertThat(mapper.map(LocalDateTime.class)).isEqualTo("datetime");
        assertThat(mapper.map(BigDecimal.class)).isEqualTo("decimal(19,2)");
        assertThat(mapper.map(byte[].class)).isEqualTo("blob");
        assertThat(mapper.map(SampleStatus.class)).isEqualTo("varchar(50)");
    }

    private enum SampleStatus {
        ACTIVE
    }
}
