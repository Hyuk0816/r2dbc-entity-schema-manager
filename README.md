# R2DBC Entity Schema Manager

> English documentation: [docs/USAGE.md](docs/USAGE.md)

Spring Boot + Spring Data R2DBC 프로젝트에서 엔티티 metadata와 실제 MariaDB schema를 비교하고, 설정에 따라 DDL을 report 또는 apply하는 schema manager starter입니다.

이 프로젝트는 R2DBC 또는 Spring 공식 프로젝트가 아닙니다.

## 현재 배포 상태

아직 Maven Central에는 배포되어 있지 않습니다.

따라서 지금은 아래처럼 `mavenCentral()`만 두고 바로 받는 방식은 사용할 수 없습니다.

```kotlin
dependencies {
    implementation("io.github.hyuk0816:r2dbc-entity-schema-manager-spring-boot-starter:0.1.0-SNAPSHOT")
}
```

위 좌표를 실제 프로젝트에서 쓰려면 다음 중 하나가 필요합니다.

1. 로컬 개발: `publishToMavenLocal` 후 `mavenLocal()` 사용
2. Maven Central 배포: Sonatype Central Portal 설정, signing, namespace 검증 후 배포

`org.mariadb:r2dbc-mariadb:1.4.0` 같은 의존성은 Maven Central에 이미 올라와 있기 때문에 `mavenCentral()`만으로 받을 수 있습니다. 반면 이 라이브러리는 아직 Maven Central에 없으므로 같은 방식으로는 받을 수 없습니다.

## 모듈 구성

```text
r2dbc-entity-schema-manager
├── core
├── autoconfigure
└── starter
```

- `core`: schema model, diff engine, 정책 판정, MariaDB DDL 생성
- `autoconfigure`: Spring Boot auto-configuration, entity scan, `information_schema` 조회, DDL 실행
- `starter`: 실제 애플리케이션에서 추가하는 starter artifact

## 지원 범위

현재 1차 범위는 MariaDB 기준입니다.

- Spring Data R2DBC entity scan
- `@Table`, `@Column`, `@Id` metadata 사용
- 신규 테이블 생성
- 신규 컬럼 추가
- 기존 컬럼 타입 차이 감지 및 옵션 기반 변경
- index 생성
- unique index 생성
- foreign key 생성
- `dry-run`, `validate`, `apply` mode
- 삭제/rename 자동 적용 금지

지원하지 않는 것:

- R2DBC용 ORM 전체 구현
- 영속성 컨텍스트
- dirty checking
- lazy loading
- JPQL
- migration 이력 관리
- 컬럼 삭제 자동 적용
- 컬럼 rename 자동 적용
- 복합 foreign key
- FK `ON DELETE`, `ON UPDATE` 옵션

## 로컬에서 사용하기

먼저 이 라이브러리 프로젝트에서 Maven Local에 배포합니다.

```bash
./gradlew clean test publishToMavenLocal
```

그 다음 실제 Spring Boot 프로젝트의 `build.gradle.kts`에 추가합니다.

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.github.hyuk0816:r2dbc-entity-schema-manager-spring-boot-starter:0.1.0-SNAPSHOT")
    runtimeOnly("org.mariadb:r2dbc-mariadb:1.4.0")
}
```

`runtimeOnly("org.mariadb:r2dbc-mariadb:1.4.0")`는 MariaDB R2DBC driver입니다. 이 라이브러리와 별개로 애플리케이션이 MariaDB에 R2DBC로 연결하려면 필요합니다.

## 기본 설정

기본값은 비활성화입니다. 반드시 명시적으로 켜야 합니다.

```yaml
r2dbc-schema-manager:
  enabled: true
  mode: dry-run
  dialect: mariadb
  apply-foreign-keys: false
```

mode 의미:

| mode | 동작 |
|------|------|
| `dry-run` | SQL을 로그로 출력하고 실행하지 않습니다. |
| `validate` | schema diff가 있으면 애플리케이션 기동을 실패시킵니다. |
| `apply` | 적용 가능한 DDL을 실제 DB에 실행합니다. |

처음부터 `apply`를 켜지 말고, 먼저 `dry-run`으로 생성될 SQL을 확인하는 것을 권장합니다.

## DDL 적용 순서

`apply` mode에서는 DDL을 아래 순서로 정렬한 뒤 실행합니다.

```text
1. CREATE TABLE
2. ADD COLUMN
3. MODIFY COLUMN TYPE
4. ADD INDEX
5. ADD UNIQUE INDEX
6. ADD FOREIGN KEY
```

foreign key는 참조 대상 테이블과 컬럼이 먼저 있어야 하므로 마지막에 실행합니다.

## 컬럼 설정

Spring Data R2DBC의 기본 mapping annotation과 함께 사용합니다.

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
            comment = "사용자 이메일"
    )
    private String email;
}
```

`@DdlColumn`이 없으면 기본 Java 타입 기준으로 MariaDB 타입을 추론합니다. 업무적으로 길이, precision, nullable, default, comment가 중요한 컬럼은 명시하는 편이 안전합니다.

## 단일 index

field에 `@DdlIndex`를 붙입니다.

```java
@Table("user_master")
public class UserMaster {

    @Id
    private Long id;

    @DdlIndex(name = "idx_user_master_email")
    private String email;
}
```

