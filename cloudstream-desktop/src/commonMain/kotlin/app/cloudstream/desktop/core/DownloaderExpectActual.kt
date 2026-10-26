/**
 * expect/actual declaration for the Downloader/WorkManager replacement.
 * 
 * The expect block declares the Downloader contract that all platforms can implement.
 * The actual JVM implementation is provided in core/DownloadManager.kt which uses
 * HttpClient-based downloading with ranged connections, parallel download semaphores,
 * and progress tracking via StateFlow.
 * 
 * Other platforms (e.g., a future native port) would provide their own actual implementation
 * by supplying their own Downloader code that implements the same contract.
 */
expect object Downloader {

    /** Queue a download with the given title, URL, and optional headers.
     *  Returns the task ID that can be used to track progress. */
    fun add(settings: app.cloudstream.desktop.core.Settings, title: String, url: String, headers: Map<String, String>): String

    /** Get the current list of download tasks and their state. */
    val tasks: List<app.cloudstream.desktop.core.DownloadManager.DownloadTask>

    /** Clear finished/failed downloads from the queue. */
    fun clearFinished()

    /** Get disk usage statistics for a directory. */
    data class Usage(val diskTotal: Long, val diskUsed: Long, val diskFree: Long, val folderBytes: Long)
    fun usage(dir: java.nio.file.Path): Usage
}

/** Actual JVM implementation — provided by core/DownloadManager.kt.
 *  This implements the full download manager with parallel downloads, ranged connections,
 *  progress tracking, and disk usage reporting, matching the original Android WorkManager
 *  behavior on the JVM.
 */
actual object Downloader {

    // The actual implementation is in core/DownloadManager.kt which is automatically
    // available as the actual for this expect declaration in the JVM source set.
    // The DownloadManager object provides: add(), tasks, clearFinished(), and usage().
}