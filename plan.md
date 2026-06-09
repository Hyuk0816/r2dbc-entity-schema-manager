# R2DBC Entity Schema Manager 기획서

작성일: 2026-06-09

## 1. 배경

Spring Data R2DBC는 엔티티 매핑과 repository 기능을 제공하지만, Hibernate의 `ddl-auto=update`처럼 엔티티 metadata를 기준으로 DB schema를 생성하거나 갱신하는 표준 기능은 제공하지 않는다.

이 기획서는 특정 프로젝트 전용 기능이 아니라, Spring Boot와 Spring Data R2DBC를 사용하는 일반 애플리케이션에서 재사용할 수 있는 범용 schema manager 라이브러리를 정의한다.

이 라이브러리는 "R2DBC 전용 Hibernate 전체"가 아니라, 그중 schema management 레이어를 먼저 구현하는 것을 목표로 한다. 영속성 컨텍스트, 더티 체킹, lazy loading, association runtime 관리는 포함하지 않는다.

## 2. 제품 포지션

`R2DBC Entity Schema Manager`는 R2DBC 엔티티와 실제 DB schema를 비교해 차이를 감지하고, 정책에 따라 DDL을 생성하거나 적용하는 도구이다.

핵심 원칙은 다음과 같다.

```text
감지는 넓게 한다.
자동 적용은 정책으로 제한한다.
삭제와 rename은 자동 적용하지 않는다.
```

즉, 테이블, 컬럼, 인덱스, unique key, primary key, foreign key, 타입 차이, nullable 차이, default 차이, comment 차이는 감지한다. 다만 데이터 손실이나 운영 lock 위험이 큰 변경은 기본적으로 report만 출력한다.

## 3. 목표

- Spring Data R2DBC 엔티티를 스캔한다.
- `@Table`, `@Column`, `@Id`와 라이브러리 전용 DDL annotation을 해석한다.
- 엔티티 metadata로 기대 schema 모델을 만든다.
- 실제 DB schema를 조회한다.
- 기대 schema와 실제 schema의 차이를 diff로 분류한다.
- 신규 테이블, 신규 컬럼, 신규 인덱스, 신규 unique index, 기존 컬럼 타입 차이에 대해 DDL을 생성한다.
- primary key, foreign key, unique key 변경을 감지하고 report한다.
- 설정에 따라 `dry-run`, `validate`, `apply` mode로 동작한다.
- 명시적 `@Column`이 없는 필드에는 설정된 `name-case` 규칙을 적용해 컬럼명을 생성한다.
- 기본 타입 추론을 제공하되, annotation으로 DDL 정보를 override할 수 있게 한다.

## 4. 비목표

다음은 schema manager의 1차 목표에서 제외한다.

- 영속성 컨텍스트 구현
- 더티 체킹
- flush 처리
- lazy loading
- association runtime 관리
- JPQL 또는 query abstraction
- 데이터 backfill 자동 생성
- 컬럼 삭제 자동 적용
- 컬럼 rename 자동 적용
- 운영 배포용 migration 이력 관리

컬럼 삭제와 rename은 별도 자동 동작으로 제공하지 않는다. 엔티티에서 `name` 필드가 사라지고 `userName` 필드가 생긴 경우, 도구는 기존 `name` 컬럼을 삭제하거나 rename하지 않고 `user_name` 컬럼을 신규 컬럼으로 추가한다. 기존 `name` 컬럼은 `EXTRA_COLUMN`으로 report만 한다.

## 5. 기본 동작

도구는 Spring Boot auto-configuration 형태로 제공한다.

```text
애플리케이션 시작
  -> r2dbc-schema-manager.enabled=true 확인
  -> 기존 ConnectionFactory 주입
  -> 기존 RelationalMappingContext 주입
  -> 엔티티 schema 모델 생성
  -> DB schema 모델 조회
  -> schema diff 생성
  -> DDL plan 생성
  -> DDL 적용 순서 정렬
  -> mode와 apply policy에 따라 report, 실패, 또는 DDL 실행
  -> schema sync가 끝난 뒤 애플리케이션 기동 완료
```

DB 접속 정보는 라이브러리가 별도로 관리하지 않는다. 애플리케이션이 이미 생성한 `ConnectionFactory`를 주입받아 그대로 사용한다. 이 방식은 `spring.r2dbc.*`를 쓰는 일반 프로젝트와, 사용자가 직접 커스텀 `ConnectionFactory` bean을 구성하는 프로젝트 모두에 대응하기 쉽다.

