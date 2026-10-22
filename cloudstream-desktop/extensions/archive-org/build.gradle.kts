plugins {
    kotlin("jvm") version "2.4.0"
}

group = "app.cloudstream.desktop.extensions"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // compileOnly, not implementation: the app already has the API and the JSON library on
    // its class path and is the extension's parent class loader. Bundling copies into the
    // jar would give the extension its own Class objects for Catalog/CatalogItem, and the
    // cast in ExtensionHost would then fail with a ClassCastException.
    compileOnly(project(":plugin-api"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation(project(":plugin-api"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

/**
 * Copies the extension jar into the extensions folder the app scans on startup, so a local
 * build is installed the same way a downloaded extension is.
 */
val installExtension by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Install this extension into ~/.cloudstream-desktop/extensions"

    val target = File(System.getProperty("user.home"), ".cloudstream-desktop/extensions")
    from(tasks.jar)
    into(target)
    doLast { println("Installed ${tasks.jar.get().archiveFileName.get()} into $target") }
}
