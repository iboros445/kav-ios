plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

// KavKit, the framework the Swift app links. It holds only the entry point (MainViewController.kt); everything else
// comes from :kav as a plain dependency. Kotlin/Native exports to Objective-C only this module's public API (plus
// libraries named with export()), so the header is one function instead of every public type in upstream and the
// compat layer. That export was most of the framework link time and caused the Swift name clashes.
kotlin {
    compilerOptions {
        optIn.addAll(
            "kotlinx.cinterop.ExperimentalForeignApi",
            "kotlin.experimental.ExperimentalNativeApi",
        )
    }

    // See :kav for the _LocationEssentials stub.
    val frameworkStubs = providers.environmentVariable("KAV_FRAMEWORK_STUBS").orNull
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "KavKit"
            isStatic = true
        }
        target.binaries.all {
            frameworkStubs?.let { linkerOpts("-F$it") }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
        }
        iosMain.dependencies {
            implementation(project(":kav"))
        }
    }
}
