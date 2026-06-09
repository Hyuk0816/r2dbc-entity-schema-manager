# R2DBC Entity Schema Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java 17 Spring Boot 3.x starter that compares Spring Data R2DBC entity metadata with MariaDB schema metadata and generates or applies ordered DDL.

**Architecture:** The project is split into `core`, `autoconfigure`, and `starter`. `core` owns schema models, diffing, policy, naming, type mapping, and MariaDB DDL generation without Spring dependencies. `autoconfigure` adapts Spring Boot, `ConnectionFactory`, `RelationalMappingContext`, and `DatabaseClient` into the core engine.

**Tech Stack:** Java 17, Gradle Kotlin DSL, JUnit 5, AssertJ, Spring Boot 3.5.x, Spring Data R2DBC, MariaDB dialect.

---

### Task 1: Project Skeleton

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `core/build.gradle.kts`
- Create: `autoconfigure/build.gradle.kts`
- Create: `starter/build.gradle.kts`

- [x] Create a Java 17 multi-module Gradle build.
- [x] Configure Maven publication metadata with a user-owned group id.
- [x] Use Spring Boot 3.5.14 dependency management for Spring-facing modules.

### Task 2: Core Engine

**Files:**
- Create: `core/src/main/java/io/github/hyuk0816/r2dbc/schema/**`
- Test: `core/src/test/java/io/github/hyuk0816/r2dbc/schema/**`

- [x] Add API stubs first so Red tests fail on behavior, not missing classes.
- [x] Test and implement name-case conversion.
- [x] Test and implement Java-to-MariaDB type mapping.
- [x] Test and implement schema diff classification.
- [x] Test and implement diff policy decisions.
- [x] Test and implement ordered MariaDB DDL generation.

### Task 3: Spring Boot Adapter

**Files:**
- Create: `autoconfigure/src/main/java/io/github/hyuk0816/r2dbc/schema/autoconfigure/**`
- Create: `autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [x] Add Spring Boot properties and auto-configuration.
- [x] Add startup initializer that runs before `ApplicationRunner`.
- [x] Add entity scanner based on `RelationalMappingContext`.
- [x] Add repository-domain fallback for real Spring Boot applications.
- [x] Add MariaDB `information_schema` reader.
- [x] Add DDL executor using `DatabaseClient`.

### Task 4: Verification

- [x] Add Testcontainers MariaDB integration tests.
- [x] Add Maven Local consuming sample smoke application.
- [x] Run `core:test`.
- [x] Run full `test`.
- [x] Run `publishToMavenLocal`.
- [x] Run sample smoke test.
- [x] Report exact verification status.
