import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

val repoRoot = rootDir.parentFile
val upstreamSrc = File(repoRoot, "android/app/src/main/java")
val overridesFile = File(rootDir, "overrides.txt")
val scope = providers.gradleProperty("kav.upstreamScope").getOrElse("none")

// Upstream files that must NOT be compiled for iOS because an override replaces them.
// Format of overrides.txt: "<path under android/app/src/main/java>  <base-sha>  <reason>", '#' starts a comment.
val overridden: Set<String> = if (overridesFile.exists()) overridesFile.readLines()
    .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
    .map { it.split(Regex("\\s+")).first() }.toSet() else emptySet()

// Copy, not srcDir+exclude, so a same-named override file is never excluded with the upstream one.
val syncUpstream by tasks.registering(Sync::class) {
    from(upstreamSrc) {
        when (scope) {
            "none" -> exclude("**")
            "data" -> include("uk/noammm/kav/data/**", "uk/noammm/kav/ui/Strings.kt", "uk/noammm/kav/ui/Theme.kt")
            "all" -> {}
            else -> throw GradleException("kav.upstreamScope must be none, data or all, not '$scope'")
        }
        overridden.forEach { exclude(it) }
    }
    into(layout.buildDirectory.dir("upstream/kotlin"))
}

// JvmStd.kt.template is stamped once for every package the synced upstream sources declare, so
// upstream calls like String.format(...) or Charsets.UTF_8 resolve (same-package beats default imports).
val stampJvmStd by tasks.registering {
    dependsOn(syncUpstream)
    val template = file("src/iosMain/jvmstd/JvmStd.kt.template")
    val srcDir = layout.buildDirectory.dir("upstream/kotlin")
    val out = layout.buildDirectory.dir("generated/jvmstd")
    inputs.file(template)
    inputs.files(syncUpstream.map { it.outputs.files }).optional()
    outputs.dir(out)
    doLast {
        val pkgs = srcDir.get().asFile.takeIf { it.exists() }?.walkTopDown().orEmpty().filter { it.extension == "kt" }
            .mapNotNull { f -> f.useLines { l -> l.firstOrNull { it.startsWith("package ") }?.removePrefix("package ")?.trim() } }
            .toSortedSet()
        // Always stamped, so the template itself is compile-checked even when no upstream is synced yet.
        pkgs.add("uk.noammm.kav.compat.selftest")
        val dir = out.get().asFile.apply { deleteRecursively(); mkdirs() }
        pkgs.forEach { p -> File(dir, "JvmStd_${p.replace('.', '_')}.kt").writeText(template.readText().replace("__PACKAGE__", p)) }
    }
}

kotlin {
    compilerOptions {
        // The shims call Foundation/posix through cinterop and use Native-only APIs on purpose.
        optIn.addAll(
            "kotlinx.cinterop.ExperimentalForeignApi",
            "kotlin.experimental.ExperimentalNativeApi",
            "kotlin.time.ExperimentalTime",
            "kotlin.concurrent.atomics.ExperimentalAtomicApi",
            "kotlin.native.concurrent.ObsoleteWorkersApi",
        )
        // Upstream code is compiled as-is; its warnings are not ours to fix.
        suppressWarnings.set(false)
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "KavKit"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        iosMain {
            kotlin.srcDir(files(layout.buildDirectory.dir("upstream/kotlin")).builtBy(syncUpstream))
            kotlin.srcDir(files(layout.buildDirectory.dir("generated/jvmstd")).builtBy(stampJvmStd))
        }
        iosTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
