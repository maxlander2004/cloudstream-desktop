pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        // Upstream library dependencies that are only published there.
        maven("https://jitpack.io")
    }
}

rootProject.name = "cloudstream-desktop"

// The extension API is published as its own module so extensions compile against a stable
// artifact instead of against the app itself.
include(":plugin-api")
include(":extensions:archive-org")

// Upstream's extension API, compiled for the JVM so real install extensions can run here.
include(":cs-compat")
