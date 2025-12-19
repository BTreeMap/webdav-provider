package org.joefang.webdav.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Paths

class WebDavProviderTest {

    @Test
    fun `parseDocumentId parses valid document ID correctly`() {
        val (accountId, path) = WebDavProvider.parseDocumentId("/1/documents/file.txt")
        
        assertEquals(1L, accountId)
        assertEquals(Paths.get("/documents/file.txt"), path)
    }

    @Test
    fun `parseDocumentId parses root path correctly`() {
        val (accountId, path) = WebDavProvider.parseDocumentId("/1/")
        
        assertEquals(1L, accountId)
        assertEquals(Paths.get("/"), path)
    }

    @Test
    fun `parseDocumentId parses nested path correctly`() {
        val (accountId, path) = WebDavProvider.parseDocumentId("/123/a/b/c/d/file.txt")
        
        assertEquals(123L, accountId)
        assertEquals(Paths.get("/a/b/c/d/file.txt"), path)
    }

    @Test
    fun `parseDocumentId handles large account ID`() {
        val (accountId, path) = WebDavProvider.parseDocumentId("/9223372036854775807/test")
        
        assertEquals(Long.MAX_VALUE, accountId)
        assertEquals(Paths.get("/test"), path)
    }

    @Test
    fun `parseDocumentId throws for empty string`() {
        try {
            WebDavProvider.parseDocumentId("")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must start with /") == true)
        }
    }

    @Test
    fun `parseDocumentId throws for missing leading slash`() {
        try {
            WebDavProvider.parseDocumentId("1/documents/file.txt")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must start with /") == true)
        }
    }

    @Test
    fun `parseDocumentId throws for invalid account ID with letters`() {
        try {
            WebDavProvider.parseDocumentId("/abc/documents/file.txt")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Bad account ID") == true)
        }
    }

    @Test
    fun `parseDocumentId throws for negative account ID`() {
        try {
            WebDavProvider.parseDocumentId("/-1/documents/file.txt")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Bad account ID") == true)
        }
    }

    @Test
    fun `parseDocumentId throws for empty account ID`() {
        try {
            WebDavProvider.parseDocumentId("//documents/file.txt")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Bad account ID") == true)
        }
    }

    @Test
    fun `parseDocumentId throws for too few path segments`() {
        try {
            WebDavProvider.parseDocumentId("/1")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid document ID") == true)
        }
    }

    // Security tests for path traversal prevention
    
    @Test
    fun `parseDocumentId throws for path traversal with double dots in middle`() {
        try {
            WebDavProvider.parseDocumentId("/1/documents/../../../etc/passwd")
            fail("Expected IllegalArgumentException for path traversal")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Path traversal detected") == true)
        }
    }

    @Test
    fun `parseDocumentId throws for path traversal with double dots at end`() {
        try {
            WebDavProvider.parseDocumentId("/1/documents/..")
            fail("Expected IllegalArgumentException for path traversal")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Path traversal detected") == true)
        }
    }

    @Test
    fun `parseDocumentId throws for path traversal with single dot`() {
        try {
            WebDavProvider.parseDocumentId("/1/documents/./hidden")
            fail("Expected IllegalArgumentException for path traversal")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Path traversal detected") == true)
        }
    }

    @Test
    fun `parseDocumentId throws for just double dots path`() {
        try {
            WebDavProvider.parseDocumentId("/1/..")
            fail("Expected IllegalArgumentException for path traversal")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Path traversal detected") == true)
        }
    }

    @Test
    fun `parseDocumentId throws for just single dot path`() {
        try {
            WebDavProvider.parseDocumentId("/1/.")
            fail("Expected IllegalArgumentException for path traversal")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Path traversal detected") == true)
        }
    }

    @Test
    fun `parseDocumentId allows filenames containing dots`() {
        // This should NOT be considered path traversal
        val (accountId, path) = WebDavProvider.parseDocumentId("/1/documents/file.name.txt")
        
        assertEquals(1L, accountId)
        assertEquals(Paths.get("/documents/file.name.txt"), path)
    }

    @Test
    fun `parseDocumentId allows directory names with dots`() {
        // Directory names like ".config" should be allowed
        val (accountId, path) = WebDavProvider.parseDocumentId("/1/.config/settings")
        
        assertEquals(1L, accountId)
        assertEquals(Paths.get("/.config/settings"), path)
    }

    @Test
    fun `parseDocumentId allows hidden files starting with dot`() {
        val (accountId, path) = WebDavProvider.parseDocumentId("/1/documents/.hidden")
        
        assertEquals(1L, accountId)
        assertEquals(Paths.get("/documents/.hidden"), path)
    }

    @Test
    fun `parseDocumentId handles URL-encoded paths`() {
        val (accountId, path) = WebDavProvider.parseDocumentId("/1/documents/file%20name.txt")
        
        assertEquals(1L, accountId)
        assertEquals(Paths.get("/documents/file%20name.txt"), path)
    }

    @Test
    fun `parseDocumentId normalizes redundant slashes`() {
        val (accountId, path) = WebDavProvider.parseDocumentId("/1/documents//file.txt")
        
        assertEquals(1L, accountId)
        // Path normalization should handle this
        assertTrue(path.toString().contains("file.txt"))
    }
}
