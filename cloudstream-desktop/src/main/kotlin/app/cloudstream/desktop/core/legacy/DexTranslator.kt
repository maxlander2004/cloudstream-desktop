package app.cloudstream.desktop.core.legacy

import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.DexFileReader
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Turns an Android extension's Dalvik bytecode into JVM class files.
 *
 * CloudStream extensions ship `classes.dex`, which the JVM cannot execute. dex2jar
 * (`de.femtopedia.dex2jar`) translates the register-based Dalvik instructions into
 * equivalent stack-based JVM bytecode, so the extension's own code can run in a desktop JVM
 * alongside the library it was compiled against.
 *
 * Translation is cached by content hash: it takes a second or two and the archive never
 * changes, so it is done once per installed file.
 */
object DexTranslator {
    /**
     * Bumped whenever the translation itself changes, so cached jars from an older build are
     * not reused. v2 adds [restoreHyphenatedNames].
     */
    private const val CACHE_VERSION = 2

    fun cacheDir(): Path =
        Paths.get(System.getProperty("user.home"), ".cloudstream-desktop", "dex-cache")

    fun translate(archive: Path): Path {
        val raw = Files.readAllBytes(archive)
        val key = sha256(raw)
        val target = cacheDir().resolve("v$CACHE_VERSION-$key.jar")
        if (Files.isRegularFile(target) && Files.size(target) > 0L) return target

        Files.createDirectories(cacheDir())

        val dexes = dexEntries(raw)
        require(dexes.isNotEmpty()) { "${archive.fileName} contains no classes.dex" }

        val staging = Files.createTempDirectory(cacheDir(), "translate")
        try {
            val parts = dexes.mapIndexed { index, dex ->
                val part = staging.resolve("part-$index.jar")
                Dex2jar.from(DexFileReader(dex)).skipDebug().topoLogicalSort().to(part)
                part
            }

            val merged = staging.resolve("merged.jar")
            if (parts.size == 1) {
                Files.copy(parts[0], merged)
            } else {
                merge(parts, merged)
            }

            restoreHyphenatedNames(merged)
            Files.move(merged, target, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            runCatching { staging.toFile().deleteRecursively() }
        }
        return target
    }

    /** Every `classes*.dex` in the archive, or the archive itself when it is a bare dex file. */
    internal fun dexEntries(raw: ByteArray): List<ByteArray> {
        // A few older extensions are a plain .dex rather than a zip.
        if (raw.size > 8 && raw[0] == 'd'.code.toByte() && raw[1] == 'e'.code.toByte() &&
            raw[2] == 'x'.code.toByte() && raw[3] == '\n'.code.toByte()
        ) {
            return listOf(raw)
        }

        val found = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(raw)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (name.startsWith("classes") && name.endsWith(".dex")) {
                    found += name to zip.readBytes()
                }
                zip.closeEntry()
            }
        }

        // classes.dex, classes2.dex, classes3.dex … — the trailing index defines the order.
        return found.sortedBy { (name, _) ->
            name.removePrefix("classes").removeSuffix(".dex").toIntOrNull() ?: 1
        }.map { it.second }
    }

    /**
     * Restores the hyphenated names Kotlin uses for value-class members.
     *
     * `kotlin.Result` is a value class, so its members are called `constructor-impl`,
     * `isFailure-impl`, `exceptionOrNull-impl` … in the Kotlin runtime. dex2jar sanitises the
     * hyphen to an underscore while translating, which turns every extension that uses
     * `runCatching` into a `NoSuchMethodError` at runtime. Renaming the references back to
     * their real names is enough: the descriptors are unchanged, and only methods ending in
     * `_impl` are touched, so ordinary underscore names are left alone.
     */
    private fun restoreHyphenatedNames(jar: Path) {
        val rewritten = jar.resolveSibling("${jar.fileName}.rewritten")
        ZipFile(jar.toFile()).use { zip ->
            ZipOutputStream(Files.newOutputStream(rewritten)).use { out ->
                for (entry in zip.entries()) {
                    val bytes = zip.getInputStream(entry).readBytes()
                    val fixed = if (entry.name.endsWith(".class")) fixClass(bytes) else bytes
                    out.putNextEntry(ZipEntry(entry.name))
                    out.write(fixed)
                    out.closeEntry()
                }
            }
        }
        Files.move(rewritten, jar, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Exposed for the test that checks the `_impl` rename. */
    internal fun restoreValueClassNames(bytes: ByteArray): ByteArray = fixClass(bytes)

    private fun fixClass(bytes: ByteArray): ByteArray = runCatching {
        val writer = ClassWriter(0)
        ClassReader(bytes).accept(ClassRemapper(writer, ValueClassNameRemapper), 0)
        writer.toByteArray()
    }.getOrDefault(bytes)

    private object ValueClassNameRemapper : Remapper() {
        private const val SUFFIX = "_impl"

        override fun mapMethodName(owner: String, name: String, descriptor: String): String =
            if (name.endsWith(SUFFIX)) name.dropLast(SUFFIX.length) + "-impl" else name
    }

    private fun merge(parts: List<Path>, target: Path) {
        ZipOutputStream(Files.newOutputStream(target)).use { out ->
            val seen = HashSet<String>()
            for (part in parts) {
                ZipInputStream(Files.newInputStream(part)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        if (name.startsWith("META-INF/") || !seen.add(name)) {
                            zip.closeEntry()
                            continue
                        }
                        out.putNextEntry(ZipEntry(name))
                        zip.copyTo(out)
                        out.closeEntry()
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
