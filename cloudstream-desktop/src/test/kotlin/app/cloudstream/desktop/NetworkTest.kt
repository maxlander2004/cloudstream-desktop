package app.cloudstream.desktop

import app.cloudstream.desktop.core.Doh
import app.cloudstream.desktop.core.DohResolverProvider
import app.cloudstream.desktop.core.Network
import java.net.spi.InetAddressResolverProvider
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** General → Network & Proxy: the GitHub proxy and the clone-site bypass. */
class NetworkRewriteTest {

    @Test
    fun `github proxy rewrites raw files through jsDelivr`() {
        assertEquals(
            "https://cdn.jsdelivr.net/gh/recloudstream/extensions@master/repo.json",
            Network.rewrite(
                "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
                githubProxy = true,
            ),
        )
        assertEquals(
            "https://cdn.jsdelivr.net/gh/owner/repo@main/dir/file.json",
            Network.rewrite(
                "https://raw.githubusercontent.com/owner/repo/main/dir/file.json",
                githubProxy = true,
            ),
        )
    }

    @Test
    fun `github proxy leaves other hosts and unusable paths alone`() {
        assertEquals(
            "https://github.com/recloudstream/extensions",
            Network.rewrite("https://github.com/recloudstream/extensions", githubProxy = true),
        )
        // No branch or path to rewrite.
        assertEquals(
            "https://raw.githubusercontent.com/owner/repo",
            Network.rewrite("https://raw.githubusercontent.com/owner/repo", githubProxy = true),
        )
        // Off by default.
        assertEquals(
            "https://raw.githubusercontent.com/owner/repo/main/a.json",
            Network.rewrite("https://raw.githubusercontent.com/owner/repo/main/a.json"),
        )
    }

    @Test
    fun `clone site bypass replaces the longest matching prefix`() {
        val rules = """
            https://old.example=https://new.example
            https://old.example/special/=https://special.example/
        """.trimIndent()

        assertEquals(
            "https://special.example/watch/1",
            Network.rewrite("https://old.example/special/watch/1", rules),
        )
        assertEquals(
            "https://new.example/watch/1",
            Network.rewrite("https://old.example/watch/1", rules),
        )
        assertEquals(
            "https://other.example/x",
            Network.rewrite("https://other.example/x", rules),
        )
    }

    @Test
    fun `clone site ignores malformed entries`() {
        val url = "https://a.example/x"
        assertEquals(url, Network.rewrite(url, "nonsense"))
        assertEquals(url, Network.rewrite(url, "=https://b.example"))
        assertEquals(url, Network.rewrite(url, "https://a.example="))
    }

    @Test
    fun `both rules apply to the same request`() {
        assertEquals(
            "https://mirror.example/gh/recloudstream/extensions@master/repo.json",
            Network.rewrite(
                "https://cdn.jsdelivr.net/gh/recloudstream/extensions@master/repo.json",
                cloneSite = "https://cdn.jsdelivr.net=https://mirror.example",
            ),
        )
    }
}

/** The DNS-over-HTTPS setting and the resolver it installs. */
class DohTest {

    private val body = """
        {"Status":0,"Answer":[
          {"name":"example.com.","type":5,"TTL":60,"data":"cdn.example.net."},
          {"name":"cdn.example.net.","type":1,"TTL":120,"data":"93.184.216.34"},
          {"name":"cdn.example.net.","type":28,"TTL":90,"data":"2606:2800:220:1:248:1893:25c8:1946"}
        ]}
    """.trimIndent()

    @Test
    fun `parses address answers and drops cnames`() {
        val answers = Doh.parse(body)
        assertEquals(3, answers.size)

        val v4 = Doh.addresses(answers, ipv4 = true, ipv6 = false)
        assertEquals(listOf("93.184.216.34"), v4.map { it.hostAddress })

        val v6 = Doh.addresses(answers, ipv4 = false, ipv6 = true)
        assertEquals(1, v6.size)
        assertTrue(v6.single().hostAddress.contains(':'))
    }

    @Test
    fun `error statuses and non-json bodies yield nothing`() {
        assertTrue(Doh.parse("""{"Status":3,"Answer":[]}""").isEmpty())
        assertTrue(Doh.parse("""{"Status":0}""").isEmpty())
        assertTrue(Doh.parse("<html>blocked</html>").isEmpty())
        assertTrue(Doh.parse("").isEmpty())
        assertTrue(Doh.addresses(Doh.parse(body), ipv4 = false, ipv6 = false).isEmpty())
    }

    @Test
    fun `known resolvers map to endpoints and off does not`() {
        assertEquals("https://cloudflare-dns.com/dns-query", Doh.endpoint("cloudflare"))
        assertEquals("https://dns.google/resolve", Doh.endpoint("google"))
        assertNull(Doh.endpoint("off"))
        assertNull(Doh.endpoint(""))
    }

    @Test
    fun `lookup is inert when the setting is off`() {
        val previous = Doh.provider
        Doh.provider = "off"
        try {
            assertNull(Doh.lookup("example.com", ipv4 = true, ipv6 = true))
        } finally {
            Doh.provider = previous
        }
    }

    @Test
    fun `lookup skips ip literals and the resolver's own hostnames`() {
        val previous = Doh.provider
        Doh.provider = "cloudflare"
        try {
            assertNull(Doh.lookup("1.1.1.1", ipv4 = true, ipv6 = false))
            assertNull(Doh.lookup("2606:4700:4700::1111", ipv4 = false, ipv6 = true))
            assertNull(Doh.lookup("cloudflare-dns.com", ipv4 = true, ipv6 = false))
            assertNull(Doh.lookup("dns.google", ipv4 = true, ipv6 = false))
        } finally {
            Doh.provider = previous
        }
    }

    @Test
    fun `the resolver is registered with the JVM`() {
        val providers = ServiceLoader.load(InetAddressResolverProvider::class.java)
            .filterIsInstance<DohResolverProvider>()
            .toList()
        assertEquals(1, providers.size)
        assertEquals("CloudStream DNS-over-HTTPS", providers.single().name())
    }
}
