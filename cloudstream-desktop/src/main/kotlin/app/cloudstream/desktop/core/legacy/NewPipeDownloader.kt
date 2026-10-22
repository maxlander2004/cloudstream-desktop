package app.cloudstream.desktop.core.legacy

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/**
 * NewPipe is a static singleton, and upstream initialises it once in `CommonActivity` with
 * exactly this downloader. Extensions that resolve YouTube links through
 * `com.lagradost.cloudstream3.extractors.YoutubeExtractor` fail with "downloader is null"
 * until it happens, so the desktop host does the same during startup.
 *
 * Ported from the app module's `DownloaderTestImpl`; the only change is that it is created
 * lazily on the JVM instead of from an Activity's lifecycle.
 */
private object NewPipeDownloader : Downloader() {
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/116.0.0.0 Safari/537.36"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val method: String = request.httpMethod()
        val url: String = request.url()
        val dataToSend: ByteArray? = request.dataToSend()
        val body = dataToSend?.toRequestBody(null, 0, dataToSend.size)

        val builder = okhttp3.Request.Builder()
            .method(method, body)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        for ((name, values) in request.headers()) {
            when {
                values.size > 1 -> {
                    builder.removeHeader(name)
                    values.forEach { builder.addHeader(name, it) }
                }

                values.size == 1 -> builder.header(name, values[0])
            }
        }

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }

            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString(),
            )
        }
    }

    fun install() {
        NewPipe.init(this)
    }
}

/** Safe to call repeatedly; `NewPipe.init` just replaces the downloader reference. */
internal fun initNewPipe() = NewPipeDownloader.install()
