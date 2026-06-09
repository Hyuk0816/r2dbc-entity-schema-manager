plugins {
    `java-library`
    `maven-publish`
}

group = "io.github.hyuk0816"
version = "0.1.0-SNAPSHOT"

val springBootVersion = "3.5.14"
val junitVersion = "5.12.2"
val assertjVersion = "3.27.7"

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    group = rootProject.group
    version = rootProject.version

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withSourcesJar()
        withJavadocJar()
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:$junitVersion"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core:$assertjVersion")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(17)
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                artifactId = when (project.path) {
                    ":core" -> "r2dbc-entity-schema-manager-core"
                    ":autoconfigure" -> "r2dbc-entity-schema-manager-autoconfigure"
                    ":starter" -> "r2dbc-entity-schema-manager-spring-boot-starter"
                    else -> project.name
                }
                from(components["java"])
                pom {
                    name.set(artifactId)
                    description.set("Entity driven schema manager for Spring Data R2DBC applications.")
                    url.set("https://github.com/hyuk0816/r2dbc-entity-schema-manager")
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }
                    developers {
                        developer {
                            id.set("hyuk0816")
                            name.set("hyuk0816")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/hyuk0816/r2dbc-entity-schema-manager.git")
                        developerConnection.set("scm:git:ssh://git@github.com:hyuk0816/r2dbc-entity-schema-manager.git")
                        url.set("https://github.com/hyuk0816/r2dbc-entity-schema-manager")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/Hyuk0816/r2dbc-entity-schema-manager")
                credentials {
                    username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                    password = findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
