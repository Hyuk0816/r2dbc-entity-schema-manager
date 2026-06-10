description = "Spring Boot starter for R2DBC Entity Schema Manager."

val springBootVersion = "3.5.14"

dependencies {
    api(project(":autoconfigure"))
    api(project(":core"))

    api("org.springframework.boot:spring-boot-starter-data-r2dbc:$springBootVersion")
}
