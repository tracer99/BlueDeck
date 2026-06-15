package com.bluedeck.data.api

import com.google.gson.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface AuApiService {
    @GET("api/v1/user/oauth2/authorize")
    suspend fun getAuthorizationCookies(
        @retrofit2.http.Query("response_type") responseType: String = "code",
        @retrofit2.http.Query("client_id") clientId: String,
        @retrofit2.http.Query("redirect_uri") redirectUri: String,
        @retrofit2.http.Query("lang") lang: String = "en"
    ): Response<ResponseBody>

    @POST("api/v1/user/signin")
    suspend fun signIn(@Body body: JsonObject): Response<JsonObject>

    @FormUrlEncoded
    @POST("api/v1/user/oauth2/token")
    suspend fun exchangeAuthorizationCode(
        @Header("Authorization") authorization: String,
        @Header("Stamp") stamp: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("redirect_uri") redirectUri: String,
        @Field("code") code: String
    ): Response<JsonObject>

    @FormUrlEncoded
    @POST("api/v1/user/oauth2/token")
    suspend fun refreshAccessToken(
        @Header("Authorization") authorization: String,
        @Header("Stamp") stamp: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): Response<JsonObject>

    @POST("api/v1/spa/notifications/register")
    suspend fun registerNotifications(
        @Header("Stamp") stamp: String,
        @Body body: JsonObject
    ): Response<JsonObject>

    @GET("api/v1/spa/vehicles")
    suspend fun getVehicles(@Header("Authorization") authorization: String): Response<JsonObject>

    @GET("api/v1/spa/vehicles/{vehicleId}/status/latest")
    suspend fun getCachedVehicleStatus(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: String
    ): Response<JsonObject>

    @GET("api/v1/spa/vehicles/{vehicleId}/status")
    suspend fun getLiveVehicleStatus(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: String
    ): Response<JsonObject>

    @GET("api/v1/spa/vehicles/{vehicleId}/ccs2/carstatus/latest")
    suspend fun getCachedCcs2VehicleStatus(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: String
    ): Response<JsonObject>

    @GET("api/v1/spa/vehicles/{vehicleId}/ccs2/carstatus")
    suspend fun getLiveCcs2VehicleStatus(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: String
    ): Response<JsonObject>

    @POST("api/v1/spa/vehicles/{vehicleId}/control/door")
    suspend fun controlDoor(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: String,
        @Body body: JsonObject
    ): Response<ResponseBody>

    @POST("api/v2/spa/vehicles/{vehicleId}/control/engine")
    suspend fun controlEngine(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: String,
        @Body body: JsonObject
    ): Response<ResponseBody>

    @POST("api/v2/spa/vehicles/{vehicleId}/control/engine")
    suspend fun controlEvClimate(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: String,
        @Body body: JsonObject
    ): Response<ResponseBody>

    @POST("api/v1/spa/vehicles/{vehicleId}/control/charge")
    suspend fun controlCharge(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: String,
        @Body body: JsonObject
    ): Response<ResponseBody>
}

class AuApiClient(
    private val region: Region,
    private val deviceId: String
) {
    private val cookieJar = inMemoryCookieJar()

    private val okHttpClient = baseOkHttpBuilder(cookieJar = cookieJar)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("ccsp-service-id", region.auServiceId)
                .header("ccsp-application-id", region.auAppId)
                .header("ccsp-device-id", deviceId)
                .header("Host", region.auHost)
                .header("Connection", "Keep-Alive")
                .header("Accept-Encoding", "gzip")
                .header("User-Agent", USER_AGENT_OK_HTTP)
            if (chain.request().header("Stamp").isNullOrBlank()) {
                builder.header("Stamp", stamp())
            }
            chain.proceed(builder.build())
        }
        .build()

    val apiService: AuApiService = buildRetrofit(region.baseUrl, okHttpClient)
        .create(AuApiService::class.java)

    fun stamp(): String = buildCcspStamp(region.auAppId, region.auCfbBase64)
}
