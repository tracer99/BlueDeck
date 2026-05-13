package com.bluebridge.android.data.api

import com.bluebridge.android.data.repository.Result
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Hyundai / Kia Canada TODS JSON API (same paths, different host + Origin / Referer).
 * Reference: bluelinky `src/constants/canada.ts` and `CanadianController` / `CanadianVehicle`.
 */
class CanadaTodsClient(region: Region) {

    private val gson = Gson()
    private val origin: String = when (region) {
        Region.CA_HYUNDAI -> "https://mybluelink.ca"
        Region.CA_KIA -> "https://kiaconnect.ca"
        else -> throw IllegalArgumentException("Not a Canadian TODS region: $region")
    }

    private val refererLogin = "$origin/login"
    private val client = OkHttpClient.Builder()
        .connectTimeout(BluelinkConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(BluelinkConstants.COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(BluelinkConstants.TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private fun offsetHours(): String {
        val offset = ZoneId.systemDefault().rules.getOffset(Instant.now())
        return (offset.totalSeconds / 3600).toString()
    }

    private fun url(path: String): String = "$origin$path"

    suspend fun login(loginId: String, password: String): Result<CaLoginResult> = withContext(Dispatchers.IO) {
        val body = JsonObject().apply {
            addProperty("loginId", loginId)
            addProperty("password", password)
        }
        executePost(url("/tods/api/lgn"), accessToken = "", vehicleId = null, pAuth = null, body = body)
            .mapEnvelope { gson.fromJson(it, CaLoginResult::class.java) }
    }

    suspend fun vehicleList(accessToken: String): Result<CaVehicleListResult> = withContext(Dispatchers.IO) {
        executePost(url("/tods/api/vhcllst"), accessToken, null, null, JsonObject())
            .mapEnvelope { gson.fromJson(it, CaVehicleListResult::class.java) }
    }

    suspend fun verifyPin(accessToken: String, vehicleId: String, pin: String): Result<CaVerifyPinResult> =
        withContext(Dispatchers.IO) {
            val body = JsonObject().apply { addProperty("pin", pin) }
            executePost(url("/tods/api/vrfypin"), accessToken, vehicleId, null, body)
                .mapEnvelope { gson.fromJson(it, CaVerifyPinResult::class.java) }
        }

    suspend fun status(accessToken: String, vehicleId: String, pin: String, forceRefresh: Boolean): Result<JsonObject> =
        withContext(Dispatchers.IO) {
            val pAuth = when (val v = verifyPin(accessToken, vehicleId, pin)) {
                is Result.Success -> v.data.pAuth
                is Result.Error -> return@withContext Result.Error(v.message, v.code)
                else -> return@withContext Result.Error("PIN verification failed")
            }
            val path = if (forceRefresh) "/tods/api/rltmvhclsts" else "/tods/api/lstvhclsts"
            val body = JsonObject().apply { addProperty("pin", pin) }
            executePost(url(path), accessToken, vehicleId, pAuth, body).mapEnvelope { it.asJsonObject }
        }

    suspend fun locate(accessToken: String, vehicleId: String, pin: String): Result<JsonObject> =
        withContext(Dispatchers.IO) {
            val pAuth = when (val v = verifyPin(accessToken, vehicleId, pin)) {
                is Result.Success -> v.data.pAuth
                is Result.Error -> return@withContext Result.Error(v.message, v.code)
                else -> return@withContext Result.Error("PIN verification failed")
            }
            val body = JsonObject().apply { addProperty("pin", pin) }
            executePost(url("/tods/api/fndmcr"), accessToken, vehicleId, pAuth, body).mapEnvelope { it.asJsonObject }
        }

    suspend fun lock(accessToken: String, vehicleId: String, pin: String): Result<Unit> =
        command(accessToken, vehicleId, pin, "/tods/api/drlck", JsonObject())

    suspend fun unlock(accessToken: String, vehicleId: String, pin: String): Result<Unit> =
        command(accessToken, vehicleId, pin, "/tods/api/drulck", JsonObject())

    suspend fun remoteStart(accessToken: String, vehicleId: String, pin: String, hvac: JsonObject): Result<Unit> =
        command(accessToken, vehicleId, pin, "/tods/api/evc/rfon", JsonObject().apply { add("hvacInfo", hvac) })

    suspend fun remoteStop(accessToken: String, vehicleId: String, pin: String): Result<Unit> =
        command(accessToken, vehicleId, pin, "/tods/api/evc/rfoff", JsonObject())

    suspend fun startCharge(accessToken: String, vehicleId: String, pin: String): Result<Unit> =
        command(accessToken, vehicleId, pin, "/tods/api/evc/rcstrt", JsonObject())

    suspend fun stopCharge(accessToken: String, vehicleId: String, pin: String): Result<Unit> =
        command(accessToken, vehicleId, pin, "/tods/api/evc/rcstp", JsonObject())

    suspend fun setChargeTargets(
        accessToken: String,
        vehicleId: String,
        pin: String,
        fast: Int,
        slow: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val pAuth = when (val v = verifyPin(accessToken, vehicleId, pin)) {
            is Result.Success -> v.data.pAuth
            is Result.Error -> return@withContext Result.Error(v.message, v.code)
            else -> return@withContext Result.Error("PIN verification failed")
        }
        val tsoc = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("plugType", 0)
                addProperty("level", fast)
            })
            add(JsonObject().apply {
                addProperty("plugType", 1)
                addProperty("level", slow)
            })
        }
        val body = JsonObject().apply {
            addProperty("pin", pin)
            add("tsoc", tsoc)
        }
        executePost(url("/tods/api/evc/setsoc"), accessToken, vehicleId, pAuth, body).toUnitResult()
    }

