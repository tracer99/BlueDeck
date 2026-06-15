package com.blueandroid.data.api

import com.google.gson.JsonObject
import com.blueandroid.data.models.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface BluelinkApiService {

    @POST("v2/ac/oauth/token")
    suspend fun getToken(@Body request: LoginRequest): Response<TokenResponse>

    @GET("ac/v2/enrollment/details/{username}")
    suspend fun getVehicles(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("payloadGenerated") payloadGenerated: String = "20200226171938",
        @Header("includeNonConnectedVehicles") includeNonConnectedVehicles: String = "Y",
        @Path("username") username: String
    ): Response<JsonObject>

    @GET("ac/v2/rcs/rvs/vehicleStatus")
    suspend fun getVehicleStatus(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("REFRESH") refresh: String = "false",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-5",
        @Header("Host") host: String = BluelinkConstants.API_HOST
    ): Response<VehicleStatus>



    @GET("ac/v2/rcs/rfc/findMyCar")
    suspend fun getVehicleLocation(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-5",
        @Header("Host") host: String = BluelinkConstants.API_HOST
    ): Response<VehicleLocation>

    @FormUrlEncoded
    @POST("ac/v2/rcs/rdo/off")
    suspend fun lockDoors(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Field("userName") formUserName: String,
        @Field("vin") formVin: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("ac/v2/rcs/rdo/on")
    suspend fun unlockDoors(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Field("userName") formUserName: String,
        @Field("vin") formVin: String
    ): Response<ResponseBody>

    @POST("ac/v2/rcs/rsc/start")
    suspend fun startEngine(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Body request: RemoteStartRequest
    ): Response<ResponseBody>

    @POST("ac/v2/evc/fatc/start")
    suspend fun startEvClimate(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Body request: RemoteStartRequest
    ): Response<ResponseBody>

    @POST("ac/v2/rcs/rsc/stop")
    suspend fun stopEngine(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST
    ): Response<ResponseBody>


    @POST("ac/v2/evc/fatc/stop")
    suspend fun stopEvClimate(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST
    ): Response<ResponseBody>

    @POST("ac/v2/rcs/rfc/start")
    suspend fun startClimate(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Body request: RemoteStartRequest
    ): Response<ResponseBody>

    @POST("ac/v2/rcs/rfc/stop")
    suspend fun stopClimate(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Body request: LockUnlockRequest
    ): Response<CommandResponse>


    @POST("api/v2/spa/vehicles/{vehicleId}/control/charge")
    suspend fun controlCharging(
        @Path("vehicleId") vehicleId: String,
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Body request: ChargeControlRequest
    ): Response<ResponseBody>

    @POST("ac/v2/evc/charge/start")
    suspend fun startCharging(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Body request: EVChargeRequest
    ): Response<ResponseBody>

    @POST("ac/v2/evc/charge/stop")
    suspend fun stopCharging(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Body request: EVChargeRequest
    ): Response<ResponseBody>

    @GET("api/v2/spa/vehicles/{vehicleId}/charge/target")
    suspend fun getSpaChargeTargets(
        @Path("vehicleId") vehicleId: String,
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST
    ): Response<ChargeTargetResponse>

    @POST("ac/v2/evc/charge/reserv/set")
    suspend fun setChargeSchedule(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Body request: JsonObject
    ): Response<ResponseBody>

    @POST("ac/v2/evc/charge/targetsoc/set")
    suspend fun setSpaChargeTargets(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Body request: SpaChargeTargetRequest
    ): Response<CommandResponse>

    @PUT("ac/v2/evc/soc")
    suspend fun setChargeTargets(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Body request: ChargeTargetRequest
    ): Response<CommandResponse>

    @FormUrlEncoded
    @POST("ac/v2/rcs/rfc/horn")
    suspend fun hornAndLights(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Field("userName") formUserName: String,
        @Field("vin") formVin: String
    ): Response<CommandResponse>

    @FormUrlEncoded
    @POST("ac/v2/rcs/rfc/light")
    suspend fun flashLightsOnly(
        @Header("access_token") accessToken: String,
        @Header("client_id") clientId: String = BluelinkConstants.CLIENT_ID,
        @Header("username") username: String,
        @Header("vin") vin: String,
        @Header("APPCLOUD-VIN") appCloudVin: String,
        @Header("bluelinkservicepin") servicePin: String = "",
        @Header("registrationId") registrationId: String = "",
        @Header("gen") gen: String = "3",
        @Header("brandIndicator") brandIndicator: String = "H",
        @Header("User-Agent") userAgent: String = "okhttp/3.12.0",
        @Header("Language") language: String = "0",
        @Header("to") to: String = "ISS",
        @Header("from") from: String = "SPA",
        @Header("encryptFlag") encryptFlag: String = "false",
        @Header("offset") offset: String = "-4",
        @Header("Host") host: String = BluelinkConstants.API_HOST,
        @Field("userName") formUserName: String,
        @Field("vin") formVin: String
    ): Response<CommandResponse>

}