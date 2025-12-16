package dev.rocli.android.webdav.data

/**
 * Defines the available header profile types for WebDAV authorization.
 */
enum class HeaderProfile {
    /**
     * No custom headers are sent (default behavior).
     */
    NONE,
    
    /**
     * Cloudflare Access Service Token profile.
     * Sends CF-Access-Client-Id and CF-Access-Client-Secret headers.
     */
    CLOUDFLARE,
    
    /**
     * Custom headers profile for advanced users.
     * Allows adding/removing arbitrary headers.
     */
    CUSTOM
}
