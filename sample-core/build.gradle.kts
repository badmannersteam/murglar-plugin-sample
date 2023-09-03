plugins {
    alias(catalog.plugins.murglar.plugin.core)
}

murglarPlugin {
    id = "sample"
    name = "Sample"
    version = "1"
    murglarClass = "com.badmanners.murglar.lib.sample.SampleMurglar"
}
