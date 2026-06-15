package com.bluedeck.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.TimeZone

internal fun readCanadaHttpBody(response: Response<ResponseBody>): String {
    val body = try {
        response.body()?.string().orEmpty()
    } catch (_: Exception) {
        ""
    }
    if (body.isNotBlank()) return body
    return try {
        response.errorBody()?.string().orEmpty()
    } catch (_: Exception) {
        ""
    }
}

internal fun parseCanadaJsonBody(raw: String): JsonObject? {
    if (raw.isBlank()) return null
    return try {
        JsonParser.parseString(raw).takeIf { it.isJsonObject }?.asJsonObject
    } catch (_: Exception) {
        null
    }
}

internal fun canadaUnreadableBodyMessage(raw: String, httpCode: Int): String {
    val trimmed = raw.trim()
    return when {
        trimmed.isBlank() ->
            "Canada API returned an empty response (HTTP $httpCode). Check your connection and try again."
        trimmed.startsWith("<") ->
            "Canada API returned a web page instead of JSON (HTTP $httpCode). " +
                "This often means the server blocked the request; try again later or sign in once in the official app."
        else ->
            "Canada API returned an unreadable response (HTTP $httpCode): ${trimmed.take(160)}"
    }
}

interface CanadaApiService {
    @POST("v2/login")
    suspend fun login(
        @Header("Deviceid") deviceId: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @POST("mfa/selverifmeth")
    suspend fun selectVerificationMethod(
        @Header("Deviceid") deviceId: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @POST("mfa/sendotp")
    suspend fun sendOtp(
        @Header("Deviceid") deviceId: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @POST("mfa/validateotp")
    suspend fun validateOtp(
        @Header("Deviceid") deviceId: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @POST("mfa/genmfatkn")
    suspend fun generateMfaToken(
        @Header("Deviceid") deviceId: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @POST("vhcllst")
    suspend fun getVehicles(
        @Header("accessToken") accessToken: String,
        @Header("Deviceid") deviceId: String
    ): Response<ResponseBody>

    @POST("lstvhclsts")
    suspend fun getCachedVehicleStatus(
        @Header("accessToken") accessToken: String,
        @Header("vehicleId") vehicleId: String,
        @Header("Deviceid") deviceId: String
    ): Response<ResponseBody>

    @POST("rltmvhclsts")
    suspend fun getLiveVehicleStatus(
        @Header("accessToken") accessToken: String,
        @Header("vehicleId") vehicleId: String,
        @Header("Deviceid") deviceId: String
    ): Response<ResponseBody>

    @POST("vrfypin")
    suspend fun verifyPin(
        @Header("accessToken") accessToken: String,
        @Header("vehicleId") vehicleId: String,
        @Header("Deviceid") deviceId: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @POST("drlck")
    suspend fun lockDoors(
        @Header("accessToken") accessToken: String,
        @Header("vehicleId") vehicleId: String,
        @Header("pAuth") pAuth: String,
        @Header("Deviceid") deviceId: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @POST("drulck")
    suspend fun unlockDoors(
        @Header("accessToken") accessToken: String,
        @Header("vehicleId") vehicleId: String,
        @Header("pAuth") pAuth: String,
        @Header("Deviceid") deviceId: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @POST("rmtstrt")
    suspend fun startEngine(
        @Header("accessToken") accessToken: String,
        @Header("vehicleId") vehicleId: String,
        @Header("pAuth") pAuth: String,
        @Header("Deviceid") deviceId: String,
        @Body body: JsonObject
    ): Response<ResponseBody>

    @POST("evc/rfon")
    suspend fun startEvClimate(
        @Header("accessToken") accessToken: String,
        @Header("vehicleId") vehicleId: String,
        @Header("pAuth") pAuth: String,
        @Header("Deviceid") deviceId: String,
        @Body body: JsonObject
    ): Response<ResponseBody>

    @POST("rmtstp")
    suspend fun stopEngine(
        @Header("accessToken") accessToken: String,
        @Header("vehicleId") vehicleId: String,
        @Header("pAuth") pAuth: String,
        @Header("Deviceid") deviceId: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @POST("evc/rfoff")
    suspend fun stopEvClimate(
        @Header("accessToken") accessToken: String,
        @Header("vehicleId") vehicleId: String,
        @Header("pAuth") pAuth: String,
        @Header("Deviceid") deviceId: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @POST("evc/rcstrt")
    suspend fun startCharging(
        @Header("accessToken") accessToken: String,
        @Header("vehicleId") vehicleId: String,
        @Header("pAuth") pAuth: String,
        @Header("Deviceid") deviceId: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>

    @POST("evc/rcstp")
    suspend fun stopCharging(
        @Header("accessToken") accessToken: String,
        @Header("vehicleId") vehicleId: String,
        @Header("pAuth") pAuth: String,
        @Header("Deviceid") deviceId: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>
}

internal fun canadaTimezoneOffsetHours(): String =
    (TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 3_600_000).toString()

class CanadaApiClient(
    baseUrl: String,
    private val canadaHost: String
) {
    private val okHttpClient = baseOkHttpBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Accept", "application/json, text/plain, */*")
                // Do not set Accept-Encoding manually — OkHttp adds gzip and decompresses transparently.
                .header("Accept-Language", "en-CA,en-US;q=0.8,en;q=0.5")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                )
                .header("Origin", "https://$canadaHost")
                .header("Referer", "https://$canadaHost/login")
                .header("from", "CWP")
                .header("language", "0")
                .header("offset", canadaTimezoneOffsetHours())
                .header("client_id", BluelinkConstants.CA_CLIENT_ID)
                .header("client_secret", BluelinkConstants.CA_CLIENT_SECRET)
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .build()
            chain.proceed(request)
        }
        .build()

    val apiService: CanadaApiService = buildRetrofit(baseUrl, okHttpClient)
        .create(CanadaApiService::class.java)
}
