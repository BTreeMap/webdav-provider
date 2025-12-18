package org.joefang.webdav.provider

import org.joefang.webdav.data.CustomHeader
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that adds custom headers to every request.
 * 
 * This interceptor is used to inject authorization headers (e.g., Cloudflare Access
 * Service Token headers) or any custom headers defined by the user.
 * 
 * The headers are attached to every WebDAV request method.
 */
class CustomHeadersInterceptor(
    private val headers: List<CustomHeader>
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        if (headers.isEmpty()) {
            return chain.proceed(originalRequest)
        }
        
        val builder = originalRequest.newBuilder()
        
        for (header in headers) {
            if (header.enabled && header.name.isNotBlank() && header.value.isNotBlank()) {
                builder.header(header.name, header.value)
            }
        }
        
        return chain.proceed(builder.build())
    }
}
