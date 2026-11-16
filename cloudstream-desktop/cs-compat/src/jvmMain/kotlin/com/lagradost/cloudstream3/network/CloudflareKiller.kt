package com.lagradost.cloudstream3.network

import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.URI

/**
 * Upstream's `CloudflareKiller`, without the WebView half.
 *
 * On Android this interceptor opens a WebView when a site answers a Cloudflare challenge and
 * reuses the `cf_clearance` cookie the WebView solved. The desktop JVM has no browser engine
 * ([WebViewResolver]'s JVM implementation is a pass-through), so the challenge cannot be
 * solved here: cookies are still applied when the extension supplies them, and the original
 * response is returned untouched otherwise. Extensions that only install this interceptor keep
 * working against sites that do not challenge them.
 */
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"

        private val ERROR_CODES = listOf(403, 503)
        private val CLOUDFLARE_SERVERS = listOf("cloudflare-nginx", "cloudflare")

        fun parseCookieMap(cookie: String): Map<String, String> = cookie
            .split(";")
            .associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
    }

    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    /** Cookies for [url], in the header form extensions pass to their own requests. */
    fun getCookieHeaders(url: String): Headers {
        val cookies = runCatching { savedCookies[URI(url).host] }.getOrNull() ?: emptyMap()
        val builder = Headers.Builder()
        if (cookies.isNotEmpty()) builder.add("cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        WebViewResolver.webViewUserAgent?.let { builder.add("user-agent", it) }
        return builder.build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val cookies = savedCookies[request.url.host]
        val outgoing = if (cookies.isNullOrEmpty()) {
            request
        } else {
            request.newBuilder().apply {
                header("cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
            }.build()
        }

        val response = chain.proceed(outgoing)
        val cloudflare = response.header("Server") in CLOUDFLARE_SERVERS &&
            response.code in ERROR_CODES
        if (cloudflare) {
            // No WebView means no challenge to solve; hand the response back so the caller
            // sees the same 403/503 it would see on a device whose WebView failed too.
            println("D/$TAG: could not bypass cloudflare at ${request.url}")
        }
        return response
    }
}
