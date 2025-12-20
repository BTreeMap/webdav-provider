package org.joefang.webdav.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTest {

    @Test
    fun `getResolvedHeaders returns empty list for NONE profile`() {
        val account = Account(
            id = 1,
            name = "Test Account",
            url = "https://example.com/webdav/",
            headerProfile = HeaderProfile.NONE
        )
        
        val headers = account.getResolvedHeaders()
        
        assertTrue(headers.isEmpty())
    }

    @Test
    fun `getResolvedHeaders returns Cloudflare headers for CLOUDFLARE profile`() {
        val account = Account(
            id = 1,
            name = "Test Account",
            url = "https://example.com/webdav/",
            headerProfile = HeaderProfile.CLOUDFLARE,
            cfAccessClientId = SecretString("test-client-id"),
            cfAccessClientSecret = SecretString("test-client-secret")
        )
        
        val headers = account.getResolvedHeaders()
        
        assertEquals(2, headers.size)
        
        val clientIdHeader = headers.find { it.name == Account.CF_ACCESS_CLIENT_ID_HEADER }
        val clientSecretHeader = headers.find { it.name == Account.CF_ACCESS_CLIENT_SECRET_HEADER }
        
        assertEquals("test-client-id", clientIdHeader?.value)
        assertEquals("test-client-secret", clientSecretHeader?.value)
        assertTrue(clientIdHeader?.isSecret == true)
        assertTrue(clientSecretHeader?.isSecret == true)
        assertTrue(clientIdHeader?.enabled == true)
        assertTrue(clientSecretHeader?.enabled == true)
    }

    @Test
    fun `getResolvedHeaders returns partial Cloudflare headers when only clientId is set`() {
        val account = Account(
            id = 1,
            name = "Test Account",
            url = "https://example.com/webdav/",
            headerProfile = HeaderProfile.CLOUDFLARE,
            cfAccessClientId = SecretString("test-client-id"),
            cfAccessClientSecret = null
        )
        
        val headers = account.getResolvedHeaders()
        
        assertEquals(1, headers.size)
        assertEquals(Account.CF_ACCESS_CLIENT_ID_HEADER, headers[0].name)
        assertEquals("test-client-id", headers[0].value)
    }

    @Test
    fun `getResolvedHeaders returns empty list for CLOUDFLARE profile with no credentials`() {
        val account = Account(
            id = 1,
            name = "Test Account",
            url = "https://example.com/webdav/",
            headerProfile = HeaderProfile.CLOUDFLARE,
            cfAccessClientId = null,
            cfAccessClientSecret = null
        )
        
        val headers = account.getResolvedHeaders()
        
        assertTrue(headers.isEmpty())
    }

    @Test
    fun `getResolvedHeaders returns custom headers for CUSTOM profile`() {
        val customHeaders = listOf(
            CustomHeader(name = "X-Custom-1", value = "value1", enabled = true),
            CustomHeader(name = "X-Custom-2", value = "value2", enabled = true),
            CustomHeader(name = "X-Disabled", value = "value3", enabled = false)
        )
        
        val account = Account(
            id = 1,
            name = "Test Account",
            url = "https://example.com/webdav/",
            headerProfile = HeaderProfile.CUSTOM,
            customHeaders = customHeaders
        )
        
        val headers = account.getResolvedHeaders()
        
        // Only enabled headers should be returned
        assertEquals(2, headers.size)
        assertTrue(headers.any { it.name == "X-Custom-1" && it.value == "value1" })
        assertTrue(headers.any { it.name == "X-Custom-2" && it.value == "value2" })
    }

    @Test
    fun `getResolvedHeaders returns empty list for CUSTOM profile with null customHeaders`() {
        val account = Account(
            id = 1,
            name = "Test Account",
            url = "https://example.com/webdav/",
            headerProfile = HeaderProfile.CUSTOM,
            customHeaders = null
        )
        
        val headers = account.getResolvedHeaders()
        
        assertTrue(headers.isEmpty())
    }

    @Test
    fun `getResolvedHeaders returns empty list for CUSTOM profile with empty customHeaders`() {
        val account = Account(
            id = 1,
            name = "Test Account",
            url = "https://example.com/webdav/",
            headerProfile = HeaderProfile.CUSTOM,
            customHeaders = emptyList()
        )
        
        val headers = account.getResolvedHeaders()
        
        assertTrue(headers.isEmpty())
    }

    @Test
    fun `hasError returns false when no credentials have errors`() {
        val account = Account(
            id = 1,
            name = "Test Account",
            url = "https://example.com/webdav/",
            username = SecretString("user"),
            password = SecretString("pass")
        )
        
        assertEquals(false, account.hasError)
    }

    @Test
    fun `hasError returns true when username has error`() {
        val account = Account(
            id = 1,
            name = "Test Account",
            url = "https://example.com/webdav/",
            username = SecretString(error = Exception("Decryption failed")),
            password = SecretString("pass")
        )
        
        assertEquals(true, account.hasError)
    }

    @Test
    fun `hasError returns true when cfAccessClientId has error`() {
        val account = Account(
            id = 1,
            name = "Test Account",
            url = "https://example.com/webdav/",
            cfAccessClientId = SecretString(error = Exception("Decryption failed"))
        )
        
        assertEquals(true, account.hasError)
    }

    @Test
    fun `rootPath extracts path from URL correctly`() {
        // Note: rootPath uses Paths.get which may not preserve trailing slash
        // depending on the platform. The test validates the path segments are correct.
        val account = Account(
            id = 1,
            name = "Test Account",
            url = "https://example.com/webdav/files/"
        )
        
        val path = account.rootPath
        assertTrue(path.toString().startsWith("/webdav/files"))
    }

    @Test
    fun `rootPath handles URL without trailing slash`() {
        val account = Account(
            id = 1,
            name = "Test Account",
            url = "https://example.com/webdav"
        )
        
        val path = account.rootPath
        assertTrue(path.toString().startsWith("/webdav"))
    }

    @Test
    fun `byId extension function finds account by id`() {
        val accounts = listOf(
            Account(id = 1, name = "Account 1", url = "https://example1.com/"),
            Account(id = 2, name = "Account 2", url = "https://example2.com/"),
            Account(id = 3, name = "Account 3", url = "https://example3.com/")
        )
        
        val found = accounts.byId(2)
        
        assertEquals("Account 2", found.name)
        assertEquals(2L, found.id)
    }

    @Test(expected = NoSuchElementException::class)
    fun `byId extension function throws when id not found`() {
        val accounts = listOf(
            Account(id = 1, name = "Account 1", url = "https://example1.com/")
        )
        
        accounts.byId(999)
    }
}
