package com.pypath.app.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Response of one HTTP call. */
data class HttpResult(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

/** Server rejected a request (not a connection problem). */
class SupabaseException(val code: Int, message: String) : Exception(message)

/**
 * Minimal HTTPS client for the Supabase REST APIs, built on the platform's HttpURLConnection, so the
 * app needs no extra networking library. Throws [IOException] when there is no connection.
 * Runs only in the app's main process; the `:python` runner process never uses it.
 */
open class SupabaseHttp(private val baseUrl: String = SupabaseConfig.URL, private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY) {

    open suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        accessToken: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): HttpResult = withContext(Dispatchers.IO) {
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection)
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.useCaches = false
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("apikey", apiKey)
            conn.setRequestProperty("Accept", "application/json")
            if (accessToken != null) conn.setRequestProperty("Authorization", "Bearer $accessToken")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            HttpResult(code, text)
        } finally {
            conn.disconnect()
        }
    }
}
