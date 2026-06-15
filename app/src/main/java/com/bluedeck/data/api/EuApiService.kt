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
import retrofit2.http.PUT
import retrofit2.http.Path

interface EuApiService {
    @FormUrlEncoded
    @POST("api/v1/user/oauth2/token")
    suspend fun refreshAccessToken(
        @Header("Authorization") authorization: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): Response<JsonObject>

    @POST("api/v1/spa/notifications/register")
    suspend fun registerNotifications(@Body body: JsonObject): Response<JsonObject>

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

    @PUT("api/v1/user/pin")
    suspend fun verifyPin(
        @Header("Authorization") authorization: String,
        @Body body: Map<String, String>
    ): Response<JsonObject>

    @POST("api/v1/spa/vehicles/{vehicleId}/control/door")
    suspend fun controlDoor(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: String,
        @Body body: JsonObject
    ): Response<ResponseBody>

    @POST("api/v1/spa/vehicles/{vehicleId}/control/temperature")
    suspend fun controlEvClimate(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: String,
        @Body body: JsonObject
    ): Response<ResponseBody>

    @POST("api/v1/spa/vehicles/{vehicleId}/control/temperature")
    suspend fun controlEngine(
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

    @POST("api/v1/spa/vehicles/{vehicleId}/charge/reserve")
    suspend fun setChargeSchedule(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: String,
        @Body body: JsonObject
    ): Response<ResponseBody>

    @POST("api/v2/spa/vehicles/{vehicleId}/charge/reserve")
    suspend fun setChargeScheduleAlt(
        @Header("Authorization") authorization: String,
        @Path("vehicleId") vehicleId: String,
        @Body body: JsonObject
    ): Response<ResponseBody>
}

class EuApiClient(
    private val region: Region,
    private val deviceId: String
) {
    private val okHttpClient = baseOkHttpBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("ccsp-service-id", region.euServiceId)
                .header("ccsp-application-id", region.euAppId)
                .header("ccsp-device-id", deviceId)
                .header("Stamp", buildCcspStamp(region.euAppId, region.euCfbBase64))
                .header("Host", region.euHost)
                .header("Connection", "Keep-Alive")
                .header("Accept-Encoding", "gzip")
                .header("User-Agent", USER_AGENT_OK_HTTP)
                .build()
            chain.proceed(request)
        }
        .build()

    val apiService: EuApiService = buildRetrofit(region.baseUrl, okHttpClient)
        .create(EuApiService::class.java)
}

interface EuIdentityApiService {
    @FormUrlEncoded
    @POST("auth/api/v2/user/oauth2/token")
    suspend fun refreshAccessToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String
    ): Response<JsonObject>
}

class EuIdentityApiClient(region: Region) {
    private val baseUrl = region.euIdentityBaseUrl.trimEnd('/') + "/"

    private val okHttpClient = baseOkHttpBuilder().build()

    val apiService: EuIdentityApiService = buildRetrofit(baseUrl, okHttpClient)
        .create(EuIdentityApiService::class.java)
}
