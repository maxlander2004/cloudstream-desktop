package android.media

/**
 * Android's `MediaCodecInfo`, which extensions query for hardware decoder support.
 * The desktop player is VLC/libmpv, so no Android codec list exists; report none.
 */
open class MediaCodecInfo {
    open fun getName(): String = ""

    open fun getSupportedTypes(): Array<String> = emptyArray()

    open fun isEncoder(): Boolean = false

    open fun isSoftwareOnly(): Boolean = true

    open fun isHardwareAccelerated(): Boolean = false

    open class CodecCapabilities {
        open fun getSupportedTypes(): Array<String> = emptyArray()

        open fun isFormatSupported(format: android.media.MediaFormat?): Boolean = false
    }
}

/** Android's `MediaFormat`, carrying the mime type extensions set up. */
open class MediaFormat {
    private val values = HashMap<String?, Any?>()

    open fun setString(key: String?, value: String?) {
        values[key] = value
    }

    open fun getString(key: String?): String? = values[key] as? String

    open fun setInteger(key: String?, value: Int) {
        values[key] = value
    }

    open fun getInteger(key: String?): Int = values[key] as? Int ?: 0

    open fun containsKey(key: String?): Boolean = values.containsKey(key)

    companion object {
        const val MIMETYPE_VIDEO_AVC = "video/avc"
        const val MIMETYPE_VIDEO_HEVC = "video/hevc"
        const val MIMETYPE_AUDIO_AAC = "audio/mp4a-latm"
    }
}
