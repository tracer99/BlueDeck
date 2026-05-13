package com.bluebridge.android.data.api

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class CaResponseHeader(
    @SerializedName("responseCode") val responseCode: Int = 0,
    @SerializedName("responseDesc") val responseDesc: String? = null
)

data class CaApiError(
    @SerializedName("errorCode") val errorCode: String? = null,
    @SerializedName("errorDesc") val errorDesc: String? = null
)

data class CaEnvelope(
    @SerializedName("responseHeader") val responseHeader: CaResponseHeader? = null,
    @SerializedName("result") val result: JsonElement? = null,
    @SerializedName("error") val error: CaApiError? = null
)

data class CaLoginResult(
    @SerializedName("accessToken") val accessToken: String = "",
    @SerializedName("refreshToken") val refreshToken: String = "",
    /** Seconds until expiry (relative), per bluelinky CanadianController. */
    @SerializedName("expireIn") val expireInSeconds: Long = 0L
)

data class CaVerifyPinResult(
    @SerializedName("pAuth") val pAuth: String = ""
)

data class CaVehicleListResult(
    @SerializedName("vehicles") val vehicles: List<CaVehicleJson> = emptyList()
)

data class CaVehicleJson(
    @SerializedName("vin") val vin: String = "",
    @SerializedName("nickName") val nickName: String = "",
    @SerializedName("vehicleId") val vehicleId: String = "",
    @SerializedName("regid") val regid: String = "",
    @SerializedName("genType") val genType: String = "3",
    @SerializedName("brandIndicator") val brandIndicator: String = "H",
    @SerializedName("modelName") val modelName: String = "",
    @SerializedName("modelYear") val modelYear: String = "",
    @SerializedName("modelCode") val modelCode: String = ""
)
