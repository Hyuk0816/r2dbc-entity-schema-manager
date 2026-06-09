# R2DBC Entity Schema Manager Usage Guide

## What This Library Does

`R2DBC Entity Schema Manager` is a Spring Boot starter for Spring Data R2DBC applications.
It compares Spring Data entity metadata with the real MariaDB schema, then reports or applies
safe DDL changes according to configuration.

This project is not an ORM and it is not Hibernate for R2DBC. It does not provide persistence
context, dirty checking, lazy loading, JPQL, or migration history. It only focuses on schema
management at application startup.

## Supported Stack

- Java 17
- Spring Boot 3.x
- Spring Data R2DBC
- MariaDB
- Gradle Kotlin DSL examples

## Installation

For local development, publish the library first:

```bash
./gradlew clean test publishToMavenLocal
```

Then add the starter to your application:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.hyuk0816:r2dbc-entity-schema-manager-spring-boot-starter:0.1.0-SNAPSHOT")
    runtimeOnly("org.mariadb:r2dbc-mariadb")
}
```

If you consume from GitHub Packages, add the GitHub Maven repository:

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/Hyuk0816/r2dbc-entity-schema-manager")
        credentials {
            username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

## Basic Configuration

The manager is disabled by default. Enable it explicitly:

```yaml
r2dbc-schema-manager:
  enabled: true
  mode: dry-run
  dialect: mariadb
  apply-foreign-keys: false
```

Modes:

| Mode | Behavior |
|------|----------|
| `dry-run` | Logs generated SQL and diff information without executing DDL. |
| `validate` | Fails application startup when schema diff exists. |
| `apply` | Executes applyable DDL in dependency-safe order. |

Recommended rollout:

1. Start with `dry-run`.
2. Review generated SQL in logs.
3. Run against a disposable local DB or Testcontainers.
4. Enable `apply`.
5. Enable `apply-foreign-keys=true` only after table, column, index, and unique key behavior is verified.

## DDL Apply Order

DDL is not executed in discovery order. It is sorted as:

```text
1. CREATE TABLE
2. ADD COLUMN
3. MODIFY COLUMN TYPE
4. ADD INDEX
5. ADD UNIQUE INDEX
6. ADD FOREIGN KEY
```

Foreign keys are last because referenced tables and columns must exist first.

## Column Definition

Spring Data annotations such as `@Table`, `@Column`, and `@Id` are read from the existing
mapping metadata. DDL-specific details are expressed with this library's annotations.

```java
@Table("user_master")
public class UserMaster {

    @Id
    private Long id;

    @DdlColumn(
            type = "varchar",
            length = 150,
            nullable = false,
            defaultValue = "'anonymous@example.com'",
            comment = "User email"
    )
    private String email;
}
```

When `@DdlColumn` is omitted, the library falls back to basic Java-to-MariaDB type inference.
Use `@DdlColumn` for business-critical columns where length, precision, nullability, default,
or comment matters.

## Single-Column Index

Use `@DdlIndex` on a field:

```java
@Table("user_master")
public class UserMaster {

    @Id
    private Long id;

    @DdlIndex(name = "idx_user_master_email")
    private String email;
}
```

Generated SQL:

```sql
CREATE INDEX `idx_user_master_email` ON `user_master` (`email`);
```

## Composite Index

Use `@DdlIndex` on the class:

```java
@Table("user_master")
@DdlIndex(
        name = "idx_user_master_tenant_status",
        columns = {"tenant_id", "status"}
)
public class UserMaster {

    @Id
    private Long id;

    private Long tenantId;

    private String status;
}
```

Generated SQL:

```sql
CREATE INDEX `idx_user_master_tenant_status`
ON `user_master` (`tenant_id`, `status`);
```

Column order matters. If the DB has the same index name but a different column order, it is
reported as `INDEX_MISMATCH`; the current version does not drop and recreate indexes automatically.

## Single-Column Unique Key

Use `@DdlUnique` on a field:

```java
@Table("user_master")
public class UserMaster {

    @Id
    private Long id;

    @DdlUnique(name = "uk_user_master_email")
    private String email;
}
```

Generated SQL:

```sql
CREATE UNIQUE INDEX `uk_user_master_email` ON `user_master` (`email`);
```

## Composite Unique Key

Use `@DdlUnique` on the class:

```java
@Table("user_master")
@DdlUnique(
        name = "uk_user_master_tenant_email",
        columns = {"tenant_id", "email"}
)
public class UserMaster {

    @Id
    private Long id;

    private Long tenantId;

