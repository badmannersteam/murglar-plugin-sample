rootProject.name = "murglar-plugin-sample"

include("sample-core")
include("sample-android")

include("client-cli")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven { url = java.net.URI.create("https://jitpack.io") }
        mavenLocal()
    }
    resolutionStrategy {
        eachPlugin {
            // workaround for requesting plugins from plain maven repositories with new syntax
            val prefix = "murglar-gradle-plugin-"
            if (requested.id.id.startsWith(prefix)) {
                val artifactId = "${requested.id.id.substringAfter(prefix)}-plugin-gradle-plugin"
                useModule("com.github.badmannersteam.murglar-plugins:$artifactId:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("catalog") {
            version("murglar-plugins", "8.0")
            version("sample", "4")  // use a single number

            // for core module
            plugin("murglar-plugin-core", "murglar-gradle-plugin-core").versionRef("murglar-plugins")
            // for android module
            plugin("murglar-plugin-android", "murglar-gradle-plugin-android").versionRef("murglar-plugins")

            // for CLI client
            library("murglar-core", "com.github.badmannersteam.murglar-plugins", "core").versionRef("murglar-plugins")

            version("ktor", "3.4.3")
            library("ktor", "io.ktor", "ktor-client-okhttp").versionRef("ktor")
            library("ktor-logging", "io.ktor", "ktor-client-logging").versionRef("ktor")
            library("ktor-encoding", "io.ktor", "ktor-client-encoding").versionRef("ktor")
        }
    }
}
