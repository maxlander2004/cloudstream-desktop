package android.util

/**
 * Android's `android.util.Base64`, as far as extension bytecode links against it. Wraps the JDK's
 * Base64 so extensions that use the Android API directly can run on the JVMs that host desktop
 * CloudStream without depending on `java.util.Base64`'s class layout.
 */
object Base64 {
    const val DEFAULT = 0
    const val NO_WRAP = 16
    const val CRLF = 8
    const val URL_SAFE = 256
    const val NO_PADDING = 32
    const val FORMAT_RAW = 4
    const val MIME = NO_WRAP or CRLF

    private fun encoderFor(flags: Int): java.util.Base64.Encoder {
        val urlSafe = flags and URL_SAFE != 0
        val noPadding = flags and NO_PADDING != 0
        var e = if (urlSafe) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (noPadding) e = e.withoutPadding()
        return e
    }

    private fun decoderFor(flags: Int): java.util.Base64.Decoder {
        val urlSafe = flags and URL_SAFE != 0
        val mime = flags and (NO_WRAP or CRLF) == MIME
        return if (urlSafe) java.util.Base64.getUrlDecoder()
            else if (mime) java.util.Base64.getMimeDecoder()
            else java.util.Base64.getDecoder()
    }

    @JvmStatic
    fun encode(input: ByteArray): String = java.util.Base64.getEncoder().encodeToString(input)

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String = encoderFor(flags).encodeToString(input)

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int, maxChunkSize: Int): String {
        val encoder = encoderFor(flags)
        val chunkSize = if (maxChunkSize <= 0) Int.MAX_VALUE else maxChunkSize
        val lines = mutableListOf<String>()
        var offset = 0
        while (offset < input.size) {
            val end = minOf(offset + chunkSize, input.size)
            val chunk = ByteArray(end - offset)
            input.copyOfRange(offset, end).copyInto(chunk)
            lines.add(encoder.encodeToString(chunk))
            offset = end
        }
        return lines.joinToString("")
    }

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int, offset: Int, length: Int): String {
        val safeLength = minOf(length, input.size - offset)
        val chunk = ByteArray(safeLength)
        input.copyOfRange(offset, offset + safeLength).copyInto(chunk)
        return encoderFor(flags).encodeToString(chunk)
    }

    @JvmStatic
    fun getEncoder(): android.util.Base64.Encoder = Encoder(java.util.Base64.getEncoder())

    @JvmStatic
    fun decode(input: String): ByteArray = java.util.Base64.getDecoder().decode(input)

    @JvmStatic
    fun decode(input: String, flags: Int): ByteArray = decode(input, 0, input.length, flags)

    @JvmStatic
    fun decode(input: String, offset: Int, length: Int, flags: Int): ByteArray {
        val safeLength = minOf(length, input.length - offset)
        val chunk = input.substring(offset, offset + safeLength)
        return decoderFor(flags).decode(chunk)
    }

    @JvmStatic
    fun decode(input: ByteArray, flags: Int): ByteArray = decoderFor(flags).decode(input)

    @JvmStatic
    fun decode(input: ByteArray, offset: Int, length: Int, flags: Int): ByteArray {
        val safeLength = minOf(length, input.size - offset)
        val chunk = ByteArray(safeLength)
        input.copyOfRange(offset, offset + safeLength).copyInto(chunk)
        return decoderFor(flags).decode(chunk)
    }

    @JvmStatic
    fun decodeAllowed(input: String, flags: Int): Boolean {
        return try {
            decode(input, flags)
            true
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: java.nio.charset.MalformedInputException) {
            false
        }
    }

    @JvmStatic
    fun isBase64(input: String, flags: Int): Boolean = decodeAllowed(input, flags)

    @JvmStatic
    fun getDecoder(): android.util.Base64.Decoder = Decoder(java.util.Base64.getDecoder())

    @JvmStatic
    fun getMimeDecoder(): android.util.Base64.Decoder = Decoder(java.util.Base64.getMimeDecoder())

    @JvmStatic
    fun getUrlEncoder(): android.util.Base64.Encoder = Encoder(java.util.Base64.getUrlEncoder())

    @JvmStatic
    fun getUrlDecoder(): android.util.Base64.Decoder = Decoder(java.util.Base64.getUrlDecoder())

    @JvmStatic
    fun encodeTobyteArray(chars: CharSequence): ByteArray =
        java.util.Base64.getEncoder().encode(java.lang.String.valueOf(chars).toByteArray(java.nio.charset.StandardCharsets.UTF_8))

    @JvmStatic
    fun encodeTobyteArray(chars: CharSequence, flags: Int): ByteArray = encodeTobyteArray(chars)

    class Encoder internal constructor(private val delegate: java.util.Base64.Encoder) {
        fun encode(input: ByteArray): String = delegate.encodeToString(input)
        fun encodeToString(input: ByteArray): String = delegate.encodeToString(input)
        fun encodeToString(input: ByteArray, maxChunkSize: Int): String {
            val chunkSize = if (maxChunkSize <= 0) Int.MAX_VALUE else maxChunkSize
            val lines = mutableListOf<String>()
            var offset = 0
            while (offset < input.size) {
                val end = minOf(offset + chunkSize, input.size)
                val chunk = ByteArray(end - offset)
                input.copyOfRange(offset, end).copyInto(chunk)
                lines.add(delegate.encodeToString(chunk))
                offset = end
            }
            return lines.joinToString("")
        }
        fun encodeToString(input: ByteArray, offset: Int, length: Int): String {
            val safeLength = minOf(length, input.size - offset)
            val chunk = ByteArray(safeLength)
            input.copyOfRange(offset, offset + safeLength).copyInto(chunk)
            return delegate.encodeToString(chunk)
        }
        fun withoutPadding(): Encoder = Encoder(delegate.withoutPadding())
    }

    class Decoder internal constructor(private val delegate: java.util.Base64.Decoder) {
        fun decode(input: String): ByteArray = delegate.decode(input)
        fun decode(input: ByteArray): ByteArray = delegate.decode(input)
        fun decode(input: ByteArray, offset: Int, length: Int): ByteArray {
            val safeLength = minOf(length, input.size - offset)
            val chunk = ByteArray(safeLength)
            input.copyOfRange(offset, offset + safeLength).copyInto(chunk)
            return delegate.decode(chunk)
        }
    }
}
