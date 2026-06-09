description = "Spring Boot auto-configuration for R2DBC Entity Schema Manager."

dependencies {
    api(project(":core"))

    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("org.springframework.data:spring-data-r2dbc")
    compileOnly("org.springframework:spring-r2dbc")
    compileOnly("io.r2dbc:r2dbc-spi")
    compileOnly("io.projectreactor:reactor-core")

    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mariadb")
    testImplementation("org.mariadb:r2dbc-mariadb")
    testImplementation("org.mariadb.jdbc:mariadb-java-client")
}
