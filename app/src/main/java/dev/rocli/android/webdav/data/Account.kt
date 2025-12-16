package dev.rocli.android.webdav.data

import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.rocli.android.webdav.BuildConfig
import dev.rocli.android.webdav.extensions.ensureTrailingSlash
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.nio.file.Path
import java.nio.file.Paths

@Entity(tableName = "account")
data class Account(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    @ColumnInfo(name = "name")
    var name: String? = null,

    @ColumnInfo(name = "url")
    var url: String? = null,

    @ColumnInfo(name = "protocol", defaultValue = "AUTO")
    var protocol: Protocol = Protocol.AUTO,

    @ColumnInfo(name = "verify_certs")
    var verifyCerts: Boolean = true,

    @ColumnInfo(name = "auth_type", defaultValue = "NONE")
    var authType: AuthType = AuthType.NONE,

    @ColumnInfo(name = "username")
    var username: SecretString? = null,

    @ColumnInfo(name = "password")
    var password: SecretString? = null,

    @ColumnInfo(name = "client_cert")
    var clientCert: String? = null,

    @ColumnInfo(name = "max_cache_file_size")
    var maxCacheFileSize: Long = 20,

    @ColumnInfo(name = "act_as_local_storage", defaultValue = "false")
    var actAsLocalStorage: Boolean = false,

    // Authorization headers configuration
    @ColumnInfo(name = "header_profile", defaultValue = "NONE")
    var headerProfile: HeaderProfile = HeaderProfile.NONE,

    // Cloudflare Access Service Token fields
    @ColumnInfo(name = "cf_access_client_id")
    var cfAccessClientId: SecretString? = null,

    @ColumnInfo(name = "cf_access_client_secret")
    var cfAccessClientSecret: SecretString? = null,

    // Custom headers for advanced users (stored as JSON)
    @ColumnInfo(name = "custom_headers")
    var customHeaders: List<CustomHeader>? = null
) {
    val rootPath: Path
        get() {
            val path = baseUrl.encodedPath.ensureTrailingSlash()
            return Paths.get(path)
        }

    val rootId: String
        get() {
            return id.toString()
        }

    val rootUri: Uri
        get() {
            return DocumentsContract.buildRootUri(BuildConfig.PROVIDER_AUTHORITY, rootId)
        }

    val baseUrl: HttpUrl
        get() {
            return url!!.ensureTrailingSlash().toHttpUrl()
        }

    val hasError: Boolean
        get() {
            return username?.error != null || password?.error != null ||
                   cfAccessClientId?.error != null || cfAccessClientSecret?.error != null
        }

    /**
     * Returns the resolved list of custom headers based on the current header profile.
     * - For NONE: returns empty list
     * - For CLOUDFLARE: returns CF-Access-Client-Id and CF-Access-Client-Secret headers
     * - For CUSTOM: returns the user-defined custom headers
     */
    fun getResolvedHeaders(): List<CustomHeader> {
        return when (headerProfile) {
            HeaderProfile.NONE -> emptyList()
            HeaderProfile.CLOUDFLARE -> {
                val headers = mutableListOf<CustomHeader>()
                cfAccessClientId?.value?.let { clientId ->
                    headers.add(CustomHeader(
                        name = CF_ACCESS_CLIENT_ID_HEADER,
                        value = clientId,
                        isSecret = true,
                        enabled = true
                    ))
                }
                cfAccessClientSecret?.value?.let { clientSecret ->
                    headers.add(CustomHeader(
                        name = CF_ACCESS_CLIENT_SECRET_HEADER,
                        value = clientSecret,
                        isSecret = true,
                        enabled = true
                    ))
                }
                headers
            }
            HeaderProfile.CUSTOM -> customHeaders?.filter { it.enabled } ?: emptyList()
        }
    }

    companion object {
        const val CF_ACCESS_CLIENT_ID_HEADER = "CF-Access-Client-Id"
        const val CF_ACCESS_CLIENT_SECRET_HEADER = "CF-Access-Client-Secret"
    }

    enum class Protocol {
        AUTO, HTTP1
    }

    enum class AuthType {
        NONE,
        BASIC,
        DIGEST
    }
}

fun List<Account>.byId(id: Long): Account {
    return this.single { v -> v.id == id }
}
