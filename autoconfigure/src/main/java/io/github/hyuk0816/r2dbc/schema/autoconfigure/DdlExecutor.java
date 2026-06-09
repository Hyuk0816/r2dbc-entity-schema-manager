package io.github.hyuk0816.r2dbc.schema.autoconfigure;

import io.github.hyuk0816.r2dbc.schema.ddl.DdlStatement;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Duration;
import java.util.List;

public final class DdlExecutor {

    private final DatabaseClient databaseClient;
    private final Duration timeout;

    public DdlExecutor(DatabaseClient databaseClient, Duration timeout) {
        this.databaseClient = databaseClient;
        this.timeout = timeout;
    }

    public void execute(List<DdlStatement> statements) {
        for (DdlStatement statement : statements) {
            databaseClient.sql(statement.sql())
                    .fetch()
                    .rowsUpdated()
                    .block(timeout);
        }
    }
}
