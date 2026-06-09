# MariaDB Smoke App

This sample verifies the published starter artifact through Maven Local.

From the repository root:

```bash
./gradlew publishToMavenLocal
./gradlew -p samples/mariadb-smoke-app test
```

The test starts a MariaDB Testcontainer, boots a Spring application, enables
`r2dbc-schema-manager`, and verifies that tables, indexes, unique keys, and
foreign keys are created in the real database.