schema sync는 `ApplicationRunner`처럼 애플리케이션 기동이 거의 끝난 뒤 실행되는 방식보다 이른 시점에 실행한다. Hibernate가 `SessionFactory`를 만드는 과정에서 schema management를 수행하는 것처럼, 이 라이브러리도 Spring bean 초기화 단계에서 schema sync를 끝내고 실패 시 `ApplicationContext` 기동을 실패시킨다. 이렇게 해야 웹 요청, scheduler, message consumer 같은 애플리케이션 runtime entrypoint가 정상 동작하기 전에 DB schema 상태를 먼저 확정할 수 있다.

## 6. 설정

기본값은 비활성화이다. `application.yml`에 설정을 추가하지 않거나 `enabled=false`이면 동작하지 않는다.

```yaml
r2dbc-schema-manager:
  enabled: true
  mode: dry-run # dry-run | validate | apply
  dialect: mariadb
  schema: app_schema # 선택값, 없으면 SELECT DATABASE() 결과 사용
  name-case: spring # spring | snake_case | lower_camel | upper_camel | lower | upper | as_is
  sync-existing-column-types: true
  apply-indexes: true
  apply-unique-indexes: true
  apply-foreign-keys: false
  fail-on-dangerous-diff: false
```

### mode 정의

| mode | 동작 |
|------|------|
| `dry-run` | 생성될 SQL과 diff report를 출력하고 실행하지 않는다. |
| `validate` | 적용 대상 diff 또는 위험 diff가 있으면 애플리케이션 기동을 실패시킨다. |
| `apply` | 적용 가능한 diff에 대해 DDL을 실행한다. |

### name-case 정의

`name-case`는 명시적 `@Column`이 없는 필드의 DB 컬럼명을 생성할 때 사용한다. 기본값은 `spring`이다. Spring Data Relational은 기본적으로 naming strategy를 사용하며, 관례상 `firstName`을 `first_name`으로 매핑한다.

이 도구는 다음 우선순위를 따른다.

```text
1. @Column("...")이 있으면 해당 이름을 그대로 사용
2. @DdlColumn(name = "...")이 있으면 해당 이름을 사용
3. 둘 다 없으면 r2dbc-schema-manager.name-case 적용
4. name-case=spring이면 Spring Data MappingContext가 계산한 column name 사용
```

| name-case | 예시 `userName` |
|-----------|----------------|
| `spring` | Spring Data NamingStrategy 결과 사용 |
| `snake_case` | `user_name` |
| `lower_camel` | `userName` |
| `upper_camel` | `UserName` |
| `lower` | `username` |
| `upper` | `USERNAME` |
| `as_is` | Java 필드명을 그대로 사용 |

## 7. Auto Configuration 설계

```java
@Configuration
@ConditionalOnProperty(
    prefix = "r2dbc-schema-manager",
    name = "enabled",
    havingValue = "true"
)
public class R2dbcSchemaManagerAutoConfiguration {

    @Bean
    R2dbcSchemaManagerInitializer r2dbcSchemaManagerInitializer(
            ConnectionFactory connectionFactory,
            RelationalMappingContext mappingContext,
            R2dbcSchemaManagerProperties properties
    ) {
        return new R2dbcSchemaManagerInitializer(
                DatabaseClient.create(connectionFactory),
                mappingContext,
                properties
        );
    }
}
```

`@ConditionalOnProperty`를 사용하는 이유는 사용자가 `yml`에서 명시적으로 기능을 켠 경우에만 bean을 등록하기 위해서이다. 이 기능은 DB schema를 변경할 수 있으므로, 기본 동작은 반드시 비활성화되어야 한다.

`R2dbcSchemaManagerInitializer`는 `ApplicationRunner`가 아니라 bean 초기화 단계에서 실행되는 startup component로 둔다. 구현 방식은 `InitializingBean`, `SmartInitializingSingleton`, 명시적 `initMethod` 중 하나를 사용할 수 있다. 핵심 요구사항은 다음과 같다.

```text
- ConnectionFactory와 RelationalMappingContext가 준비된 뒤 실행한다.
- ApplicationRunner, CommandLineRunner, ApplicationReadyEvent보다 먼저 끝난다.
- 실패하면 예외를 전파해 ApplicationContext 기동을 실패시킨다.
- apply mode에서 일부 DDL이 성공한 뒤 실패할 수 있으므로 rollback을 보장한다고 말하지 않는다.
```

## 8. 엔티티 메타데이터 수집

