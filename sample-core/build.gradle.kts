plugins {
    alias(catalog.plugins.murglar.plugin.core)
}

murglarPlugin {
    id = "sample"
    name = "Sample"
    version = catalog.versions.sample.map(String::toInt)
    entryPointClass = "com.badmanners.murglar.lib.sample.SampleMurglar"
}
