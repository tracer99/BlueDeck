package com.bluebridge.android.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object BluelinkConstants {
    const val BASE_URL_US_HYUNDAI = "https://api.telematics.hyundaiusa.com/"
    const val BASE_URL_US_KIA = "https://kiaconnect.com/"
    const val BASE_URL_CA_HYUNDAI = "https://mybluelink.ca/"
    const val BASE_URL_CA_KIA = "https://kiaconnect.ca/"
    const val BASE_URL_EU = "https://prd.eu-ccapi.hyundai.com:8080/"
    const val BASE_URL_AU = "https://prd.aus-ccapi.hyundai.com:8080/"

    // Public client credentials from the Bluelink app (via bluelinky)
    const val CLIENT_ID = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
    const val CLIENT_SECRET = "v558o935-6nne-423i-baa8"
    const val API_HOST = "api.telematics.hyundaiusa.com"
    const val APP_ID = "14d5efbe-c194-4a5c-af66-e0ba8c8f4c80"

    // Kia UVO credentials
    const val KIA_CLIENT_ID = "L5hc7010"
    const val KIA_CLIENT_SECRET = "mcnpc9dEZlfLfFaHR18zAMBNBqNMcDdcOBOLWOlBqjGDCcpkIj"

    const val TIMEOUT_SECONDS = 30L
    const val COMMAND_TIMEOUT_SECONDS = 60L
}

data class UsSpaHeaders(
    val clientId: String,
    val clientSecret: String,
    val appId: String?,
    val apiHost: String,
    val offset: String = "-5"
) {
    companion object {
        val HYUNDAI = UsSpaHeaders(
            clientId = BluelinkConstants.CLIENT_ID,
            clientSecret = BluelinkConstants.CLIENT_SECRET,
            appId = BluelinkConstants.APP_ID,
            apiHost = BluelinkConstants.API_HOST
        )
        val KIA = UsSpaHeaders(
            clientId = BluelinkConstants.KIA_CLIENT_ID,
            clientSecret = BluelinkConstants.KIA_CLIENT_SECRET,
            appId = null,
            apiHost = "api.owners.kia.com"
        )
    }
}

class ApiClient(
    private val baseUrl: String = BluelinkConstants.BASE_URL_US_HYUNDAI,
    private val spaHeaders: UsSpaHeaders = UsSpaHeaders.HYUNDAI
) {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val b = chain.request().newBuilder()
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Encoding", "gzip, deflate, br")
                .addHeader("User-Agent", "okhttp/4.12.0")
                .addHeader("client_id", spaHeaders.clientId)
                .addHeader("clientSecret", spaHeaders.clientSecret)
                .addHeader("deviceType", "Android")
                .addHeader("from", "SPA")
                .addHeader("language", "0")
                .addHeader("offset", spaHeaders.offset)
                .addHeader("to", "ISS")
                .addHeader("encryptFlag", "false")
            spaHeaders.appId?.let { b.addHeader("appId", it) }
            b.addHeader("Host", spaHeaders.apiHost)
            chain.proceed(b.build())
        }
        .connectTimeout(BluelinkConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(BluelinkConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(BluelinkConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    val apiService: BluelinkApiService = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BluelinkApiService::class.java)
}

enum class Region(val baseUrl: String, val label: String) {
    US_HYUNDAI(BluelinkConstants.BASE_URL_US_HYUNDAI, "USA — Hyundai"),
    US_KIA(BluelinkConstants.BASE_URL_US_KIA, "USA — Kia"),
    CA_HYUNDAI(BluelinkConstants.BASE_URL_CA_HYUNDAI, "Canada — Hyundai"),
    CA_KIA(BluelinkConstants.BASE_URL_CA_KIA, "Canada — Kia"),
    EU(BluelinkConstants.BASE_URL_EU, "Europe"),
    AU(BluelinkConstants.BASE_URL_AU, "Australia / New Zealand")
}

fun Region.usesCanadianTods(): Boolean =
    this == Region.CA_HYUNDAI || this == Region.CA_KIA

fun Region.usesUsSpa(): Boolean =
    this == Region.US_HYUNDAI || this == Region.US_KIA