생성 예시:

```sql
CREATE INDEX `idx_user_master_email` ON `user_master` (`email`);
```

## 복합 index

class에 `@DdlIndex`를 붙이고 `columns`를 지정합니다.

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

생성 예시:

```sql
CREATE INDEX `idx_user_master_tenant_status`
ON `user_master` (`tenant_id`, `status`);
```

컬럼 순서도 schema 비교 대상입니다. 같은 index 이름이 있어도 컬럼 순서가 다르면 `INDEX_MISMATCH`로 report합니다.

## 단일 unique index

field에 `@DdlUnique`를 붙입니다.

```java
@Table("user_master")
public class UserMaster {

    @Id
    private Long id;

    @DdlUnique(name = "uk_user_master_email")
    private String email;
}
```

생성 예시:

```sql
CREATE UNIQUE INDEX `uk_user_master_email` ON `user_master` (`email`);
```

## 복합 unique index

class에 `@DdlUnique`를 붙이고 `columns`를 지정합니다.

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

생성 예시:

```sql
CREATE UNIQUE INDEX `uk_user_master_tenant_email`
ON `user_master` (`tenant_id`, `email`);
```

기존 데이터가 unique 제약을 위반하면 MariaDB가 DDL을 거부하고, `apply` mode에서는 애플리케이션 기동이 실패합니다.

## Foreign key

R2DBC 자체가 FK annotation을 제공하는 것은 아닙니다. FK 설정은 이 라이브러리의 `@DdlForeignKey`가 제공합니다.

현재는 단일 컬럼 FK만 지원합니다.

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

생성 예시:

```sql
ALTER TABLE `order_master`
    ADD CONSTRAINT `fk_order_master_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `user_master` (`id`);
```

FK 적용은 기본값이 꺼져 있습니다.

```yaml
r2dbc-schema-manager:
  mode: apply
  apply-foreign-keys: true
```

기존 데이터가 FK를 위반하면 MariaDB가 DDL을 거부합니다.

## 필드 변경 시나리오

### 필드 추가

엔티티에 새 필드가 추가되고 DB 컬럼이 없으면 `ADD_COLUMN`으로 감지합니다.

```java
@DdlColumn(type = "varchar", length = 100, nullable = true)
private String nickname;
```

생성 예시:

```sql
ALTER TABLE `user_master` ADD COLUMN `nickname` varchar(100);
```

정책:

- nullable 컬럼: 적용 가능
- `nullable=false` + default 있음: 적용 가능
- `nullable=false` + default 없음 + 기존 테이블: report-only

마지막 경우는 기존 row가 새 NOT NULL 제약을 만족하지 못할 수 있기 때문입니다.

### 필드 삭제

엔티티에서 필드가 사라졌지만 DB에 컬럼이 남아 있으면 `EXTRA_COLUMN`으로 report합니다.

컬럼은 자동 삭제하지 않습니다.

### 필드 rename

예를 들어 `name`이 `userName`으로 바뀐 경우, 현재 엔티티 상태만 보고 rename인지 신규 필드인지 판단할 수 없습니다.

따라서 다음처럼 처리합니다.

```text
EXTRA_COLUMN: name
ADD_COLUMN: user_name
```

자동 rename은 하지 않습니다.

### 필드 타입 변경

엔티티는 `varchar(200)`을 기대하지만 DB가 `varchar(100)`이면 `MODIFY_COLUMN_TYPE`으로 감지합니다.

```yaml
r2dbc-schema-manager:
  sync-existing-column-types: true
```

일 때만 적용 대상입니다.

생성 예시:

```sql
ALTER TABLE `user_master` MODIFY COLUMN `email` varchar(200);
```

타입 변경은 테이블 lock이나 데이터 상태에 따라 실패할 수 있으므로 운영 DB에서는 주의해야 합니다.

### nullable/default/comment 변경

현재 버전은 감지만 하고 자동 적용하지 않습니다.

```text
NULLABILITY_MISMATCH
DEFAULT_MISMATCH
COMMENT_MISMATCH
```

## 실제 프로젝트에서 테스트하는 순서

1. 이 라이브러리에서 `./gradlew clean test publishToMavenLocal`
2. 실제 프로젝트에 `mavenLocal()`과 starter 의존성 추가
3. `mode=dry-run`으로 먼저 실행
4. 로그에 출력되는 SQL 검토
5. Testcontainers 또는 disposable DB에서 `mode=apply` 검증
6. 필요할 때만 `apply-foreign-keys=true`
7. 공유 개발 DB나 운영 DB에서는 바로 `apply` 금지

## 샘플 프로젝트

별도 샘플 앱이 있습니다.

```bash
./gradlew publishToMavenLocal
./gradlew -p samples/mariadb-smoke-app clean test
```

샘플은 Maven Local에 배포된 starter artifact를 실제 Spring Boot 앱에서 소비하고, MariaDB Testcontainer를 띄워 테이블, index, unique index, foreign key 생성을 검증합니다.

## GitHub Actions

현재 CI workflow가 있습니다.

- `.github/workflows/ci.yml`: push/PR 시 테스트 실행

Maven Central 자동 배포 workflow는 아직 설정되어 있지 않습니다.