Spring Data Relational의 mapping metadata를 우선 사용한다.

- `@Table`에서 table 이름을 구한다.
- `@Column`에서 column 이름을 구한다.
- `@Id` 필드는 신규 테이블 생성 시 primary key 후보로 사용한다.
- 이미 존재하는 테이블에서 `@Id` 컬럼 또는 primary key 정의가 다르면 `PRIMARY_KEY_MISMATCH`로 report한다.
- `transient` 필드, `@Transient` 필드, static 필드는 제외한다.
- 컬럼 타입은 기본 추론을 우선 사용한다.
- `@DdlColumn`이 있으면 기본 추론보다 우선한다.
- `@DdlIndex`, `@DdlUnique`, `@DdlForeignKey`가 있으면 key/index metadata로 수집한다.

## 9. 기본 타입 추론

| Java 타입 | MariaDB DDL |
|----------|-------------|
| `String` | `varchar(255)` |
| `Long`, `long` | `bigint` |
| `Integer`, `int` | `int` |
| `Boolean`, `boolean` | `tinyint(1)` |
| `LocalDate` | `date` |
| `LocalDateTime` | `datetime` |
| `BigDecimal` | `decimal(19,2)` |
| `byte[]` | `blob` |
| `Enum` | `varchar(50)` |

기본 추론은 편의 기능이다. 업무적으로 길이, precision, scale, default, comment가 중요한 컬럼은 annotation으로 명시해야 한다.

## 10. Annotation 설계

### DdlColumn

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DdlColumn {
    String name() default "";
    String type() default "";
    int length() default -1;
    int precision() default -1;
    int scale() default -1;
    boolean nullable() default true;
    String defaultValue() default "";
    String comment() default "";
}
```

사용 예시는 다음과 같다.

```java
@DdlColumn(type = "varchar", length = 100, nullable = false, defaultValue = "''", comment = "사용자명")
private String userName;
```

이미 존재하는 테이블에 컬럼을 추가할 때 `nullable=false`이고 `defaultValue`가 없는 신규 컬럼은 기존 row가 있는 테이블에서 실패할 수 있으므로 자동 적용하지 않는다. 신규 테이블 생성 시에는 기존 row가 없으므로 해당 제약을 그대로 생성할 수 있다.

### DdlIndex

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface DdlIndex {
    String name() default "";
    String[] columns() default {};
}
```

field에 붙이면 해당 필드 하나를 대상으로 하는 index이다. type에 붙이면 `columns`로 복합 index를 정의한다.

### DdlUnique

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface DdlUnique {
    String name() default "";
    String[] columns() default {};
}
```

field에 붙이면 단일 컬럼 unique index이다. type에 붙이면 `columns`로 복합 unique key를 정의한다.

### DdlForeignKey

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DdlForeignKey {
    String name() default "";
    String referencedTable();
    String referencedColumn();
}
```

foreign key는 감지와 DDL 생성 대상이지만, 기존 데이터 위반 가능성이 있으므로 기본 자동 적용은 비활성화한다. `apply-foreign-keys=true`일 때만 apply mode에서 실행한다.

## 11. DB Schema 조회

schema 이름은 설정값이 있으면 설정값을 사용하고, 없으면 `SELECT DATABASE()` 결과를 사용한다.

테이블은 `information_schema.tables`에서 조회한다.

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = :schema
  AND table_type = 'BASE TABLE'
```

컬럼은 `information_schema.columns`에서 조회한다.

```sql
SELECT table_name,
       column_name,
       data_type,
       character_maximum_length,
       numeric_precision,
       numeric_scale,
       is_nullable,
       column_default,
       column_comment
FROM information_schema.columns
WHERE table_schema = :schema
```

인덱스와 unique key는 `information_schema.statistics`에서 조회한다.

```sql
SELECT table_name,
       index_name,
       non_unique,
       column_name,
       seq_in_index
FROM information_schema.statistics
WHERE table_schema = :schema
```

primary key와 foreign key는 `information_schema.table_constraints`와 `information_schema.key_column_usage`에서 조회한다.

```sql
SELECT tc.table_name,
       tc.constraint_name,
       tc.constraint_type,
       kcu.column_name,
       kcu.referenced_table_name,
       kcu.referenced_column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_schema = kcu.constraint_schema
 AND tc.table_name = kcu.table_name
 AND tc.constraint_name = kcu.constraint_name
