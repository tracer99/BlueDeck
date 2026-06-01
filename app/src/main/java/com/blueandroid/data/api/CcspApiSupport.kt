package com.blueandroid.data.api

import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit

internal const val USER_AGENT_OK_HTTP = "okhttp/3.12.0"

fun newAuPushRegistrationId(): String {
    val random = SecureRandom()
    val alphabet = "0123456789abcdef"
    return buildString(64) {
        repeat(64) { append(alphabet[random.nextInt(alphabet.length)]) }
    }
}

fun newAuUuid(): String = UUID.randomUUID().toString()

internal fun buildCcspStamp(appId: String, cfbBase64: String): String {
    val cfb = Base64.decode(cfbBase64, Base64.DEFAULT)
    val raw = "$appId:${System.currentTimeMillis() / 1000}".encodeToByteArray()
    val stamped = ByteArray(minOf(cfb.size, raw.size)) { index ->
        (cfb[index].toInt() xor raw[index].toInt()).toByte()
    }
    return Base64.encodeToString(stamped, Base64.NO_WRAP)
}

internal fun inMemoryCookieJar(): CookieJar = object : CookieJar {
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        store.getOrPut(url.host) { mutableListOf() }.apply {
            cookies.forEach { cookie ->
                removeAll { it.name == cookie.name }
                add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host].orEmpty().filter { it.expiresAt > System.currentTimeMillis() }
}

internal fun buildRetrofit(
    baseUrl: String,
    client: OkHttpClient
): Retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

internal fun baseOkHttpBuilder(
    logging: Boolean = true,
    cookieJar: CookieJar? = null
): OkHttpClient.Builder {
    val builder = OkHttpClient.Builder()
        .connectTimeout(BluelinkConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(BluelinkConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(BluelinkConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
    if (logging) {
        builder.addInterceptor(
            HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        )
    }
    cookieJar?.let { builder.cookieJar(it) }
    return builder
}
