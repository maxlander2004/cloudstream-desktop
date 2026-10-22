plugins {
    kotlin("jvm") version "2.4.0"
}

group = "app.cloudstream.desktop"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

// Deliberately dependency-free: an extension jar is compiled against these types and the
// app supplies them at runtime through the extension's parent class loader.
