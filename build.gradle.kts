plugins {
    id("ingsis.snippet.spring") version "0.1.0"
}

group = "ingsis.snippet"
version = "0.1.0"

tasks.bootJar {
    archiveFileName.set("app.jar")
}

val printscriptVersion =
    providers
        .gradleProperty("printscriptVersion")
        .getOrElse(System.getenv("PRINTSCRIPT_VERSION") ?: "1.0.0-SNAPSHOT")

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        // Repo fijo: es el que publica el jar de PrintScript. No sale de GITHUB_REPOSITORY,
        // que dentro del CI de este repo apunta acá mismo y devuelve 404.
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/IngsisMac/printscript")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    implementation("com.printscript:runner:$printscriptVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}
