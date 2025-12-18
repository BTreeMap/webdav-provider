package org.joefang.webdav.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomHeaderTest {

    @Test
    fun `validateHeaderName returns null for valid header names`() {
        assertNull(CustomHeader.validateHeaderName("CF-Access-Client-Id"))
        assertNull(CustomHeader.validateHeaderName("Authorization"))
        assertNull(CustomHeader.validateHeaderName("X-Custom-Header"))
        assertNull(CustomHeader.validateHeaderName("Content-Type"))
        assertNull(CustomHeader.validateHeaderName("Accept"))
        assertNull(CustomHeader.validateHeaderName("X_Custom_Header"))
        assertNull(CustomHeader.validateHeaderName("header123"))
    }

    @Test
    fun `validateHeaderName returns error for blank header names`() {
        assertEquals("Header name is required", CustomHeader.validateHeaderName(""))
        assertEquals("Header name is required", CustomHeader.validateHeaderName("   "))
    }

    @Test
    fun `validateHeaderName returns error for invalid characters`() {
        assertEquals("Header name contains invalid characters", CustomHeader.validateHeaderName("Header Name"))
        assertEquals("Header name contains invalid characters", CustomHeader.validateHeaderName("Header:Value"))
        assertEquals("Header name contains invalid characters", CustomHeader.validateHeaderName("Header\tName"))
        assertEquals("Header name contains invalid characters", CustomHeader.validateHeaderName("Header\nName"))
    }

    @Test
    fun `isDangerousHeader identifies dangerous headers`() {
        assertTrue(CustomHeader.isDangerousHeader("Host"))
        assertTrue(CustomHeader.isDangerousHeader("Content-Length"))
        assertTrue(CustomHeader.isDangerousHeader("Content-Type"))
        assertTrue(CustomHeader.isDangerousHeader("Transfer-Encoding"))
        assertTrue(CustomHeader.isDangerousHeader("Connection"))
        assertTrue(CustomHeader.isDangerousHeader("Upgrade"))
        assertTrue(CustomHeader.isDangerousHeader("Authorization"))
        
        // Case insensitive
        assertTrue(CustomHeader.isDangerousHeader("host"))
        assertTrue(CustomHeader.isDangerousHeader("HOST"))
        assertTrue(CustomHeader.isDangerousHeader("content-length"))
    }

    @Test
    fun `isDangerousHeader returns false for safe headers`() {
        assertFalse(CustomHeader.isDangerousHeader("CF-Access-Client-Id"))
        assertFalse(CustomHeader.isDangerousHeader("CF-Access-Client-Secret"))
        assertFalse(CustomHeader.isDangerousHeader("X-Custom-Header"))
        assertFalse(CustomHeader.isDangerousHeader("Accept"))
        assertFalse(CustomHeader.isDangerousHeader("Accept-Language"))
    }

    @Test
    fun `CustomHeader data class properties work correctly`() {
        val header = CustomHeader(
            name = "CF-Access-Client-Id",
            value = "test-value",
            isSecret = true,
            enabled = true
        )
        
        assertEquals("CF-Access-Client-Id", header.name)
        assertEquals("test-value", header.value)
        assertTrue(header.isSecret)
        assertTrue(header.enabled)
    }

    @Test
    fun `CustomHeader default values are correct`() {
        val header = CustomHeader(name = "Test", value = "Value")
        
        assertFalse(header.isSecret)
        assertTrue(header.enabled)
    }
}