WHERE tc.constraint_schema = :schema
```

## 12. Diff 정책

### 감지 대상

| 상황 | 분류 | 기본 처리 |
|------|------|-----------|
| 엔티티 테이블이 DB에 없음 | `CREATE_TABLE` | 적용 가능 |
| 엔티티 필드가 DB 컬럼에 없음 | `ADD_COLUMN` | 적용 가능 |
| 엔티티 인덱스가 DB에 없음 | `ADD_INDEX` | 적용 가능 |
| 엔티티 unique key가 DB에 없음 | `ADD_UNIQUE_KEY` | 적용 가능 |
| 엔티티 foreign key가 DB에 없음 | `ADD_FOREIGN_KEY` | report, 옵션 적용 |
| 타입이 다름 | `MODIFY_COLUMN_TYPE` | 옵션 적용 |
| nullable이 다름 | `NULLABILITY_MISMATCH` | report |
| default가 다름 | `DEFAULT_MISMATCH` | report |
| comment가 다름 | `COMMENT_MISMATCH` | report |
| primary key 정의가 다름 | `PRIMARY_KEY_MISMATCH` | report |
| foreign key 정의가 다름 | `FOREIGN_KEY_MISMATCH` | report |
| unique key 정의가 다름 | `UNIQUE_KEY_MISMATCH` | report |
| index 정의가 다름 | `INDEX_MISMATCH` | report |
| DB 컬럼이 엔티티에 없음 | `EXTRA_COLUMN` | report, 삭제하지 않음 |
| DB 테이블이 엔티티에 없음 | `EXTRA_TABLE` | report, 삭제하지 않음 |

### 자동 적용 대상

1차 구현의 자동 적용 대상은 다음이다.

- `CREATE_TABLE`
- `ADD_COLUMN`
- `ADD_INDEX`
- `ADD_UNIQUE_KEY`
- `ADD_FOREIGN_KEY`, 단 `apply-foreign-keys=true`일 때만
- `MODIFY_COLUMN_TYPE`, 단 `sync-existing-column-types=true`일 때만

### 자동 미적용 대상

다음 diff는 감지와 report만 수행한다.

- `EXTRA_COLUMN`
- `EXTRA_TABLE`
- `PRIMARY_KEY_MISMATCH`
- `FOREIGN_KEY_MISMATCH`
- `UNIQUE_KEY_MISMATCH`
- `INDEX_MISMATCH`
- `NULLABILITY_MISMATCH`
- `DEFAULT_MISMATCH`
- `COMMENT_MISMATCH`

## 13. DDL 적용 순서

`apply` mode에서는 diff를 발견한 순서대로 실행하지 않고, 의존성에 맞춰 DDL plan을 정렬한 뒤 실행한다. 특히 foreign key는 참조 대상 테이블과 컬럼이 모두 존재해야 하므로 항상 마지막 단계에서 적용한다.

기본 적용 순서는 다음과 같다.

```text
1. CREATE_TABLE
   신규 테이블을 먼저 생성한다.

2. ADD_COLUMN
   기존 테이블에 누락 컬럼을 추가한다.

3. MODIFY_COLUMN_TYPE
   sync-existing-column-types=true인 경우에만 기존 컬럼 타입을 변경한다.

4. ADD_INDEX
   일반 인덱스를 생성한다.

5. ADD_UNIQUE_KEY
   unique index 또는 unique key를 생성한다.

6. ADD_FOREIGN_KEY
   apply-foreign-keys=true인 경우에만 foreign key를 생성한다.
