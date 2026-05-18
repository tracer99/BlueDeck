package com.bluebridge.android.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

object BluelinkConstants {
    const val BASE_URL_US_HYUNDAI = "https://api.telematics.hyundaiusa.com/"
    const val BASE_URL_US_KIA = "https://api.owners.kia.com/apigw/v1/"
    const val BASE_URL_CA_HYUNDAI = "https://mybluelink.ca/tods/api/"
    const val BASE_URL_CA_KIA = "https://kiaconnect.ca/tods/api/"
    const val BASE_URL_CA_GENESIS = "https://genesisconnect.ca/tods/api/"
    const val BASE_URL_EU_HYUNDAI = "https://prd.eu-ccapi.hyundai.com:8080/"
    const val BASE_URL_EU_KIA = "https://prd.eu-ccapi.kia.com:8080/"
    const val BASE_URL_EU_GENESIS = "https://prd-eu-ccapi.genesis.com/"
    const val IDP_URL_EU_HYUNDAI = "https://idpconnect-eu.hyundai.com/"
    const val IDP_URL_EU_KIA = "https://idpconnect-eu.kia.com/"
    const val IDP_URL_EU_GENESIS = "https://idpconnect-eu.genesis.com/"
    const val BASE_URL_AU_HYUNDAI = "https://au-apigw.ccs.hyundai.com.au:8080/"
    const val BASE_URL_AU_KIA = "https://au-apigw.ccs.kia.com.au:8082/"
    const val BASE_URL_NZ_KIA = "https://au-apigw.ccs.kia.com.au:8082/"

    // Public client credentials from the Bluelink app (via bluelinky)
    const val CLIENT_ID = "m66129Bb-em93-SPAHYN-bZ91-am4540zp19920"
    const val CLIENT_SECRET = "v558o935-6nne-423i-baa8"
    const val API_HOST = "api.telematics.hyundaiusa.com"
    const val APP_ID = "14d5efbe-c194-4a5c-af66-e0ba8c8f4c80"

    const val TIMEOUT_SECONDS = 30L
    const val COMMAND_TIMEOUT_SECONDS = 60L
}

