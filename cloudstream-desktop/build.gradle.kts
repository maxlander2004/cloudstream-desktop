import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    id("org.jetbrains.compose") version "1.12.0"
}

group = "app.cloudstream.desktop"
version = "1.3.4"

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

    // Rewrites the translated classes: dex2jar renames Kotlin's hyphenated `-impl` methods to
    // `_impl`, which does not exist in the Kotlin runtime extensions are linked against.
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-commons:9.10.1")

    // Kotlin Compose 1.12 requires the Compose Base plugin to be applied to the root project
    // and activated for the JVM target; this activation isn't automatic in 1.12, so add it here.
    implementation(compose.desktop.currentOs)

    // Needed at compile time to install a Downloader for the YouTube extractor. okhttp is
    // already what CloudStream's own HTTP stack uses; this pins the same version.
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.3")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation(compose.desktop.currentOs)
    // Must match the Compose Multiplatform version above: material3 1.7.3 was built against
    // foundation-layout 1.7.3, and Gradle upgrades foundation-layout to 1.12.0 in the packaged
    // app. The old material3 then called WindowInsets$_notMobileKt.getSystemBars, which no
    // longer exists in 1.12.0, and the installer died at launch with a NoSuchMethodError.
    // 1.12.0-alpha03 is the alpha in the 1.12 line and what upstream's app pins itself.
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    // Rail and settings icons matching the original app's UI. 1.7.3 is the last published
    // build of this artifact; it only holds ImageVector data, so it does not touch the
    // foundation APIs that changed.
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    // Poster artwork on the home/search grids, like the Android app's poster cards.
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")

    // In-app playback: VLCJ embeds the local VLC/libvlc install in the app window. Users
    // without VLC keep the external-player fallback, so this is optional at runtime.
    implementation("uk.co.caprica:vlcj:4.12.1")

    // Compose Desktop JVM target activation.

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

/**
 * Renders one headless Compose frame on the *packaged* distributable: its own jlink runtime and
 * exactly the jars that ship in the installer.
 *
 * Two releases shipped installers that died before showing a window — one missing the
 * java.net.http JDK module, one with material3 built against an older foundation-layout than
 * Gradle resolves. Compiling and unit tests both passed, because the development classpath is
 * not the packaged one. This runs the same Material3 composition the app starts with, so a
 * linkage failure fails the build instead of the user's launch.
 */
tasks.register<Exec>("verifyRuntime") {
    group = "verification"
    description = "Render a headless Compose frame from the packaged app image"
    dependsOn("createDistributable", "testClasses")

    val imageDir = layout.buildDirectory.dir("compose/binaries/main/app/CloudStream")
    val testClassesDir = layout.buildDirectory.dir("classes/kotlin/test")
    val launcher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }

    doFirst {
        val image = imageDir.get().asFile

        // jpackage lays the app image out differently per platform: Linux keeps the jars in
        // lib/app with lib/runtime beside them, Windows keeps both at the image root. Probe
        // both layouts instead of assuming the build host's.
        fun firstDirWithJars(vararg relative: String): File? = relative
            .map { File(image, it) }
            .firstOrNull { dir ->
                dir.listFiles { file -> file.extension == "jar" }?.isNotEmpty() == true
            }

        val appDir = firstDirWithJars("lib/app", "app")
            ?: error("No application jars under $image")
        val runtime = listOf("lib/runtime", "runtime")
            .map { File(image, it) }
            .firstOrNull { it.isDirectory }
            ?: error("Packaged runtime not found under $image")

        // Compose ships the jlink image without a java executable (the launcher loads libjvm
        // directly), so the app jars are run on the build JDK. What matters is that these are
        // the jars from the installer, not the development classpath.
        val jars = appDir.listFiles { file -> file.extension == "jar" }!!
            .sortedBy { it.name }

        // ...and that the packaged runtime carries the JDK modules the app links against. A
        // jlink image only gets Compose's default module set, which has no java.net.http, so the
        // 1.3.0 installer died at launch with NoClassDefFoundError: java/net/http/HttpClient.
        val release = File(runtime, "release")
        require(release.isFile) { "Packaged runtime not found at $runtime" }
        val modules = release.readText().lineSequence()
            .firstOrNull { it.startsWith("MODULES=") }
            ?.removePrefix("MODULES=")
            ?.split(' ')
            ?.toSet()
            .orEmpty()
        val required = listOf("java.net.http", "java.desktop", "java.scripting", "jdk.unsupported")
        val missing = required.filterNot { it in modules }
        require(missing.isEmpty()) {
            "Packaged runtime is missing $missing; nativeDistributions must ship those modules"
        }

        val classpath = (listOf(testClassesDir.get().asFile) + jars)
            .joinToString(File.pathSeparator) { it.absolutePath }

        // jpackage leaves skiko's native library next to the jars; the launcher normally finds
        // it there. Name the directory explicitly so the smoke run does not depend on
        // java.library.path guessing right.
        val nativeDir = appDir.absolutePath
        commandLine(
            launcher.get().executablePath.asFile.absolutePath,
            "-Djava.awt.headless=true",
            "-Dskiko.renderApi=SOFTWARE",
            "-Dskiko.library.path=$nativeDir",
            "-Djava.library.path=$nativeDir",
            "--enable-native-access=ALL-UNNAMED",
            "-cp",
            classpath,
            "app.cloudstream.desktop.HeadlessRenderKt",
        )
        println("Rendering package smoke frame: ${jars.size} installer jars, ${modules.size} JDK modules")
    }
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
            packageVersion = "1.3.4"

            // jpackage builds the bundled runtime with jlink, whose default module set has no
            // java.net.http — the installer then died at launch with
            // NoClassDefFoundError: java/net/http/HttpClient, because the HTTP client, the
            // downloader, subtitles and DNS-over-HTTPS all use it. Extensions add further
            // reflective JDK use, so ship every module instead of guessing per release.
            includeAllModules = true
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
