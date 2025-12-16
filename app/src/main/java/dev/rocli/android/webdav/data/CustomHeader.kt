package dev.rocli.android.webdav.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a custom HTTP header to be sent with WebDAV requests.
 * @param name The header name (e.g., "CF-Access-Client-Id")
 * @param value The header value
 * @param isSecret Whether the value should be treated as a secret (masked in UI, encrypted in storage)
 * @param enabled Whether this header should be included in requests
 */
@Parcelize
data class CustomHeader(
    val name: String,
    val value: String,
    val isSecret: Boolean = false,
    val enabled: Boolean = true
) : Parcelable {
    companion object {
        // Header name validation regex: alphanumeric, hyphen, underscore only
        private val VALID_HEADER_NAME_REGEX = Regex("^[A-Za-z0-9_-]+$")
        
        // Dangerous headers that should trigger a warning
        val DANGEROUS_HEADERS = setOf(
            "Host", "Content-Length", "Content-Type", "Transfer-Encoding",
            "Connection", "Upgrade", "Authorization"
        )

        /**
         * Validates a header name.
         * @return null if valid, error message if invalid
         */
        fun validateHeaderName(name: String): String? {
            if (name.isBlank()) {
                return "Header name is required"
            }
            if (!VALID_HEADER_NAME_REGEX.matches(name)) {
                return "Header name contains invalid characters"
            }
            return null
        }

        /**
         * Checks if a header name is considered dangerous.
         */
        fun isDangerousHeader(name: String): Boolean {
            return DANGEROUS_HEADERS.any { it.equals(name, ignoreCase = true) }
        }
    }
}
