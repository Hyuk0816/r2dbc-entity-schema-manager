package io.github.hyuk0816.r2dbc.schema.autoconfigure;

import io.github.hyuk0816.r2dbc.schema.model.TableDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.Table;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EntitySchemaScannerTest {

    @Test
    void ignoresMappingContextTypesThatAreNotAnnotatedAsTables() {
        RelationalMappingContext mappingContext = new RelationalMappingContext();
        mappingContext.setInitialEntitySet(Set.of(AccountTable.class, PkiCertValueDto.class));
        mappingContext.afterPropertiesSet();
        EntitySchemaScanner scanner = new EntitySchemaScanner(mappingContext, new R2dbcSchemaManagerProperties());

        assertThat(scanner.scan().tables())
                .extracting(TableDefinition::name)
                .containsExactly("account_table");
    }

    @Table("account_table")
    static final class AccountTable {

        @Id
        Long id;

        String name;
    }

    static final class PkiCertValueDto {

        String key;

        String value;
    }
}
