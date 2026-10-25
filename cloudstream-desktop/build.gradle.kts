import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("multiplatform") version "2.4.0" apply false
    kotlin("plugin.serialization") version "2.4.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    id("org.jetbrains.compose") version "1.12.0"
}

group = "app.cloudstream.desktop"
version = "1.1.2"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":plugin-api"))

    // Upstream's extension API compiled for the JVM, so original CloudStream extensions can
    // link against the real com.lagradost.cloudstream3 classes they were built for.
    implementation(project(":cs-compat"))

    // Translates an extension's classes.dex into JVM bytecode.
    implementation("de.femtopedia.dex2jar:dex-translator:2.4.38")

    // Needed at compile time to install a Downloader for the YouTube extractor. okhttp is
    // already what CloudStream's own HTTP stack uses; this pins the same version.
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.3")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // Rail and settings icons matching the original app's UI.
    implementation(compose.materialIconsExtended)
    // Poster artwork on the home/search grids, like the Android app's poster cards.
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

/**
 * Builds and installs the example extension, then exercises it through the real loader:
 * browse, search and stream resolution against a live source. Needs network access.
 */
tasks.register<JavaExec>("verifyExtension") {
    group = "verification"
    description = "Install the example extension and load it through ExtensionHost"
    dependsOn(":extensions:archive-org:installExtension")
    mainClass = "app.cloudstream.desktop.ExtensionSmokeKt"
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

/**
 * Downloads real CloudStream extensions (.cs3) and runs them through the legacy host:
 * DEX translation, plugin load, browse, search and link resolution. Needs network access.
 */
tasks.register<JavaExec>("verifyLegacy") {
    group = "verification"
    description = "Load published CloudStream extensions through the legacy extension host"
    mainClass = "app.cloudstream.desktop.LegacySmokeKt"
    classpath = sourceSets["test"].runtimeClasspath
}

// Skiko (Compose's renderer) needs native access on modern JDKs.
tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

compose.desktop {
    application {
        mainClass = "app.cloudstream.desktop.MainKt"

        nativeDistributions {
            // .msi / .exe can only be produced on Windows (jpackage + WiX).
            // .deb is produced on Linux, .dmg on macOS.
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "CloudStream"
            packageVersion = "1.1.2"
            description = "CloudStream Desktop"
            vendor = "CloudStream Desktop"
            copyright = "GPL-3.0-or-later"

            windows {
                iconFile.set(project.file("assets/icon.ico"))
                menuGroup = "CloudStream"
                shortcut = true
                dirChooser = true
                // Stable GUID so upgrades replace the previous install instead of stacking.
                upgradeUuid = "6f1f9b52-8f0e-4a6d-9d1c-2b7a5e4c8d31"
            }

            linux {
                iconFile.set(project.file("assets/icon.png"))
                menuGroup = "CloudStream"
            }
        }
    }
}