```

이 순서는 Hibernate schema migration의 큰 흐름과 비슷하게 잡는다. 테이블과 컬럼을 먼저 맞추고, 그 다음 index와 unique key를 적용하며, foreign key는 모든 테이블 처리가 끝난 뒤 적용한다. 이렇게 해야 서로 다른 테이블 사이의 참조 관계나 cross-schema 참조가 있어도 참조 대상이 먼저 만들어질 가능성이 높다.

동일 단계 안에서는 가능하면 안정적인 정렬을 사용한다.

```text
- schema 이름
- table 이름
- column 또는 constraint 이름
```

DDL 실행은 하나의 DML transaction처럼 rollback된다고 가정하지 않는다. 따라서 `apply` mode에서 중간 DDL이 실패하면 즉시 중단하고, 이미 실행된 DDL은 report에 남긴다.

## 14. Rename / Delete 정책

rename과 delete는 도구가 자동 판단하지 않는다.

예를 들어 DB에 `name` 컬럼이 있고 엔티티가 다음처럼 변경된 경우:

```java
private String userName;
```

`name-case=snake_case`라면 도구는 `user_name` 컬럼을 신규 컬럼으로 판단한다. 기존 `name` 컬럼은 `EXTRA_COLUMN`으로 report만 하고 삭제하지 않는다.

이 정책을 선택하는 이유는 현재 엔티티 상태만 보고는 rename과 신규 추가를 구분할 수 없기 때문이다.

## 15. DDL 생성 규칙

테이블 생성 예시:

```sql
CREATE TABLE `user_master` (
    `id` varchar(36) NOT NULL,
    `user_name` varchar(100) NOT NULL DEFAULT '' COMMENT '사용자명',
    PRIMARY KEY (`id`)
);
```

컬럼 추가 예시:

```sql
ALTER TABLE `user_master` ADD COLUMN `user_name` varchar(100) NOT NULL DEFAULT '' COMMENT '사용자명';
```

기존 컬럼 타입 변경 예시:

```sql
ALTER TABLE `user_master` MODIFY COLUMN `user_name` varchar(200);
```

인덱스 생성 예시:

```sql
CREATE INDEX `idx_user_master_user_name` ON `user_master` (`user_name`);
```

unique key 생성 예시:

```sql
CREATE UNIQUE INDEX `uk_user_master_user_name` ON `user_master` (`user_name`);
```

foreign key 생성 예시:

```sql
ALTER TABLE `order_master`
    ADD CONSTRAINT `fk_order_master_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `user_master` (`id`);
```

MariaDB dialect에서는 identifier quoting에 backtick을 사용한다.

## 16. 오류 처리

- `dry-run`: 가능한 SQL과 모든 diff report를 출력하고 실행하지 않는다.
- `validate`: 적용 대상 diff 또는 위험 diff가 있으면 기동 실패 처리한다.
- `apply`: 적용 가능한 DDL을 순차 실행한다.
- `apply`: DDL은 `CREATE_TABLE -> ADD_COLUMN -> MODIFY_COLUMN_TYPE -> ADD_INDEX -> ADD_UNIQUE_KEY -> ADD_FOREIGN_KEY` 순서로 실행한다.
- `apply`: 하나의 DDL이라도 실패하면 기동 실패 처리한다.
- 실패 시 이미 적용된 이전 DDL을 자동 rollback하지 않는다.

DDL은 일반 DML 트랜잭션처럼 안전하게 rollback된다고 가정하지 않는다. DBMS별 DDL commit 정책이 다르기 때문이다.

## 17. 구성요소

```text
R2dbcSchemaManagerProperties
  yml 설정 바인딩

R2dbcSchemaManagerAutoConfiguration
  Spring Boot 조건부 bean 등록

R2dbcSchemaManagerInitializer
  bean 초기화 단계에서 schema sync 실행

EntitySchemaScanner
  Spring Data mapping metadata와 DDL annotation에서 기대 schema 생성

DatabaseSchemaReader
  information_schema에서 실제 schema 조회

SchemaDiffEngine
  기대 schema와 실제 schema 비교

DiffPolicyEvaluator
  감지된 diff를 apply 가능, report only, dangerous로 분류

SchemaChangePlanner
  적용 가능한 diff를 DDL 적용 순서에 맞게 정렬

MariaDbDdlGenerator
  CREATE TABLE, ADD COLUMN, MODIFY COLUMN, CREATE INDEX, CREATE UNIQUE INDEX, ADD FOREIGN KEY DDL 생성

DdlExecutor
  dry-run, validate, apply mode 처리
```

## 18. 고려한 대안

### 대안 1. R2DBC 전용 Hibernate 전체 구현

영속성 컨텍스트, 더티 체킹, flush, lazy loading, association runtime 관리까지 포함하는 방향이다. 장기적으로는 가능하지만, 초기 목표가 지나치게 커진다. 현재 기획은 schema management 레이어만 먼저 구현한다.

### 대안 2. ADD COLUMN 전용 자동화

위험도는 낮지만 사용자가 원하는 테이블, 인덱스, 키, 타입 변경 감지 범위에 비해 너무 좁다.

### 대안 3. 모든 diff 자동 적용

강력하지만 컬럼 삭제, rename, key 변경, nullable/default 변경에서 데이터 손실이나 lock 문제가 커질 수 있다. 현재 기획에서는 감지는 넓게 하되 자동 적용은 정책으로 제한한다.

### 대안 4. Schema manager 레이어 우선 구현

엔티티와 DB schema의 차이를 넓게 감지하고, 안전하거나 명시적으로 허용된 변경만 적용한다. 현재 기획의 선택안이다.

