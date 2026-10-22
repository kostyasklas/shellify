package io.shellify.app.core.deeplink

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.shellify.app.util.JavaUrlSafeBase64Codec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkHandlerTest {

    private val codec = JavaUrlSafeBase64Codec

    // ── encodeUrl / decodeUrl ─────────────────────────────────────────────────

    @Test
    fun `encodeUrl produces base64url-encoded string`() {
        val encoded = DeepLinkHandler.encodeUrl("https://example.com", codec)
        // Verify by decoding
        val decoded = DeepLinkHandler.decodeUrl(encoded, codec)
        assertEquals("https://example.com", decoded)
    }

    @Test
    fun `decodeUrl returns https url when valid`() {
        val encoded = DeepLinkHandler.encodeUrl("https://example.com/app", codec)
        assertEquals("https://example.com/app", DeepLinkHandler.decodeUrl(encoded, codec))
    }

    @Test
    fun `decodeUrl returns http url when valid`() {
        val encoded = DeepLinkHandler.encodeUrl("http://example.com", codec)
        assertEquals("http://example.com", DeepLinkHandler.decodeUrl(encoded, codec))
    }

    @Test
    fun `decodeUrl returns null for non-web schemes`() {
        val urls = listOf(
            "file:///etc/passwd",
            "javascript:alert(1)",
            "ftp://example.com/file",
            "data:text/html,test",
        )
        for (url in urls) {
            val encoded = DeepLinkHandler.encodeUrl(url, codec)
            assertNull(url, DeepLinkHandler.decodeUrl(encoded, codec))
        }
    }

    @Test
    fun `decodeUrl returns null for empty or invalid hosts`() {
        val urls = listOf(
            "http://",
            "https://",
            "http:///path",
            "https:///path",
            "http://?q=value",
            "https://#fragment",
            "http://:8080/path",
            "https://user@:443/path",
            "http://exa mple.com/path",
            "https://   /path",
            "http://[invalid]/path",
        )
        for (url in urls) {
            val encoded = DeepLinkHandler.encodeUrl(url, codec)
            assertNull(url, DeepLinkHandler.decodeUrl(encoded, codec))
        }
    }

    @Test
    fun `decodeUrl requires an explicit web scheme`() {
        val urls = listOf(
            "example.com",
            "//example.com",
            "http:example.com",
            "https:/example.com",
            " https://example.com",
        )
        for (url in urls) {
            val encoded = DeepLinkHandler.encodeUrl(url, codec)
            assertNull(url, DeepLinkHandler.decodeUrl(encoded, codec))
        }
    }

    @Test
    fun `decodeUrl preserves uppercase and mixed case web schemes`() {
        val urls = listOf(
            "HTTP://Example.COM/Path?q=hello world&lang=fr#Section",
            "HTTPS://Example.COM/Path?q=hello world&lang=fr#Section",
            "HtTp://example.com/app",
            "HtTpS://example.com/app",
        )
        for (url in urls) {
            val encoded = DeepLinkHandler.encodeUrl(url, codec)
            assertEquals(url, DeepLinkHandler.decodeUrl(encoded, codec))
        }
    }

    @Test
    fun `decodeUrl accepts local http urls with ports`() {
        val urls = listOf(
            "http://localhost:8080/app?q=hello world",
            "http://127.0.0.1:3000/app",
            "http://[::1]:8080/app",
        )
        for (url in urls) {
            val encoded = DeepLinkHandler.encodeUrl(url, codec)
            assertEquals(url, DeepLinkHandler.decodeUrl(encoded, codec))
        }
    }

    @Test
    fun `decodeUrl returns null for invalid base64`() {
        assertNull(DeepLinkHandler.decodeUrl("!!!not-base64!!!", codec))
    }

    @Test
    fun `decodeUrl returns null for empty string`() {
        assertNull(DeepLinkHandler.decodeUrl("", codec))
    }

    @Test
    fun `encodeUrl handles special characters in url`() {
        val url = "https://example.com/path?q=hello world&lang=fr"
        val encoded = DeepLinkHandler.encodeUrl(url, codec)
        val decoded = DeepLinkHandler.decodeUrl(encoded, codec)
        assertEquals(url, decoded)
    }

    @Test
    fun `encodeUrl and decodeUrl are inverse operations`() {
        val urls = listOf(
            "https://app.example.com",
            "https://sub.domain.io/path/to/app?tab=home",
            "https://example.com/app#section",
            "http://example.com/path?q=hello world&lang=fr#section",
        )
        for (url in urls) {
            assertEquals(
                url,
                DeepLinkHandler.decodeUrl(DeepLinkHandler.encodeUrl(url, codec), codec)
            )
        }
    }

    // ── parse() ───────────────────────────────────────────────────────────────

    private fun mockUri(
        scheme: String,
        host: String,
        path: String? = null,
        urlParam: String? = null,
        nameParam: String? = null,
    ): Uri = mockk<Uri>().also {
        every { it.scheme } returns scheme
        every { it.host } returns host
        every { it.path } returns path
        every { it.getQueryParameter("url") } returns urlParam
        every { it.getQueryParameter("name") } returns nameParam
    }

    @Test
    fun `parse returns url and name for custom shellify scheme`() {
        val encodedUrl = DeepLinkHandler.encodeUrl("https://example.com", codec)
        val uri = mockUri("shellify", "add", urlParam = encodedUrl, nameParam = "My App")
        val result = DeepLinkHandler.parse(uri, codec)
        assertEquals("https://example.com" to "My App", result)
    }

    @Test
    fun `parse returns empty name when name param is missing`() {
        val encodedUrl = DeepLinkHandler.encodeUrl("https://example.com", codec)
        val uri = mockUri("shellify", "add", urlParam = encodedUrl, nameParam = null)
        val result = DeepLinkHandler.parse(uri, codec)
        assertEquals("https://example.com" to "", result)
    }

    @Test
    fun `parse returns null for wrong scheme`() {
        val encodedUrl = DeepLinkHandler.encodeUrl("https://example.com", codec)
        val uri = mockUri("ftp", "add", urlParam = encodedUrl, nameParam = "App")
        assertNull(DeepLinkHandler.parse(uri, codec))
    }

    @Test
    fun `parse returns null for wrong host on custom scheme`() {
        val encodedUrl = DeepLinkHandler.encodeUrl("https://example.com", codec)
        val uri = mockUri("shellify", "wrong-host", urlParam = encodedUrl, nameParam = "App")
        assertNull(DeepLinkHandler.parse(uri, codec))
    }

    @Test
    fun `parse returns null when url param is missing`() {
        val uri = mockUri("shellify", "add", urlParam = null, nameParam = "App")
        assertNull(DeepLinkHandler.parse(uri, codec))
    }

    @Test
    fun `parse returns null when url param is blank`() {
        val uri = mockUri("shellify", "add", urlParam = "   ", nameParam = "App")
        assertNull(DeepLinkHandler.parse(uri, codec))
    }

    @Test
    fun `parse accepts decoded http url`() {
        val encodedHttp = DeepLinkHandler.encodeUrl("http://example.com", codec)
        val uri = mockUri("shellify", "add", urlParam = encodedHttp, nameParam = "App")
        assertEquals("http://example.com" to "App", DeepLinkHandler.parse(uri, codec))
    }

    @Test
    fun `parse rejects non-web urls and empty hosts`() {
        val urls = listOf("file:///tmp/app", "javascript:alert(1)", "ftp://example.com", "http://", "https:///path")
        for (url in urls) {
            val encodedUrl = DeepLinkHandler.encodeUrl(url, codec)
            val uri = mockUri("shellify", "add", urlParam = encodedUrl, nameParam = "App")
            assertNull(url, DeepLinkHandler.parse(uri, codec))
        }
    }

    @Test
    fun `parse accepts https shellify app host`() {
        val encodedUrl = DeepLinkHandler.encodeUrl("https://example.com", codec)
        val uri = mockUri(
            "https",
            "shellify.app",
            path = "/add",
            urlParam = encodedUrl,
            nameParam = "App"
        )
        val result = DeepLinkHandler.parse(uri, codec)
        assertEquals("https://example.com" to "App", result)
    }

    @Test
    fun `parse accepts http payload through https shellify app link`() {
        val url = "HTTP://localhost:8080/app?q=hello world&lang=fr#section"
        val encodedUrl = DeepLinkHandler.encodeUrl(url, codec)
        val uri = mockUri("https", "shellify.app", path = "/add", urlParam = encodedUrl, nameParam = "Local App")
        assertEquals(url to "Local App", DeepLinkHandler.parse(uri, codec))
    }

    @Test
    fun `parse returns null for http shellify app link`() {
        val encodedUrl = DeepLinkHandler.encodeUrl("http://example.com", codec)
        val uri = mockUri("http", "shellify.app", path = "/add", urlParam = encodedUrl, nameParam = "App")
        assertNull(DeepLinkHandler.parse(uri, codec))
    }

    @Test
    fun `parse returns null for https scheme but wrong host`() {
        val encodedUrl = DeepLinkHandler.encodeUrl("https://example.com", codec)
        val uri =
            mockUri("https", "wrong.host", path = "/add", urlParam = encodedUrl, nameParam = "App")
        assertNull(DeepLinkHandler.parse(uri, codec))
    }

    // ── parseOpen() ───────────────────────────────────────────────────────────

    @Test
    fun `parseOpen returns decoded url for custom shellify open scheme`() {
        val encodedUrl = DeepLinkHandler.encodeUrl("https://example.com/app", codec)
        val uri = mockUri("shellify", "open", urlParam = encodedUrl)
        assertEquals("https://example.com/app", DeepLinkHandler.parseOpen(uri, codec))
    }

    @Test
    fun `parseOpen returns decoded url for https shellify app open path`() {
        val encodedUrl = DeepLinkHandler.encodeUrl("https://example.com", codec)
        val uri = mockUri("https", "shellify.app", path = "/open", urlParam = encodedUrl)
        assertEquals("https://example.com", DeepLinkHandler.parseOpen(uri, codec))
    }

    @Test
    fun `parseOpen accepts decoded http url`() {
        val encodedHttp = DeepLinkHandler.encodeUrl("http://example.com", codec)
        val uri = mockUri("shellify", "open", urlParam = encodedHttp)
        assertEquals("http://example.com", DeepLinkHandler.parseOpen(uri, codec))
    }

    @Test
    fun `parseOpen accepts http payload through https shellify app link`() {
        val url = "HTTP://localhost:8080/app?q=hello world&lang=fr#section"
        val encodedUrl = DeepLinkHandler.encodeUrl(url, codec)
        val uri = mockUri("https", "shellify.app", path = "/open", urlParam = encodedUrl)
        assertEquals(url, DeepLinkHandler.parseOpen(uri, codec))
    }

    @Test
    fun `parseOpen returns null for http shellify app link`() {
        val encodedUrl = DeepLinkHandler.encodeUrl("http://example.com", codec)
        val uri = mockUri("http", "shellify.app", path = "/open", urlParam = encodedUrl)
        assertNull(DeepLinkHandler.parseOpen(uri, codec))
    }

    @Test
    fun `parseOpen rejects non-web urls and empty hosts`() {
        val urls = listOf("file:///tmp/app", "javascript:alert(1)", "ftp://example.com", "http://", "https:///path")
        for (url in urls) {
            val encodedUrl = DeepLinkHandler.encodeUrl(url, codec)
            val uri = mockUri("shellify", "open", urlParam = encodedUrl)
            assertNull(url, DeepLinkHandler.parseOpen(uri, codec))
        }
    }

    @Test
    fun `parseOpen returns null for wrong host on shellify scheme`() {
        val encodedUrl = DeepLinkHandler.encodeUrl("https://example.com", codec)
        val uri = mockUri("shellify", "add", urlParam = encodedUrl)
        assertNull(DeepLinkHandler.parseOpen(uri, codec))
    }

    @Test
    fun `parseOpen returns null when url param is missing`() {
        val uri = mockUri("shellify", "open", urlParam = null)
        assertNull(DeepLinkHandler.parseOpen(uri, codec))
    }

    // ── buildOpen() ───────────────────────────────────────────────────────────
    // Uri.Builder is an Android platform class and cannot be tested in JVM unit tests.
    // The build functions are verified via encodeUrl/decodeUrl round-trip assertions instead.

    @Test
    fun `buildOpen encoded url can be decoded back to original url`() {
        val originalUrl = "https://github.com"
        val encodedParam = DeepLinkHandler.encodeUrl(originalUrl, codec)
        val decoded = DeepLinkHandler.decodeUrl(encodedParam, codec)
        assertEquals(originalUrl, decoded)
    }

    @Test
    fun `buildOpen encoded http url can be decoded back to original url`() {
        val httpUrl = "http://example.com"
        val encodedParam = DeepLinkHandler.encodeUrl(httpUrl, codec)
        val decoded = DeepLinkHandler.decodeUrl(encodedParam, codec)
        assertEquals(httpUrl, decoded)
    }
}
