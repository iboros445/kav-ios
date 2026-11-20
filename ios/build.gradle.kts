// Versions are pinned here once; :kav reads them through the plugin DSL.
// Bump all three together: the Compose plugin has to support the Kotlin version.
plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    kotlin("plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.compose") version "1.12.1" apply false
}