@Singleton
class ApiClient(private val baseUrl: String = BluelinkConstants.BASE_URL_US_HYUNDAI) {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("User-Agent", "okhttp/4.12.0")
                .addHeader("client_id", BluelinkConstants.CLIENT_ID)
                .addHeader("clientSecret", BluelinkConstants.CLIENT_SECRET)
                .addHeader("appId", BluelinkConstants.APP_ID)
                .addHeader("deviceType", "Android")
                .addHeader("from", "SPA")
                .addHeader("language", "0")
                .addHeader("offset", "-5")
                .addHeader("to", "ISS")
                .addHeader("encryptFlag", "false")
                .build()
            chain.proceed(request)
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

// Regional API configuration
enum class Region(
    val baseUrl: String,
    val label: String,
    val isCanada: Boolean = false,
    val canadaHost: String = "",
    val isEurope: Boolean = false,
    val euHost: String = "",
    val euServiceId: String = "",
    val euAppId: String = "",
    val euBasicAuthorization: String = "",
    val euClientSecret: String = "",
    val euIdentityBaseUrl: String = "",
    val euCfbBase64: String = "",
    val euPushType: String = "GCM",
    val isAustralia: Boolean = false,
    val auHost: String = "",
    val auServiceId: String = "",
    val auAppId: String = "",
    val auBasicAuthorization: String = "",
    val auCfbBase64: String = ""
) {
    US_HYUNDAI(BluelinkConstants.BASE_URL_US_HYUNDAI, "USA — Hyundai"),
    US_KIA(BluelinkConstants.BASE_URL_US_KIA, "USA — Kia"),
    CA_HYUNDAI(BluelinkConstants.BASE_URL_CA_HYUNDAI, "Canada — Hyundai", true, "mybluelink.ca"),
    CA_KIA(BluelinkConstants.BASE_URL_CA_KIA, "Canada — Kia", true, "kiaconnect.ca"),
    CA_GENESIS(BluelinkConstants.BASE_URL_CA_GENESIS, "Canada — Genesis", true, "genesisconnect.ca"),
    EU(
        BluelinkConstants.BASE_URL_EU_HYUNDAI,
        "Europe — Hyundai",
        isEurope = true,
        euHost = "prd.eu-ccapi.hyundai.com",
        euServiceId = "6d477c38-3ca4-4cf3-9557-2a1929a94654",
        euAppId = "014d2225-8495-4735-812d-2616334fd15d",
        euBasicAuthorization = "Basic NmQ0NzdjMzgtM2NhNC00Y2YzLTk1NTctMmExOTI5YTk0NjU0OktVeTQ5WHhQekxwTHVvSzB4aEJDNzdXNlZYaG10UVI5aVFobUlGampvWTRJcHhzVg==",
        euClientSecret = "KUy49XxPzLpLuoK0xhBC77W6VXhmtQR9iQhmIFjjoY4IpxsV",
        euIdentityBaseUrl = BluelinkConstants.IDP_URL_EU_HYUNDAI,
        euCfbBase64 = "RFtoRq/vDXJmRndoZaZQyfOot7OrIqGVFj96iY2WL3yyH5Z/pUvlUhqmCxD2t+D65SQ=",
        euPushType = "GCM"
    ),
    EU_KIA(
        BluelinkConstants.BASE_URL_EU_KIA,
        "Europe — Kia",
        isEurope = true,
        euHost = "prd.eu-ccapi.kia.com",
        euServiceId = "fdc85c00-0a2f-4c64-bcb4-2cfb1500730a",
        euAppId = "a2b8469b-30a3-4361-8e13-6fceea8fbe74",
        euBasicAuthorization = "Basic ZmRjODVjMDAtMGEyZi00YzY0LWJjYjQtMmNmYjE1MDA3MzBhOnNlY3JldA==",
        euClientSecret = "secret",
        euIdentityBaseUrl = BluelinkConstants.IDP_URL_EU_KIA,
        euCfbBase64 = "wLTVxwidmH8CfJYBWSnHD6E0huk0ozdiuygB4hLkM5XCgzAL1Dk5sE36d/bx5PFMbZs=",
        euPushType = "APNS"
    ),
    EU_GENESIS(
        BluelinkConstants.BASE_URL_EU_GENESIS,
        "Europe — Genesis",
        isEurope = true,
        euHost = "prd-eu-ccapi.genesis.com",
        euServiceId = "3020afa2-30ff-412a-aa51-d28fbe901e10",
        euAppId = "f11f2b86-e0e7-4851-90df-5600b01d8b70",
        euBasicAuthorization = "Basic MzAyMGFmYTItMzBmZi00MTJhLWFhNTEtZDI4ZmJlOTAxZTEwOkZLRGRsZWYyZmZkbGVGRXdlRUxGS0VSaUxFUjJGRUQyMXNEZHdkZ1F6NmhGRVNFMw==",
        euClientSecret = "secret",
        euIdentityBaseUrl = BluelinkConstants.IDP_URL_EU_GENESIS,
        euCfbBase64 = "RFtoRq/vDXJmRndoZaZQyYo3/qFLtVReW8P7utRPcc0ZxOzOELm9mexvviBk/qqIp4A=",
        euPushType = "GCM"
    ),
    AU_HYUNDAI(
        BluelinkConstants.BASE_URL_AU_HYUNDAI,
        "Australia — Hyundai",
        isAustralia = true,
        auHost = "au-apigw.ccs.hyundai.com.au:8080",
        auServiceId = "855c72df-dfd7-4230-ab03-67cbf902bb1c",
        auAppId = "f9ccfdac-a48d-4c57-bd32-9116963c24ed",
        auBasicAuthorization = "Basic ODU1YzcyZGYtZGZkNy00MjMwLWFiMDMtNjdjYmY5MDJiYjFjOmU2ZmJ3SE0zMllOYmhRbDBwdmlhUHAzcmY0dDNTNms5MWVjZUEzTUpMZGJkVGhDTw==",
        auCfbBase64 = "nGDHng3k4Cg9gWV+C+A6Yk/ecDopUNTkGmDpr2qVKAQXx9bvY2/YLoHPfObliK32mZQ="
    ),
    AU_KIA(
        BluelinkConstants.BASE_URL_AU_KIA,
        "Australia — Kia",
        isAustralia = true,
        auHost = "au-apigw.ccs.kia.com.au:8082",
        auServiceId = "8acb778a-b918-4a8d-8624-73a0beb64289",
        auAppId = "4ad4dcde-be23-48a8-bc1c-91b94f5c06f8",
        auBasicAuthorization = "Basic OGFjYjc3OGEtYjkxOC00YThkLTg2MjQtNzNhMGJlYjY0Mjg5OjdTY01NbTZmRVlYZGlFUEN4YVBhUW1nZVlkbFVyZndvaDRBZlhHT3pZSVMyQ3U5VA==",
        auCfbBase64 = "SGGCDRvrzmRa2WTNFQPUaNfSFdtPklZ48xUuVckigYasxmeOQqVgCAC++YNrI1vVabI="
    ),
    NZ_KIA(
        BluelinkConstants.BASE_URL_NZ_KIA,
        "New Zealand — Kia",
        isAustralia = true,
        auHost = "au-apigw.ccs.kia.com.au:8082",
        auServiceId = "4ab606a7-cea4-48a0-a216-ed9c14a4a38c",
        auAppId = "97745337-cac6-4a5b-afc3-e65ace81c994",
        auBasicAuthorization = "Basic NGFiNjA2YTctY2VhNC00OGEwLWEyMTYtZWQ5YzE0YTRhMzhjOjBoYUZxWFRrS2t0Tktmemt4aFowYWt1MzFpNzRnMHlRRm01b2QybXo0TGRJNW1MWQ==",
        auCfbBase64 = "SGGCDRvrzmRa2WTNFQPUaC1OsnAhQgPgcQETEfbY8abEjR/ICXK0p+Rayw5tHCGyiUA="
    ),
    AU(
        BluelinkConstants.BASE_URL_AU_HYUNDAI,
        "Australia / New Zealand",
        isAustralia = true,
        auHost = "au-apigw.ccs.hyundai.com.au:8080",
        auServiceId = "855c72df-dfd7-4230-ab03-67cbf902bb1c",
        auAppId = "f9ccfdac-a48d-4c57-bd32-9116963c24ed",
        auBasicAuthorization = "Basic ODU1YzcyZGYtZGZkNy00MjMwLWFiMDMtNjdjYmY5MDJiYjFjOmU2ZmJ3SE0zMllOYmhRbDBwdmlhUHAzcmY0dDNTNms5MWVjZUEzTUpMZGJkVGhDTw==",
        auCfbBase64 = "nGDHng3k4Cg9gWV+C+A6Yk/ecDopUNTkGmDpr2qVKAQXx9bvY2/YLoHPfObliK32mZQ="
    )
}
