package com.cinehub.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

class HttpException(val code: Int, message: String) : IOException(message)

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw HttpException(r.code, "HTTP ${r.code}: $url")
                r.body?.string() ?: ""
            }
        }

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonObject =
        AppJson.parseToJsonElement(getText(url, headers + ("Accept" to "application/json"))) as JsonObject

    suspend fun getJsonAny(url: String, headers: Map<String, String> = emptyMap()): JsonElement =
        AppJson.parseToJsonElement(getText(url, headers + ("Accept" to "application/json")))

    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): JsonObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw HttpException(r.code, "HTTP ${r.code}")
                val text = r.body?.string().orEmpty().ifBlank { "{}" }
                (AppJson.parseToJsonElement(text) as? JsonObject) ?: JsonObject(emptyMap())
            }
        }
}

// ---- Удобный доступ к полям JSON ----

fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
fun JsonObject.arr(name: String): List<JsonObject> =
    (this[name] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
fun JsonObject.strList(name: String): List<String> =
    (this[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

private fun JsonObject.prim(name: String): JsonPrimitive? =
    this[name]?.takeIf { it !is JsonNull } as? JsonPrimitive

fun JsonObject.str(name: String): String? = prim(name)?.contentOrNull?.takeIf { it.isNotEmpty() }
fun JsonObject.int(name: String): Int? = prim(name)?.intOrNull
fun JsonObject.long(name: String): Long? = prim(name)?.longOrNull
fun JsonObject.dbl(name: String): Double? = prim(name)?.doubleOrNull
fun JsonObject.bool(name: String): Boolean? = prim(name)?.booleanOrNull

fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")
fun String.trimSlash(): String = trimEnd('/')
