plugins {
    // Versions come from the root project's plugin declarations.
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "app.cloudstream.desktop"
version = "1.0.0"

/**
 * Upstream's `library` module, compiled for the JVM.
 *
 * The sources are used unmodified: `library` already declares a `jvm()` target with real
 * `actual` implementations in `jvmMain`, so this is not a re-implementation or a stub. That
 * matters because extensions are compiled against the *Android* variant of these exact
 * classes — for their translated bytecode to link, the desktop runtime has to expose
 * binary-compatible `com.lagradost.cloudstream3.*` types. Compiling the same source with the
 * same Kotlin version (upstream and this project both use 2.4.0) is the only reliable way to
 * guarantee that.
 *
 * Multiplatform rather than plain JVM only because the sources contain `expect`/`actual`
 * declarations, which need a multiplatform module even when there is a single target.
 */
kotlin {
    jvmToolchain(21)

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                "-Xexpect-actual-classes",
                "-opt-in=com.lagradost.cloudstream3.InternalAPI",
                "-opt-in=com.lagradost.cloudstream3.Prerelease",
            )
        }
    }

    // Relative to this project directory (cloudstream-desktop/cs-compat).
    val upstream = "../../src-cloudstream/library/src"

    sourceSets {
        // Mirrors upstream's layout: expect declarations in commonMain, JVM actuals in
        // jvmMain, and the shared JVM-only code in an intermediate jvmCommon set.
        val commonMain by getting {
            kotlin.srcDir("$upstream/commonMain/kotlin")
        }

        val jvmCommonMain by creating {
            dependsOn(commonMain)
            kotlin.srcDir("$upstream/jvmCommonMain/kotlin")
        }

        val jvmMain by getting {
            dependsOn(jvmCommonMain)
            kotlin.srcDir("$upstream/jvmMain/kotlin")

            dependencies {
                // Mirrors upstream's `commonMain` + `jvmCommonMain` dependency set.
                implementation("androidx.annotation:annotation:1.10.0")
                implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
                implementation("org.jsoup:jsoup:1.22.1")
                implementation("org.jetbrains.kotlinx:atomicfu:0.33.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("com.fleeksoft.ksoup:ksoup:0.2.6")
                implementation("io.ktor:ktor-http:3.5.0")
                implementation("com.github.Blatzar:NiceHttp:0.4.18")
                implementation("org.mozilla:rhino:1.8.1")
                implementation("dev.whyoleg.cryptography:cryptography-core:0.6.0")
                implementation("dev.whyoleg.cryptography:cryptography-provider-optimal:0.6.0")
                implementation("org.jetbrains.kotlin:kotlin-reflect")
                implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.3")
            }
        }
    }
}
