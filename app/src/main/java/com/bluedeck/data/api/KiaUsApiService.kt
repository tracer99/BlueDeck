package com.bluedeck.data.api

import com.google.gson.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

interface KiaUsApiService {
    @POST("prof/authUser")
    suspend fun authUser(
        @Body body: JsonObject,
        @Header("sid") sid: String? = null,
        @Header("rmtoken") refreshToken: String? = null
    ): Response<JsonObject>

    @POST("cmm/sendOTP")
    suspend fun sendOtp(
        @Header("otpkey") otpKey: String,
        @Header("notifytype") notifyType: String,
        @Header("xid") xid: String,
        @Body body: JsonObject = JsonObject()
    ): Response<JsonObject>

    @POST("cmm/verifyOTP")
    suspend fun verifyOtp(
        @Header("otpkey") otpKey: String,
        @Header("xid") xid: String,
        @Body body: JsonObject
    ): Response<JsonObject>

    @GET("ownr/gvl")
    suspend fun getVehicles(@Header("sid") sid: String): Response<JsonObject>

    @POST("cmm/gvi")
    suspend fun getCachedVehicleInfo(
        @Header("sid") sid: String,
        @Header("vinkey") vehicleKey: String,
        @Body body: JsonObject
    ): Response<JsonObject>

    @POST("rems/rvs")
    suspend fun forceRefreshVehicleInfo(
        @Header("sid") sid: String,
        @Header("vinkey") vehicleKey: String,
        @Body body: JsonObject
    ): Response<JsonObject>

    @GET("evc/gts")
    suspend fun getChargeTargets(
        @Header("sid") sid: String,
        @Header("vinkey") vehicleKey: String
    ): Response<JsonObject>

    @GET("rems/door/lock")
    suspend fun lockDoors(
        @Header("sid") sid: String,
        @Header("vinkey") vehicleKey: String
    ): Response<ResponseBody>

    @GET("rems/door/unlock")
    suspend fun unlockDoors(
        @Header("sid") sid: String,
        @Header("vinkey") vehicleKey: String
    ): Response<ResponseBody>

    @POST("rems/start")
    suspend fun startClimate(
        @Header("sid") sid: String,
        @Header("vinkey") vehicleKey: String,
        @Body body: JsonObject
    ): Response<ResponseBody>

    @POST("rems/stop")
    suspend fun stopClimate(
        @Header("sid") sid: String,
        @Header("vinkey") vehicleKey: String
    ): Response<ResponseBody>

    @POST("evc/charge")
    suspend fun startCharging(
        @Header("sid") sid: String,
        @Header("vinkey") vehicleKey: String,
        @Body body: JsonObject
    ): Response<ResponseBody>

    @POST("evc/cancel")
    suspend fun stopCharging(
        @Header("sid") sid: String,
        @Header("vinkey") vehicleKey: String
    ): Response<ResponseBody>

    @POST("evc/sts")
    suspend fun setChargeTargets(
        @Header("sid") sid: String,
        @Header("vinkey") vehicleKey: String,
        @Body body: JsonObject
    ): Response<ResponseBody>
}

class KiaUsApiClient(deviceId: String) {
    private val okHttpClient = baseOkHttpBuilder()
        .addInterceptor { chain ->
            val offsetHours = TimeZone.getDefault().rawOffset / 3_600_000
            val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }.format(Date())
            val request = chain.request().newBuilder()
                .header("content-type", "application/json;charset=UTF-8")
                .header("accept", "application/json, text/plain, */*")
                .header("accept-encoding", "gzip, deflate, br")
                .header("accept-language", "en-US,en;q=0.9")
                .header("apptype", "L")
                .header("appversion", "4.10.0")
                .header("clientid", "MWAMOBILE")
                .header("from", "SPA")
                .header("host", "api.owners.kia.com")
                .header("language", "0")
                .header("offset", offsetHours.toString())
                .header("ostype", "Android")
                .header("osversion", "11")
                .header("secretkey", "98er-w34rf-ibf3-3f6h")
                .header("to", "APIGW")
                .header("tokentype", "G")
                .header("user-agent", "okhttp/3.12.1")
                .header("date", date)
                .header("deviceid", deviceId)
                .build()
            chain.proceed(request)
        }
        .build()

    val apiService: KiaUsApiService = buildRetrofit(BluelinkConstants.BASE_URL_US_KIA, okHttpClient)
        .create(KiaUsApiService::class.java)
}
