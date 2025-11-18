plugins {
    java
    alias(catalog.plugins.murglar.plugin.core)
}

dependencies {
    implementation(catalog.murglar.core)

    implementation(project(":sample-core"))

    implementation(catalog.ktor)
    implementation(catalog.ktor.logging)
    implementation(catalog.ktor.encoding)
}