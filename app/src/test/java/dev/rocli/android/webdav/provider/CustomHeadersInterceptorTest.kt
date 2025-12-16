package dev.rocli.android.webdav.provider

import dev.rocli.android.webdav.data.CustomHeader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CustomHeadersInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `interceptor adds all enabled headers to request`() {
        val headers = listOf(
            CustomHeader("CF-Access-Client-Id", "client-id-value", isSecret = true, enabled = true),
            CustomHeader("CF-Access-Client-Secret", "client-secret-value", isSecret = true, enabled = true),
            CustomHeader("X-Custom-Header", "custom-value", isSecret = false, enabled = true)
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(CustomHeadersInterceptor(headers))
            .build()

        server.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(server.url("/test"))
            .build()

        client.newCall(request).execute()

        val recordedRequest = server.takeRequest()
        assertEquals("client-id-value", recordedRequest.getHeader("CF-Access-Client-Id"))
        assertEquals("client-secret-value", recordedRequest.getHeader("CF-Access-Client-Secret"))
        assertEquals("custom-value", recordedRequest.getHeader("X-Custom-Header"))
    }

    @Test
    fun `interceptor does not add disabled headers`() {
        val headers = listOf(
            CustomHeader("Enabled-Header", "enabled-value", enabled = true),
            CustomHeader("Disabled-Header", "disabled-value", enabled = false)
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(CustomHeadersInterceptor(headers))
            .build()

        server.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(server.url("/test"))
            .build()

        client.newCall(request).execute()

        val recordedRequest = server.takeRequest()
        assertEquals("enabled-value", recordedRequest.getHeader("Enabled-Header"))
        assertNull(recordedRequest.getHeader("Disabled-Header"))
    }

    @Test
    fun `interceptor does not add headers with blank name or value`() {
        val headers = listOf(
            CustomHeader("", "value", enabled = true),
            CustomHeader("Header", "", enabled = true),
            CustomHeader("Valid-Header", "valid-value", enabled = true)
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(CustomHeadersInterceptor(headers))
            .build()

        server.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(server.url("/test"))
            .build()

        client.newCall(request).execute()

        val recordedRequest = server.takeRequest()
        assertEquals("valid-value", recordedRequest.getHeader("Valid-Header"))
    }

    @Test
    fun `interceptor with empty list does not modify request`() {
        val client = OkHttpClient.Builder()
            .addInterceptor(CustomHeadersInterceptor(emptyList()))
            .build()

        server.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(server.url("/test"))
            .header("Existing-Header", "existing-value")
            .build()

        client.newCall(request).execute()

        val recordedRequest = server.takeRequest()
        assertEquals("existing-value", recordedRequest.getHeader("Existing-Header"))
    }

    @Test
    fun `interceptor overwrites existing headers with same name`() {
        val headers = listOf(
            CustomHeader("Authorization", "custom-auth", enabled = true)
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(CustomHeadersInterceptor(headers))
            .build()

        server.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(server.url("/test"))
            .header("Authorization", "original-auth")
            .build()

        client.newCall(request).execute()

        val recordedRequest = server.takeRequest()
        assertEquals("custom-auth", recordedRequest.getHeader("Authorization"))
    }
}
