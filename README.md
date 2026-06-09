# R2DBC Entity Schema Manager

Entity driven schema manager for Spring Boot and Spring Data R2DBC applications.

This project is not affiliated with the R2DBC project or the Spring project.

## Modules

- `r2dbc-entity-schema-manager-core`: schema model, diff engine, policy, type mapping, and MariaDB DDL generation.
- `r2dbc-entity-schema-manager-autoconfigure`: Spring Boot auto-configuration.
- `r2dbc-entity-schema-manager-spring-boot-starter`: starter dependency for applications.

## Local Usage

Publish locally:

```bash
./gradlew publishToMavenLocal
```

Use from another Spring Boot app:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.hyuk0816:r2dbc-entity-schema-manager-spring-boot-starter:0.1.0-SNAPSHOT")
}
```

Enable explicitly:

```yaml
r2dbc-schema-manager:
  enabled: true
  mode: dry-run # dry-run | validate | apply
  dialect: mariadb
  apply-foreign-keys: true
```

The library reuses the host application's existing `ConnectionFactory` and `RelationalMappingContext`.

## Documentation

- [Detailed usage guide](docs/USAGE.md)
- [MariaDB smoke sample](samples/mariadb-smoke-app/README.md)

## Verification

Run the library tests:

```bash
./gradlew test
```

`autoconfigure` includes Testcontainers-based MariaDB integration tests, so Docker must be running.

Verify the published starter from a separate sample application:

```bash
./gradlew publishToMavenLocal
./gradlew -p samples/mariadb-smoke-app test
```

The sample consumes `io.github.hyuk0816:r2dbc-entity-schema-manager-spring-boot-starter:0.1.0-SNAPSHOT`
from Maven Local and starts a real MariaDB Testcontainer.
