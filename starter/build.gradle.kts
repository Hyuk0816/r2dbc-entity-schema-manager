description = "Spring Boot starter for R2DBC Entity Schema Manager."

dependencies {
    api(project(":autoconfigure"))
    api(project(":core"))

    api(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))
    api("org.springframework.boot:spring-boot-starter-data-r2dbc")
}
