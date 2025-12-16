package dev.rocli.android.webdav.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

/**
 * TypeConverter for storing a list of CustomHeader objects as JSON in the database.
 * Values marked as secret are encrypted using SecretStringConverter before being stored.
 */
class CustomHeaderListConverter {
    private val secretStringConverter = SecretStringConverter()

    @TypeConverter
    fun fromCustomHeaderList(headers: List<CustomHeader>?): String? {
        if (headers == null || headers.isEmpty()) {
            return null
        }

        val jsonArray = JSONArray()
        for (header in headers) {
            val jsonObject = JSONObject()
            jsonObject.put("name", header.name)
            // Encrypt secret values
            val storedValue = if (header.isSecret) {
                secretStringConverter.encrypt(SecretString(header.value))
            } else {
                header.value
            }
            jsonObject.put("value", storedValue)
            jsonObject.put("isSecret", header.isSecret)
            jsonObject.put("enabled", header.enabled)
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toCustomHeaderList(json: String?): List<CustomHeader>? {
        if (json.isNullOrBlank()) {
            return null
        }

        val headers = mutableListOf<CustomHeader>()
        val jsonArray = JSONArray(json)
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            val name = jsonObject.getString("name")
            val storedValue = jsonObject.getString("value")
            val isSecret = jsonObject.optBoolean("isSecret", false)
            val enabled = jsonObject.optBoolean("enabled", true)
            
            // Decrypt secret values
            val value = if (isSecret) {
                val decrypted = secretStringConverter.decrypt(storedValue)
                decrypted?.value ?: ""
            } else {
                storedValue
            }
            
            headers.add(CustomHeader(name, value, isSecret, enabled))
        }
        return headers
    }
}
