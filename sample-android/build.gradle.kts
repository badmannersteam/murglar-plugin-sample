plugins {
    alias(catalog.plugins.murglar.plugin.android)
}

murglarAndroidPlugin {
    id = "sample"
    name = "Sample"
    version = catalog.versions.sample.map(String::toInt)
    entryPointClass = "com.badmanners.murglar.lib.sample.SampleMurglar"
}

dependencies {
    implementation(project(":sample-core"))
}
