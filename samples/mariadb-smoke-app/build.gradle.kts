plugins {
    java
}

group = "io.github.hyuk0816.r2dbc.schema.samples"
version = "0.1.0-SNAPSHOT"

val springBootVersion = "3.5.14"
val testcontainersVersion = "1.21.4"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("io.github.hyuk0816:r2dbc-entity-schema-manager-spring-boot-starter:0.1.0-SNAPSHOT")
    runtimeOnly("org.mariadb:r2dbc-mariadb")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation(platform("org.testcontainers:testcontainers-bom:$testcontainersVersion"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mariadb")
    testImplementation("org.mariadb.jdbc:mariadb-java-client")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}