    suspend fun hornLight(accessToken: String, vehicleId: String, pin: String, withHorn: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val pAuth = when (val v = verifyPin(accessToken, vehicleId, pin)) {
                is Result.Success -> v.data.pAuth
                is Result.Error -> return@withContext Result.Error(v.message, v.code)
                else -> return@withContext Result.Error("PIN verification failed")
            }
            val body = JsonObject().apply {
                addProperty("pin", pin)
                addProperty("horn", withHorn)
            }
            executePost(url("/tods/api/hornlight"), accessToken, vehicleId, pAuth, body).toUnitResult()
        }

    private suspend fun command(
        accessToken: String,
        vehicleId: String,
        pin: String,
        path: String,
        body: JsonObject
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val pAuth = when (val v = verifyPin(accessToken, vehicleId, pin)) {
            is Result.Success -> v.data.pAuth
            is Result.Error -> return@withContext Result.Error(v.message, v.code)
            else -> return@withContext Result.Error("PIN verification failed")
        }
        val merged = gson.fromJson(body, JsonObject::class.java).apply {
            if (!has("pin")) addProperty("pin", pin)
        }
        executePost(url(path), accessToken, vehicleId, pAuth, merged).toUnitResult()
    }

    private fun executePost(
        url: String,
        accessToken: String,
        vehicleId: String?,
        pAuth: String?,
        body: JsonObject
    ): Result<CaEnvelope> {
        return try {
            val json = gson.toJson(body)
            val reqBuilder = Request.Builder()
                .url(url)
                .post(json.toRequestBody(JSON))
                .header("Content-Type", "application/json")
                .header("from", "SPA")
                .header("language", "1")
                .header("offset", offsetHours())
                .header("accessToken", accessToken)
                .header("Origin", origin)
                .header("Referer", refererLogin)
            if (!vehicleId.isNullOrBlank()) reqBuilder.header("vehicleId", vehicleId)
            if (!pAuth.isNullOrBlank()) reqBuilder.header("pAuth", pAuth)
            val resp = client.newCall(reqBuilder.build()).execute()
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return Result.Error("HTTP ${resp.code}: ${text.take(200)}", resp.code)
            }
            val envelope = gson.fromJson(text, CaEnvelope::class.java)
            val code = envelope.responseHeader?.responseCode ?: 0
            if (code != 0) {
                val desc = envelope.responseHeader?.responseDesc ?: "TODS error $code"
                return Result.Error(desc, code)
            }
            Result.Success(envelope)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    private fun <T> Result<CaEnvelope>.mapEnvelope(mapper: (com.google.gson.JsonElement) -> T): Result<T> {
        return when (this) {
            is Result.Success -> {
                val el = data.result ?: return Result.Error("Empty TODS result")
                try {
                    Result.Success(mapper(el))
                } catch (e: Exception) {
                    Result.Error(e.message ?: "Parse error")
                }
            }
            is Result.Error -> Result.Error(message, code)
            is Result.Loading -> Result.Error("Unexpected loading state")
        }
    }

    private fun Result<CaEnvelope>.toUnitResult(): Result<Unit> {
        return when (this) {
            is Result.Success -> {
                if (data.result == null) Result.Error("Empty TODS result")
                else Result.Success(Unit)
            }
            is Result.Error -> Result.Error(message, code)
            is Result.Loading -> Result.Error("Unexpected loading state")
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
