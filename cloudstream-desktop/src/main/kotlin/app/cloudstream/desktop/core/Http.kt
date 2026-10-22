package app.cloudstream.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Thin wrapper over the JDK HTTP client so extensions need no extra dependency. */
object Http {
    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CloudStreamDesktop/1.0"

    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    private val noRedirectClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    private fun request(url: String, timeout: Duration): HttpRequest =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .header("User-Agent", UA)
            .GET()
            .build()

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val response = client.send(request(url, Duration.ofSeconds(25)), HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()} for $url" }
        response.body()
    }

    suspend fun getBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val response = client.send(request(url, Duration.ofSeconds(60)), HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()} for $url" }
        response.body()
    }

    /**
     * Where a short link points, without following it — the redirect lookup upstream uses to
     * resolve repository shortcodes such as `megarepo`. Null when the link does not redirect.
     */
    suspend fun redirectTarget(url: String): String? = withContext(Dispatchers.IO) {
        val response = noRedirectClient.send(
            request(url, Duration.ofSeconds(15)),
            HttpResponse.BodyHandlers.discarding(),
        )
        if (response.statusCode() !in 300..399) return@withContext null
        response.headers().firstValue("location").orElse(null)?.trim()
    }
}