## 19. 남는 리스크

- 기본 타입 추론이 업무 스키마와 다를 수 있다.
- 커스텀 converter가 있는 필드는 실제 저장 타입 판단이 어려울 수 있다.
- 다중 `ConnectionFactory` 프로젝트에서는 어떤 DB에 적용할지 명시해야 한다.
- 기존 row가 많은 테이블에 `ALTER TABLE`을 실행하면 lock 또는 지연이 발생할 수 있다.
- `MODIFY COLUMN`은 DBMS와 데이터 상태에 따라 실패하거나 lock이 길어질 수 있다.
- foreign key 추가는 기존 데이터 위반으로 실패할 수 있다.
- `name-case`가 애플리케이션의 실제 Spring Data NamingStrategy와 다르면, 도구가 생성한 컬럼명과 런타임 쿼리의 컬럼명이 달라질 수 있다.
- DB 계정에 DDL 권한이 없으면 `apply`는 실패한다.
- 호스트 프로젝트가 별도 DB 배포 정책을 갖고 있을 경우, `apply` mode 사용 여부를 그 정책과 분리해서 검토해야 한다.

## 20. 검증 계획

- 단위 테스트
  - Java 타입 -> MariaDB 타입 추론 검증
  - `name-case`별 컬럼명 변환 검증
  - `@DdlColumn` override 검증
  - `@DdlIndex` 인덱스 metadata 검증
  - `@DdlUnique` unique key metadata 검증
  - `@DdlForeignKey` foreign key metadata 검증
  - diff 분류 검증
  - DDL plan 정렬 검증
  - foreign key가 항상 table, column, unique key 생성 이후에 배치되는지 검증
  - DDL 문자열 생성 검증

- 라이브러리 자체 통합 테스트
  - 독립 라이브러리 프로젝트에서는 Testcontainers 기반 통합 테스트를 둔다.
  - MariaDB test schema에서 누락 테이블, 누락 컬럼, 누락 인덱스, unique key, foreign key, 타입 차이 감지와 `apply` 실행을 검증한다.
  - `apply-foreign-keys=true`에서 참조 대상 테이블과 컬럼 생성 이후 foreign key가 생성되는지 검증한다.
  - `dry-run`에서 SQL이 실행되지 않는지 검증한다.
  - `validate`에서 적용 대상 diff가 있을 때 실패하는지 검증한다.

- 호스트 프로젝트 적용 전 확인
  - 대상 애플리케이션의 `ConnectionFactory` 주입 가능 여부 확인
  - 현재 DAO/entity annotation 패턴 확인
  - 커스텀 converter 필드 처리 기준 확인
  - 호스트 프로젝트의 DB 변경 정책과 `apply` mode 사용 가능 여부 확인

## 21. 1차 구현 범위

1차 구현은 schema manager 레이어를 대상으로 한다.

- Spring Boot starter 구조
- `r2dbc-schema-manager.enabled` 조건부 활성화
- `dry-run`, `validate`, `apply` mode
- `ApplicationRunner`가 아닌 bean 초기화 단계의 schema sync
- 기존 `ConnectionFactory` 재사용
- `RelationalMappingContext` 기반 엔티티 스캔
- `@DdlColumn`, `@DdlIndex`, `@DdlUnique`, `@DdlForeignKey`
- MariaDB `information_schema.tables` 조회
- MariaDB `information_schema.columns` 조회
- MariaDB `information_schema.statistics` 조회
- MariaDB `information_schema.table_constraints` 조회
- MariaDB `information_schema.key_column_usage` 조회
- 신규 테이블 감지 및 생성
- 신규 컬럼 감지 및 추가
- 신규 인덱스 감지 및 생성
- 신규 unique key 감지 및 생성
- 신규 foreign key 감지 및 옵션 적용
- 기존 컬럼 타입 차이 감지 및 옵션 적용
- DDL plan 정렬
- foreign key 마지막 적용
- primary key mismatch 감지 및 report
- foreign key mismatch 감지 및 report
- unique key mismatch 감지 및 report
- index mismatch 감지 및 report
- nullable/default/comment mismatch 감지 및 report
- `name-case` 기반 컬럼명 생성
- 기본 타입 추론
- MariaDB DDL 생성 및 실행

1차 구현에서도 컬럼 삭제와 rename은 제공하지 않는다. 엔티티 필드명이 변경된 경우에는 기존 컬럼을 그대로 두고 새 필드명으로 계산된 신규 컬럼만 추가한다.
