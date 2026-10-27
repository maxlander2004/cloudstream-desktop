package app.cloudstream.desktop.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.spi.InetAddressResolver
import java.net.spi.InetAddressResolverProvider
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

/**
 * DNS-over-HTTPS for the app's own name lookups, behind General → Network & Proxy.
 *
 * Every hostname the JVM resolves goes through [InetAddress], and the JDK lets a
 * `java.net.spi.InetAddressResolverProvider` stand in for the system resolver. That provider is
 * registered in `META-INF/services`, so switching the setting on covers the app's HTTP client,
 * downloads, image loading and anything an extension resolves — the same scope the original's
 * DoH switch has. No restart is needed: the resolver reads [provider] on every lookup.
 *
 * Every failure path falls back to the system resolver, so an unreachable DoH server, a
 * malformed answer or a name that is already an IP literal all behave exactly as before.
 */
object Doh {

    /** One of [Settings.dohProviders]' keys. Maintained by [Settings.doh]'s setter. */
    @Volatile
    var provider: String = "off"

    /** Resolvers' own hostnames, which must never be looked up through DoH (chicken and egg). */
    private val resolverHosts = setOf("cloudflare-dns.com", "dns.google")

    /** Set while this object is itself resolving, so a DoH lookup cannot recurse. */
    private val resolving = ThreadLocal.withInitial { false }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(4))
            .build()
    }

    private class CacheEntry(val expiresAt: Long, val addresses: List<InetAddress>)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /** JSON answer record; `type` 1 is IPv4 and 28 is IPv6, 5 a CNAME. */
    internal data class DohAnswer(val type: Int, val data: String, val ttl: Long)

    fun configure(settings: Settings) {
        provider = settings.doh
    }

    /** The JSON endpoint for a resolver key, or null when DoH is off. */
    fun endpoint(provider: String): String? = when (provider) {
        "cloudflare" -> "https://cloudflare-dns.com/dns-query"
        "google" -> "https://dns.google/resolve"
        else -> null
    }

    internal fun parse(body: String): List<DohAnswer> = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        // Status 0 is NOERROR; anything else (NXDOMAIN, SERVFAIL, blocked) is no answer.
        if ((root["Status"]?.jsonPrimitive?.intOrNull ?: 0) != 0) return emptyList()
        val answers = root["Answer"] as? JsonArray ?: return emptyList()
        answers.mapNotNull { element ->
            val record = element.jsonObject
            val type = record["type"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val data = record["data"]?.jsonPrimitive?.contentOrNull?.trim()
            if (data.isNullOrEmpty()) return@mapNotNull null
            DohAnswer(type, data, record["TTL"]?.jsonPrimitive?.longOrNull ?: 0L)
        }
    }.getOrDefault(emptyList())

    /** Address records of the requested families, in answer order. */
    internal fun addresses(
        answers: List<DohAnswer>,
        ipv4: Boolean,
        ipv6: Boolean,
    ): List<InetAddress> = answers.mapNotNull { answer ->
        val wanted = when {
            answer.type == 1 && ipv4 && !answer.data.contains(':') -> true
            answer.type == 28 && ipv6 && answer.data.contains(':') -> true
            else -> false
        }
        if (!wanted) return@mapNotNull null
        runCatching { InetAddress.getByName(answer.data) }.getOrNull()
    }.distinct()

    internal fun isIpLiteral(host: String): Boolean =
        host.contains(':') || host.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}"""))

    /**
     * Resolves [host] through DoH, or returns null when DoH is off, the name is an IP literal,
     * the resolver failed, or this thread is already inside a DoH lookup. Callers then use the
     * system resolver.
     */
    fun lookup(host: String, ipv4: Boolean, ipv6: Boolean): List<InetAddress>? {
        val selected = provider
        val endpoint = endpoint(selected) ?: return null
        if (host.isBlank() || resolving.get()) return null
        if (host.lowercase() in resolverHosts || isIpLiteral(host)) return null

        val key = "$selected|$host"
        cache[key]?.let { entry ->
            if (entry.expiresAt > System.currentTimeMillis()) {
                val matching = entry.addresses.filter { address ->
                    (ipv4 && !address.isIpv6()) || (ipv6 && address.isIpv6())
                }
                if (matching.isNotEmpty()) return matching
            } else {
                cache.remove(key, entry)
            }
        }

        resolving.set(true)
        try {
            val found = mutableListOf<InetAddress>()
            var ttl = Long.MAX_VALUE
            if (ipv4) {
                val (records, recordTtl) = query(endpoint, host, "A")
                found += records
                if (records.isNotEmpty() && recordTtl > 0) ttl = minOf(ttl, recordTtl)
            }
            // Only ask for IPv6 when IPv4 gave nothing: most networks cannot reach it anyway,
            // and one fewer request keeps the first lookup fast.
            if (ipv6 && found.isEmpty()) {
                val (records, recordTtl) = query(endpoint, host, "AAAA")
                found += records
                if (records.isNotEmpty() && recordTtl > 0) ttl = minOf(ttl, recordTtl)
            }
            if (found.isEmpty()) return null
            val lifetime = ttl.coerceIn(30L, 3600L)
            cache[key] = CacheEntry(System.currentTimeMillis() + lifetime * 1000, found)
            return found
        } catch (_: Throwable) {
            return null
        } finally {
            resolving.set(false)
        }
    }

    private fun query(endpoint: String, host: String, type: String): Pair<List<InetAddress>, Long> {
        val name = URLEncoder.encode(host, StandardCharsets.UTF_8)
        val request = HttpRequest.newBuilder(URI.create("$endpoint?name=$name&type=$type"))
            .timeout(Duration.ofSeconds(6))
            .header("Accept", "application/dns-json")
            .header("User-Agent", "CloudStreamDesktop/1.1")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) return emptyList<InetAddress>() to 0L
        val answers = parse(response.body())
        val records = addresses(answers, ipv4 = type == "A", ipv6 = type == "AAAA")
        val ttl = answers
            .filter { it.type == 1 || it.type == 28 }
            .minOfOrNull { it.ttl }
            ?: 0L
        return records to ttl
    }

    private fun InetAddress.isIpv6() = this is Inet6Address
}

/**
 * The JVM's hook into name resolution: delegates to [Doh] and falls back to the platform
 * resolver whenever DoH is off or has nothing to say.
 */
class DohResolver(private val builtin: InetAddressResolver) : InetAddressResolver {

    override fun lookupByName(host: String, lookupPolicy: InetAddressResolver.LookupPolicy): Stream<InetAddress> {
        // LookupPolicy exposes a bitmask of the requested families rather than getters.
        val characteristics = lookupPolicy.characteristics()
        val wantsV4 = (characteristics and InetAddressResolver.LookupPolicy.IPV4) != 0
        val wantsV6 = (characteristics and InetAddressResolver.LookupPolicy.IPV6) != 0
        // No family bit set means "whatever is available".
        val ipv4 = wantsV4 || !wantsV6
        val ipv6 = wantsV6 || !wantsV4
        val addresses = runCatching {
            Doh.lookup(host, ipv4, ipv6)
        }.getOrNull()
        return if (addresses.isNullOrEmpty()) {
            builtin.lookupByName(host, lookupPolicy)
        } else {
            addresses.stream()
        }
    }

    override fun lookupByAddress(addr: ByteArray): String = builtin.lookupByAddress(addr)
}

/** Registered in `META-INF/services/java.net.spi.InetAddressResolverProvider`. */
class DohResolverProvider : InetAddressResolverProvider() {

    override fun get(configuration: InetAddressResolverProvider.Configuration): InetAddressResolver =
        DohResolver(configuration.builtinResolver())

    override fun name(): String = "CloudStream DNS-over-HTTPS"
}