    private String email;
}
```

Generated SQL:

```sql
CREATE UNIQUE INDEX `uk_user_master_tenant_email`
ON `user_master` (`tenant_id`, `email`);
```

If existing data violates the unique key, MariaDB will reject the DDL and application startup
will fail in `apply` mode.

## Foreign Key

Foreign keys are supported by this library, not by R2DBC itself.

Current support is field-level single-column foreign keys:

```java
@Table("order_master")
public class OrderMaster {

    @Id
    private Long id;

    @DdlForeignKey(
            name = "fk_order_master_user_id",
            referencedTable = "user_master",
            referencedColumn = "id"
    )
    private Long userId;
}
```

Generated SQL:

```sql
ALTER TABLE `order_master`
    ADD CONSTRAINT `fk_order_master_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `user_master` (`id`);
```

Foreign key application is disabled by default:

```yaml
r2dbc-schema-manager:
  apply-foreign-keys: false
```

Enable it explicitly:

```yaml
r2dbc-schema-manager:
  mode: apply
  apply-foreign-keys: true
```

Current limitations:

- Composite foreign keys are not supported yet.
- `ON DELETE` and `ON UPDATE` actions are not supported yet.
- Existing invalid data can make FK creation fail.

## Schema Evolution Scenarios

### Field Added

Entity:

```java
@DdlColumn(type = "varchar", length = 100, nullable = true)
private String nickname;
```

When DB column is missing:

```sql
ALTER TABLE `user_master` ADD COLUMN `nickname` varchar(100);
```

Important policy:

- Nullable column: applyable.
- Non-null column with default: applyable.
- Non-null column without default on an existing table: report-only, because existing rows may violate the new constraint.

### Field Removed

If the DB has `nickname` but the entity no longer has it:

```text
EXTRA_COLUMN
```

The library does not drop the column. This avoids accidental data loss.

### Field Renamed

If `name` becomes `userName`, the library cannot know if this is a rename or a new field.
It reports the old column and treats the new column as missing:

```text
EXTRA_COLUMN: name
ADD_COLUMN: user_name
```

It does not perform automatic rename.

### Field Type Changed

If the entity expects `varchar(200)` but DB has `varchar(100)`:

```text
MODIFY_COLUMN_TYPE
```

This is applyable only when:

```yaml
r2dbc-schema-manager:
  sync-existing-column-types: true
```

Generated SQL:

```sql
ALTER TABLE `user_master` MODIFY COLUMN `email` varchar(200);
```

Type changes can lock a table or fail depending on data state.

### Nullability, Default, Comment Changed

The current version detects these changes but reports them only:

```text
NULLABILITY_MISMATCH
DEFAULT_MISMATCH
COMMENT_MISMATCH
```

It does not automatically apply those changes yet.

### Index Added

Missing index:

```text
ADD_INDEX
```

Default behavior is applyable:

```yaml
r2dbc-schema-manager:
  apply-indexes: true
```

### Unique Key Added

Missing unique key:

```text
ADD_UNIQUE_KEY
```

Default behavior is applyable:

```yaml
r2dbc-schema-manager:
  apply-unique-indexes: true
```

### Index or Unique Definition Changed

If the same index name exists but columns or uniqueness differ:

```text
INDEX_MISMATCH
UNIQUE_KEY_MISMATCH
```

The current version reports only. It does not drop and recreate existing indexes automatically.

### Foreign Key Added

Missing FK:

```text
ADD_FOREIGN_KEY
```

Applyable only when:

```yaml
r2dbc-schema-manager:
  apply-foreign-keys: true
```

### Foreign Key Changed

If the FK name exists but columns or referenced target differ:

```text
FOREIGN_KEY_MISMATCH
```

The current version reports only. It does not drop and recreate FKs automatically.

## Testing in a Real Project

The safest real-project flow is:

1. Publish this library locally.
2. Add the starter to the target project.
3. Add a Testcontainers MariaDB integration test in the target project.
4. Run with `mode=apply` against the container.
5. Verify `information_schema`.
6. Run `dry-run` against a real development DB.
7. Review SQL before enabling `apply`.

Example test properties:

```yaml
r2dbc-schema-manager:
  enabled: true
  mode: apply
  apply-foreign-keys: true
```

Never enable `apply` first against a shared development or production DB.

## GitHub Packages Publishing

This repository includes a GitHub Actions workflow that can publish artifacts to GitHub Packages.
That is a Maven-compatible repository, but it is not Maven Central.

Consumers use:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/Hyuk0816/r2dbc-entity-schema-manager")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}
```

Publishing to Maven Central requires a separate Sonatype Central Portal setup, artifact signing,
namespace verification, and release credentials.
