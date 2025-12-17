package dev.rocli.android.webdav.provider

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.Buffer
import org.simpleframework.xml.Serializer
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

class SimpleXmlConverterFactoryCompat private constructor(
    private val serializer: Serializer,
    private val strict: Boolean
) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val cls = (type as? Class<*>) ?: return null
        return SimpleXmlResponseBodyConverter(serializer, cls, strict)
    }

    @Suppress("UNCHECKED_CAST")
    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<Annotation>,
        methodAnnotations: Array<Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        val cls = type as? Class<Any> ?: return null
        return SimpleXmlRequestBodyConverter(cls, serializer)
    }

    companion object {
        private val MEDIA_TYPE = "application/xml; charset=UTF-8".toMediaType()

        @JvmStatic
        fun create(serializer: Serializer, strict: Boolean = true): SimpleXmlConverterFactoryCompat {
            return SimpleXmlConverterFactoryCompat(serializer, strict)
        }
    }

    private class SimpleXmlResponseBodyConverter<T>(
        private val serializer: Serializer,
        private val cls: Class<T>,
        private val strict: Boolean
    ) : Converter<ResponseBody, T> {
        override fun convert(value: ResponseBody): T {
            value.use { body ->
                return serializer.read(cls, body.charStream(), strict)
            }
        }
    }

    private class SimpleXmlRequestBodyConverter<T : Any>(
        private val cls: Class<T>,
        private val serializer: Serializer
    ) : Converter<T, RequestBody> {
        override fun convert(value: T): RequestBody {
            val buffer = Buffer()
            serializer.write(cls.cast(value), buffer.outputStream())
            return buffer.readByteString().toRequestBody(MEDIA_TYPE)
        }
    }
}
