plugins {
    alias(catalog.plugins.murglar.plugin.android)
}

murglarAndroidPlugin {
    id = "sample"
    name = "Sample"
    version = 1
    murglarClass = "com.badmanners.murglar.lib.sample.SampleMurglar"
}

dependencies {
    implementation(project(":sample-core"))
}
