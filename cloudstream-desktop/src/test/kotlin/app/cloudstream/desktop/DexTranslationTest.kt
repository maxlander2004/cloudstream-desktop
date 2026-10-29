package app.cloudstream.desktop

import app.cloudstream.desktop.core.legacy.DexTranslator
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DexTranslationTest {
    /** A method with the JVM name dex2jar gives Kotlin's `constructor-impl`. */
    @Suppress("unused")
    fun constructor_impl(value: Any?): Any? = value

    /**
     * dex2jar sanitises the hyphen in Kotlin value-class members (`constructor-impl` becomes
     * `constructor_impl`). The Kotlin runtime only has the hyphenated name, so an extension
     * that uses `runCatching` failed with `NoSuchMethodError` until the translator renamed
     * those references back.
     */
    @Test
    fun `value class members get their hyphen back`() {
        val bytes = javaClass.getResourceAsStream("DexTranslationTest.class")!!.readBytes()
        assertTrue(declaredMethods(bytes).contains("constructor_impl"))

        val fixed = DexTranslator.restoreValueClassNames(bytes)
        val methods = declaredMethods(fixed)

        assertTrue(methods.contains("constructor-impl"), "the hyphenated name is missing")
        assertFalse(methods.contains("constructor_impl"), "the sanitised name survived")
    }

    /** The method names the class file declares, read with ASM. */
    private fun declaredMethods(bytes: ByteArray): Set<String> {
        val names = mutableSetOf<String>()
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    names += name
                    return null
                }
            },
            ClassReader.SKIP_CODE,
        )
        return names
    }
}
