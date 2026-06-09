package io.github.hyuk0816.r2dbc.schema.autoconfigure;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.r2dbc.R2dbcRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.r2dbc.core.DatabaseClient;

@AutoConfiguration(after = {
        R2dbcAutoConfiguration.class,
        R2dbcDataAutoConfiguration.class,
        R2dbcRepositoriesAutoConfiguration.class
})
@ConditionalOnClass({ConnectionFactory.class, DatabaseClient.class, RelationalMappingContext.class})
@ConditionalOnBean({ConnectionFactory.class, RelationalMappingContext.class})
@ConditionalOnProperty(prefix = "r2dbc-schema-manager", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(R2dbcSchemaManagerProperties.class)
public class R2dbcSchemaManagerAutoConfiguration {

    @Bean
    R2dbcSchemaManagerInitializer r2dbcSchemaManagerInitializer(
            ConnectionFactory connectionFactory,
            RelationalMappingContext mappingContext,
            R2dbcSchemaManagerProperties properties,
            ListableBeanFactory beanFactory
    ) {
        DatabaseClient databaseClient = DatabaseClient.create(connectionFactory);
        EntitySchemaScanner scanner = new EntitySchemaScanner(mappingContext, properties, beanFactory);
        DatabaseSchemaReader reader = new DatabaseSchemaReader(databaseClient, properties.getExecutionTimeout());
        DdlExecutor executor = new DdlExecutor(databaseClient, properties.getExecutionTimeout());
        R2dbcSchemaManager manager = new R2dbcSchemaManager(scanner, reader, executor, properties);
        return new R2dbcSchemaManagerInitializer(manager, properties);
    }
}
