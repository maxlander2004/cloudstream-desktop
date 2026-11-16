package android.content

import android.net.Uri
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Android's `ContentResolver`, as far as extension bytecode links against it.
 *
 * Extensions use it to read or write a `content://` document (a downloaded subtitle, a saved
 * playlist). The desktop has no content providers, so only `file://` URIs resolve; everything
 * else returns null and the extension falls back the same way it does on a device without the
 * provider.
 */
open class ContentResolver {
    open fun openInputStream(uri: Uri): InputStream? = runCatching {
        FileInputStream(uri.value.removePrefix("file://"))
    }.getOrNull()

    open fun openOutputStream(uri: Uri): OutputStream? = runCatching {
        FileOutputStream(uri.value.removePrefix("file://"))
    }.getOrNull()

    open fun getType(uri: Uri): String? = null

    open fun insert(url: Uri, values: Any?): Uri? = null

    open fun delete(url: Uri, where: String?, selectionArgs: Array<String>?): Int = 0

    open fun update(url: Uri, values: Any?, where: String?, selectionArgs: Array<String>?): Int = 0

    /** Android's `ContentResolver.SCHEME_FILE` and friends. */
    companion object {
        const val SCHEME_FILE = "file"
        const val SCHEME_CONTENT = "content"
    }
}
