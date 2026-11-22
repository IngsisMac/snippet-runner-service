pluginManagement {
    // La version de snippet-plugins no vive en este repo. Por defecto se resuelve la ultima
    // publicada en GitHub Packages, asi que subir el plugin no obliga a tocar este archivo.
    // Se pinnea con -PsnippetPluginsVersion=0.1.1 o con la variable SNIPPET_PLUGINS_VERSION
    // cuando hace falta un build reproducible.
    val pinnedPluginsVersion: String? =
        providers.gradleProperty("snippetPluginsVersion").orNull
            ?: System.getenv("SNIPPET_PLUGINS_VERSION")

    repositories {
        // mavenLocal va primero y no tapa al registry: ante una version dinamica Gradle junta
        // las versiones de TODOS los repositorios y se queda con la mayor, no con la del primero
        // que conteste. Un 0.1.1 viejo en ~/.m2 pierde contra un 0.2.0 publicado. Ademas, si no
        // hay credenciales de GitHub Packages, Gradle sigue adelante con lo que encuentre aca en
        // vez de abortar: es lo que mantiene vivo el hook de pre-commit en una maquina sin token.
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/IngsisMac/snippet-plugins")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }

    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("ingsis.snippet.")) {
                useVersion(pinnedPluginsVersion ?: "latest.release")
            }
        }
    }
}

rootProject.name = "snippet-runner-service"
