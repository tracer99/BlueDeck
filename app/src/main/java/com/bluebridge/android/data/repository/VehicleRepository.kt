package com.bluebridge.android.data.repository

import com.bluebridge.android.data.api.ApiClient
import com.bluebridge.android.data.api.CanadaClimateCodes
import com.bluebridge.android.data.api.CanadaStatusMapper
import com.bluebridge.android.data.api.CanadaTodsClient
import com.bluebridge.android.data.api.CaVehicleJson
import com.bluebridge.android.data.api.Region
import com.bluebridge.android.data.api.UsSpaHeaders
import com.bluebridge.android.data.api.usesCanadianTods
import com.bluebridge.android.data.models.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.ResponseBody

import javax.inject.Singleton

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val code: Int? = null) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

@Singleton
class VehicleRepository @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val secureCredentialsManager: SecureCredentialsManager
) {
    private var cachedSpaKey: Pair<String, ApiClient>? = null
    private var cachedCanadaKey: Pair<String, CanadaTodsClient>? = null

    private suspend fun currentRegion(): Region =
        Region.valueOf(preferencesManager.region.first())

    private suspend fun getApiService() = run {
        val region = currentRegion()
        if (region.usesCanadianTods()) {
            error("US SPA API not used for Canadian regions")
        }
        val key = region.name
        val client = cachedSpaKey?.takeIf { it.first == key }?.second ?: ApiClient(
            baseUrl = region.baseUrl,
            spaHeaders = when (region) {
                Region.US_KIA -> UsSpaHeaders.KIA
                else -> UsSpaHeaders.HYUNDAI
            }
        ).also { cachedSpaKey = key to it }
        client.apiService
    }

    private suspend fun getCanadaClient(): CanadaTodsClient {
        val region = currentRegion()
        require(region.usesCanadianTods()) { "Not a Canadian TODS region" }
        val key = region.name
        return cachedCanadaKey?.takeIf { it.first == key }?.second
            ?: CanadaTodsClient(region).also { cachedCanadaKey = key to it }
    }

    private suspend fun ensureCanadaSessionFresh() {
        val region = currentRegion()
        if (!region.usesCanadianTods()) return
        val exp = preferencesManager.tokenExpiryMillis()
        if (exp > System.currentTimeMillis() + 120_000L) return
        val creds = secureCredentialsManager.getSavedCredentials() ?: return
        login(creds.username, creds.password, creds.servicePin)
    }

    private fun CaVehicleJson.toVehicle(region: Region): Vehicle = Vehicle(
        vin = vin,
        vehicleIdentifier = vehicleId,
        regId = regid,
        generation = genType.ifBlank { "3" },
        nickname = nickName,
        modelName = modelName,
        modelYear = modelYear,
        modelCode = modelCode,
        brandIndicator = brandIndicator.ifBlank {
            if (region == Region.CA_KIA) "K" else "H"
        }
    )

    private suspend fun getToken() = preferencesManager.accessToken.first()
        ?: throw IllegalStateException("Not authenticated")

    private suspend fun getUsername() = preferencesManager.username.first()
        ?: throw IllegalStateException("Not authenticated")

    private suspend fun getServicePin(required: Boolean = true): String {
        val pin = preferencesManager.servicePin.first().orEmpty().trim()
        if (required && pin.isBlank()) {
            throw IllegalStateException("Bluelink PIN is required. Add your 4-digit PIN in Settings > Account.")
        }
        return pin
    }

    private fun validateCommandResponse(response: retrofit2.Response<CommandResponse>, actionName: String): Result<Unit> {
        val body = response.body()
        if (!response.isSuccessful) {
            return Result.Error("$actionName failed (${response.code()})", response.code())
        }
        if (body?.isInvalidPin == true) {
            val attempts = body.remainingAttemptCount?.takeIf { it.isNotBlank() }
            val suffix = attempts?.let { " $it attempt(s) remaining." }.orEmpty()
            return Result.Error("Incorrect Bluelink PIN.$suffix Update it in Settings > Account before trying again.")
        }
        body?.userMessage?.let { message ->
            if (message.contains("invalid", ignoreCase = true) ||
                message.contains("failed", ignoreCase = true) ||
                message.contains("error", ignoreCase = true)
            ) {
                return Result.Error(message)
            }
        }
        return Result.Success(Unit)
    }

    private fun validateEmptyCommandResponse(response: retrofit2.Response<ResponseBody>, actionName: String): Result<Unit> {
        if (!response.isSuccessful) {
            return Result.Error("$actionName failed (${response.code()})", response.code())
        }
        return Result.Success(Unit)
    }

    private fun validateRawCommandResponse(response: retrofit2.Response<ResponseBody>, actionName: String): Result<Unit> {
        val rawBody = try {
            response.body()?.string().orEmpty()
        } catch (_: Exception) {
            ""
        }

        val rawErrorBody = try {
            response.errorBody()?.string().orEmpty()
        } catch (_: Exception) {
            ""
        }

        if (!response.isSuccessful) {
            val serverMessage = extractCommandErrorMessage(rawErrorBody)
            return Result.Error(serverMessage ?: "$actionName failed (${response.code()})", response.code())
        }

        if (rawBody.isBlank()) {
            return Result.Success(Unit)
        }

        extractCommandErrorMessage(rawBody)?.let { message ->
            return Result.Error(message)
        }

        return Result.Success(Unit)
    }

    private fun extractCommandErrorMessage(rawJson: String): String? {
        if (rawJson.isBlank()) return null

        return try {
            val element = JsonParser.parseString(rawJson)
            if (!element.isJsonObject) return null
            val json = element.asJsonObject

            val pinValid = json.stringOrNull("isBlueLinkServicePinValid")
            if (pinValid?.equals("invalid", ignoreCase = true) == true) {
                val attempts = json.stringOrNull("remainingAttemptCount")?.takeIf { it.isNotBlank() }
                val suffix = attempts?.let { " $it attempt(s) remaining." }.orEmpty()
                return "Incorrect Bluelink PIN.$suffix Update it in Settings > Account before trying again."
            }

            val invalidAttempt = json.stringOrNull("invalidAttemptMessage")
            if (!invalidAttempt.isNullOrBlank()) {
                return invalidAttempt
            }

            val errorMessage = json.stringOrNull("errorMessage")
                ?: json.stringOrNull("message")
                ?: json.stringOrNull("error")

            errorMessage?.takeIf { message ->
                message.contains("invalid", ignoreCase = true) ||
                    message.contains("failed", ignoreCase = true) ||
                    message.contains("error", ignoreCase = true) ||
                    message.contains("incorrect", ignoreCase = true)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asString }.getOrNull()
    }

    // ─── Auth ──────────────────────────────────────────────────────────────────

    suspend fun login(username: String, password: String, servicePin: String = ""): Result<Unit> {
        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                val ca = getCanadaClient()
                when (val r = ca.login(username, password)) {
                    is Result.Success -> {
                        val t = r.data
                        val expiresIn = t.expireInSeconds.takeIf { it > 0 }?.toInt() ?: 1799
                        preferencesManager.saveSession(
                            accessToken = t.accessToken,
                            refreshToken = t.refreshToken,
                            username = username,
                            expiresIn = expiresIn,
                            servicePin = servicePin
                        )
                        Result.Success(Unit)
                    }
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Login failed")
                }
            } else {
                val response = getApiService().getToken(
                    LoginRequest(username = username, password = password)
                )
                if (response.isSuccessful) {
                    val token = response.body() ?: return Result.Error("Empty response")
                    preferencesManager.saveSession(
                        accessToken = token.accessToken,
                        refreshToken = token.refreshToken,
                        username = username,
                        expiresIn = token.expiresIn.toIntOrNull() ?: 1799,
                        servicePin = servicePin
                    )
                    Result.Success(Unit)
                } else {
                    Result.Error(
                        when (response.code()) {
                            401 -> "Invalid username or password"
                            403 -> "Account locked. Please use the Bluelink app to unlock."
                            429 -> "Too many attempts. Please wait before trying again."
                            else -> "Login failed (${response.code()})"
                        },
                        response.code()
                    )
                }
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error. Check your connection.")
        }
    }

    suspend fun logout(requirePassword: Boolean = true) {
        preferencesManager.clearSession(requirePassword = requirePassword)
        cachedSpaKey = null
        cachedCanadaKey = null
    }

    // ─── Vehicles ──────────────────────────────────────────────────────────────

    suspend fun getVehicles(): Result<List<Vehicle>> {
        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                ensureCanadaSessionFresh()
                val token = getToken()
                val ca = getCanadaClient()
                when (val r = ca.vehicleList(token)) {
                    is Result.Success -> {
                        val vehicles = r.data.vehicles.map { it.toVehicle(region) }
                        Result.Success(vehicles)
                    }
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Failed to fetch vehicles")
                }
            } else {
                val token = getToken()
                val username = getUsername()
                val response = getApiService().getVehicles(
                    accessToken = token,
                    username = username
                )
                if (response.isSuccessful) {
                    val vehicles = response.body()?.vehicles?.map { detail ->
                        detail.vehicle.copy(packageDetails = detail.packageDetails)
                    } ?: emptyList()
                    Result.Success(vehicles)
                } else {
                    Result.Error("Failed to fetch vehicles (${response.code()})")
                }
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    // ─── Status ────────────────────────────────────────────────────────────────

    suspend fun getVehicleStatus(
        vin: String,
        forceRefresh: Boolean = false,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H"
    ): Result<VehicleStatusData> {
        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                ensureCanadaSessionFresh()
                val token = getToken()
                val vehicleId = registrationId.ifBlank { vin }
                val pin = getServicePin(required = false)
                if (pin.isBlank()) {
                    return Result.Error("Bluelink PIN is required for status in Canada. Add it in Settings > Account.")
                }
                val ca = getCanadaClient()
                when (val r = ca.status(token, vehicleId, pin, forceRefresh)) {
                    is Result.Success -> {
                        val data = CanadaStatusMapper.mapStatusFromResult(r.data)
                            ?: return Result.Error("Could not parse vehicle status")
                        preferencesManager.setLastStatusRefresh(System.currentTimeMillis())
                        Result.Success(data)
                    }
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Status fetch failed")
                }
            } else {
                val token = getToken()
                val username = getUsername()
                val api = getApiService()

                val response = api.getVehicleStatus(
                    accessToken = token,
                    username = username,
                    vin = vin,
                    appCloudVin = vin,
                    refresh = forceRefresh.toString(),
                    registrationId = registrationId,
                    gen = generation,
                    brandIndicator = brandIndicator
                )

                if (response.isSuccessful) {
                    val data = response.body()?.vehicleStatus
                        ?: return Result.Error("Could not parse vehicle status")
                    preferencesManager.setLastStatusRefresh(System.currentTimeMillis())
                    Result.Success(data)
                } else {
                    Result.Error("Status fetch failed (${response.code()})")
                }
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }


    suspend fun getVehicleLocation(
        vin: String,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H"
    ): Result<VehicleLocation> {
        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                ensureCanadaSessionFresh()
                val token = getToken()
                val vehicleId = registrationId.ifBlank { vin }
                val pin = getServicePin()
                val ca = getCanadaClient()
                when (val r = ca.locate(token, vehicleId, pin)) {
                    is Result.Success -> {
                        val location = CanadaStatusMapper.mapLocation(r.data)
                            ?: return Result.Error("Could not parse vehicle location")
                        Result.Success(location)
                    }
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Location fetch failed")
                }
            } else {
                val token = getToken()
                val username = getUsername()
                val response = getApiService().getVehicleLocation(
                    accessToken = token,
                    username = username,
                    vin = vin,
                    appCloudVin = vin,
                    registrationId = registrationId,
                    gen = generation,
                    brandIndicator = brandIndicator
                )

                if (response.isSuccessful) {
                    val location = response.body()
                        ?: return Result.Error("Could not parse vehicle location")
                    Result.Success(location)
                } else {
                    Result.Error("Location fetch failed (${response.code()})")
                }
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    // ─── Lock / Unlock ─────────────────────────────────────────────────────────

    suspend fun lockDoors(
        vin: String,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H"
    ): Result<Unit> {
        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                ensureCanadaSessionFresh()
                val token = getToken()
                val vehicleId = registrationId.ifBlank { vin }
                val pin = getServicePin(required = false)
                val ca = getCanadaClient()
                when (val r = ca.lock(token, vehicleId, pin)) {
                    is Result.Success -> Result.Success(Unit)
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Lock failed")
                }
            } else {
                val token = getToken()
                val username = getUsername()
                val pin = getServicePin(required = false)
                val response = getApiService().lockDoors(
                    accessToken = token,
                    username = username,
                    vin = vin,
                    appCloudVin = vin,
                    servicePin = pin,
                    registrationId = registrationId,
                    gen = generation,
                    brandIndicator = brandIndicator,
                    formUserName = username,
                    formVin = vin
                )
                validateRawCommandResponse(response, "Lock")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun unlockDoors(
        vin: String,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H"
    ): Result<Unit> {
        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                ensureCanadaSessionFresh()
                val token = getToken()
                val vehicleId = registrationId.ifBlank { vin }
                val pin = getServicePin()
                val ca = getCanadaClient()
                when (val r = ca.unlock(token, vehicleId, pin)) {
                    is Result.Success -> Result.Success(Unit)
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Unlock failed")
                }
            } else {
                val token = getToken()
                val username = getUsername()
                val pin = getServicePin()
                val response = getApiService().unlockDoors(
                    accessToken = token,
                    username = username,
                    vin = vin,
                    appCloudVin = vin,
                    servicePin = pin,
                    registrationId = registrationId,
                    gen = generation,
                    brandIndicator = brandIndicator,
                    formUserName = username,
                    formVin = vin
                )
                validateRawCommandResponse(response, "Unlock")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    // ─── Remote Start / Stop ───────────────────────────────────────────────────

    suspend fun startEngine(
        vin: String,
        tempF: String = "72",
        hvacOn: Boolean = true,
        defrost: Boolean = false,
        heatedSteering: Boolean = false,
        driverSeatHeat: Int = 2,
        passengerSeatHeat: Int = 2,
        rearLeftSeatHeat: Int = 2,
        rearRightSeatHeat: Int = 2,
        durationMinutes: Int = 10,
        isEv: Boolean = false,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H"
    ): Result<Unit> {
        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                ensureCanadaSessionFresh()
                val token = getToken()
                val vehicleId = registrationId.ifBlank { vin }
                val pin = getServicePin()
                val tempUnit = preferencesManager.temperatureUnit.first()
                val degC = when (tempUnit) {
                    "C" -> tempF.toDoubleOrNull() ?: 22.0
                    else -> CanadaClimateCodes.fahrenheitToCelsius(tempF.toDoubleOrNull() ?: 72.0)
                }
                val tempCode = CanadaClimateCodes.celsiusToTempCode(degC)
                val hvac = com.google.gson.JsonObject().apply {
                    addProperty("airCtrl", if (hvacOn || defrost) 1 else 0)
                    addProperty("defrost", defrost)
                    addProperty("heating1", if (heatedSteering) 1 else 0)
                    add("airTemp", com.google.gson.JsonObject().apply {
                        addProperty("value", tempCode)
                        addProperty("unit", 0)
                        addProperty("hvacTempType", 1)
                    })
                }
                val ca = getCanadaClient()
                when (val r = ca.remoteStart(token, vehicleId, pin, hvac)) {
                    is Result.Success -> Result.Success(Unit)
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Start failed")
                }
            } else {
                val token = getToken()
                val username = getUsername()
                val pin = getServicePin()
                val request = RemoteStartRequest(
                    airCtrl = if (hvacOn) 1 else 0,
                    airTemp = AirTempRequest(value = tempF, unit = 1),
                    defrost = defrost,
                    heating1 = if (heatedSteering) 1 else 0,
                    igniOnDuration = durationMinutes,
                    seatHeaterVentInfo = SeatInfo(
                        driverSeatHeatCool = driverSeatHeat,
                        passengerSeatHeatCool = passengerSeatHeat,
                        rearLeftSeatHeatCool = rearLeftSeatHeat,
                        rearRightSeatHeatCool = rearRightSeatHeat
                    ),
                    username = username,
                    vin = vin
                )
                val response = if (isEv) {
                    getApiService().startEvClimate(
                        accessToken = token,
                        username = username,
                        vin = vin,
                        appCloudVin = vin,
                        servicePin = pin,
                        registrationId = registrationId,
                        gen = generation,
                        brandIndicator = brandIndicator,
                        request = request
                    )
                } else {
                    getApiService().startEngine(
                        accessToken = token,
                        username = username,
                        vin = vin,
                        appCloudVin = vin,
                        servicePin = pin,
                        registrationId = registrationId,
                        gen = generation,
                        brandIndicator = brandIndicator,
                        request = request
                    )
                }
                validateRawCommandResponse(response, "Start")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun stopEngine(
        vin: String,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H"
    ): Result<Unit> {
        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                ensureCanadaSessionFresh()
                val token = getToken()
                val vehicleId = registrationId.ifBlank { vin }
                val pin = getServicePin()
                val ca = getCanadaClient()
                when (val r = ca.remoteStop(token, vehicleId, pin)) {
                    is Result.Success -> Result.Success(Unit)
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Stop failed")
                }
            } else {
                val token = getToken()
                val username = getUsername()
                val pin = getServicePin()
                val response = getApiService().stopEngine(
                    accessToken = token,
                    username = username,
                    vin = vin,
                    appCloudVin = vin,
                    servicePin = pin,
                    registrationId = registrationId,
                    gen = generation,
                    brandIndicator = brandIndicator
                )
                validateRawCommandResponse(response, "Stop")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }


    private suspend fun stopEvClimate(
        vin: String,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H"
    ): Result<Unit> {
        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                ensureCanadaSessionFresh()
                val token = getToken()
                val vehicleId = registrationId.ifBlank { vin }
                val pin = getServicePin()
                val ca = getCanadaClient()
                when (val r = ca.remoteStop(token, vehicleId, pin)) {
                    is Result.Success -> Result.Success(Unit)
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Stop Climate failed")
                }
            } else {
                val token = getToken()
                val username = getUsername()
                val pin = getServicePin()
                val response = getApiService().stopEvClimate(
                    accessToken = token,
                    username = username,
                    vin = vin,
                    appCloudVin = vin,
                    servicePin = pin,
                    registrationId = registrationId,
                    gen = generation,
                    brandIndicator = brandIndicator
                )
                validateRawCommandResponse(response, "Stop Climate")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    // ─── Climate ───────────────────────────────────────────────────────────────

    suspend fun startClimate(
        vin: String,
        tempF: String = "72",
        defrost: Boolean = false,
        driverSeatHeat: Int = 2,
        passengerSeatHeat: Int = 2,
        rearLeftSeatHeat: Int = 2,
        rearRightSeatHeat: Int = 2,
        isEv: Boolean = false,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H"
    ): Result<Unit> {
        // Hyundai USA uses remote-start/preconditioning for cabin climate.
        // EVs go through /ac/v2/evc/fatc/start; gas vehicles through /ac/v2/rcs/rsc/start.
        // Seat heat/vent values use Hyundai's reported level codes:
        // 2/off, 6-8 heat low/medium/high, 3-5 vent low/medium/high.
        return startEngine(
            vin = vin,
            tempF = tempF,
            hvacOn = true,
            defrost = defrost,
            driverSeatHeat = driverSeatHeat,
            passengerSeatHeat = passengerSeatHeat,
            rearLeftSeatHeat = rearLeftSeatHeat,
            rearRightSeatHeat = rearRightSeatHeat,
            isEv = isEv,
            registrationId = registrationId,
            generation = generation,
            brandIndicator = brandIndicator
        )
    }

    suspend fun stopClimate(
        vin: String,
        isEv: Boolean = false,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H"
    ): Result<Unit> {
        // IONIQ EV climate preconditioning should stop through the EV FATC route.
        // The non-EV rsc/stop route returns unsupported for the IONIQ 9.
        return if (isEv) {
            stopEvClimate(
                vin = vin,
                registrationId = registrationId,
                generation = generation,
                brandIndicator = brandIndicator
            )
        } else {
            stopEngine(
                vin = vin,
                registrationId = registrationId,
                generation = generation,
                brandIndicator = brandIndicator
            )
        }
    }

    // ─── EV Charging ───────────────────────────────────────────────────────────

    suspend fun startCharging(
        vin: String,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H",
        vehicleId: String = ""
    ): Result<Unit> {
        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                ensureCanadaSessionFresh()
                val token = getToken()
                val vid = registrationId.ifBlank { vin }
                val pin = getServicePin()
                val ca = getCanadaClient()
                when (val r = ca.startCharge(token, vid, pin)) {
                    is Result.Success -> Result.Success(Unit)
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Charge start failed")
                }
            } else {
                val token = getToken()
                val username = getUsername()
                val pin = getServicePin()

                val response = getApiService().startCharging(
                    accessToken = token,
                    username = username,
                    vin = vin,
                    appCloudVin = vin,
                    servicePin = pin,
                    registrationId = registrationId,
                    gen = generation,
                    brandIndicator = brandIndicator,
                    request = EVChargeRequest(userName = username, vin = vin, action = "start")
                )
                validateEmptyCommandResponse(response, "Charge start")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun stopCharging(
        vin: String,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H",
        vehicleId: String = ""
    ): Result<Unit> {
        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                ensureCanadaSessionFresh()
                val token = getToken()
                val vid = registrationId.ifBlank { vin }
                val pin = getServicePin()
                val ca = getCanadaClient()
                when (val r = ca.stopCharge(token, vid, pin)) {
                    is Result.Success -> Result.Success(Unit)
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Charge stop failed")
                }
            } else {
                val token = getToken()
                val username = getUsername()
                val pin = getServicePin()

                val response = getApiService().stopCharging(
                    accessToken = token,
                    username = username,
                    vin = vin,
                    appCloudVin = vin,
                    servicePin = pin,
                    registrationId = registrationId,
                    gen = generation,
                    brandIndicator = brandIndicator,
                    request = EVChargeRequest(userName = username, vin = vin, action = "stop")
                )
                validateEmptyCommandResponse(response, "Charge stop")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun setChargeTarget(
        vin: String,
        acTarget: Int = 90,
        dcTarget: Int = 80,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H",
        vehicleId: String = ""
    ): Result<Unit> {
        val allowedTargets = setOf(50, 60, 70, 80, 90, 100)
        if (acTarget !in allowedTargets || dcTarget !in allowedTargets) {
            return Result.Error("Charge targets must be one of 50, 60, 70, 80, 90, or 100%")
        }

        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                ensureCanadaSessionFresh()
                val token = getToken()
                val vid = registrationId.ifBlank { vin }
                val pin = getServicePin()
                val ca = getCanadaClient()
                when (val r = ca.setChargeTargets(token, vid, pin, fast = dcTarget, slow = acTarget)) {
                    is Result.Success -> Result.Success(Unit)
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Set charge targets failed")
                }
            } else {
                val token = getToken()
                val username = getUsername()
                val pin = getServicePin()

                val request = SpaChargeTargetRequest(
                    targets = listOf(
                        ChargeTarget(plugType = 0, targetSoc = dcTarget),
                        ChargeTarget(plugType = 1, targetSoc = acTarget)
                    )
                )

                val response = getApiService().setSpaChargeTargets(
                    accessToken = token,
                    username = username,
                    vin = vin,
                    appCloudVin = vin,
                    servicePin = pin,
                    registrationId = registrationId,
                    gen = generation,
                    brandIndicator = brandIndicator,
                    request = request
                )

                validateCommandResponse(response, "Set charge targets")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }


    // ─── Horn / Lights ─────────────────────────────────────────────────────────

    suspend fun hornAndLights(
        vin: String,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H"
    ): Result<Unit> {
        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                ensureCanadaSessionFresh()
                val token = getToken()
                val vehicleId = registrationId.ifBlank { vin }
                val pin = getServicePin()
                val ca = getCanadaClient()
                when (val r = ca.hornLight(token, vehicleId, pin, withHorn = true)) {
                    is Result.Success -> Result.Success(Unit)
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Horn/lights failed")
                }
            } else {
                val token = getToken()
                val username = getUsername()
                val pin = getServicePin()
                val response = getApiService().hornAndLights(
                    accessToken = token,
                    username = username,
                    vin = vin,
                    appCloudVin = vin,
                    servicePin = pin,
                    registrationId = registrationId,
                    gen = generation,
                    brandIndicator = brandIndicator,
                    formUserName = username,
                    formVin = vin
                )
                validateCommandResponse(response, "Horn/lights")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    suspend fun flashLights(
        vin: String,
        registrationId: String = "",
        generation: String = "3",
        brandIndicator: String = "H"
    ): Result<Unit> {
        return try {
            val region = currentRegion()
            if (region.usesCanadianTods()) {
                ensureCanadaSessionFresh()
                val token = getToken()
                val vehicleId = registrationId.ifBlank { vin }
                val pin = getServicePin()
                val ca = getCanadaClient()
                when (val r = ca.hornLight(token, vehicleId, pin, withHorn = false)) {
                    is Result.Success -> Result.Success(Unit)
                    is Result.Error -> Result.Error(r.message, r.code)
                    else -> Result.Error("Flash lights failed")
                }
            } else {
                val token = getToken()
                val username = getUsername()
                val pin = getServicePin()
                val response = getApiService().flashLightsOnly(
                    accessToken = token,
                    username = username,
                    vin = vin,
                    appCloudVin = vin,
                    servicePin = pin,
                    registrationId = registrationId,
                    gen = generation,
                    brandIndicator = brandIndicator,
                    formUserName = username,
                    formVin = vin
                )
                validateCommandResponse(response, "Flash lights")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    // ─── Official-app-style feature hooks ─────────────────────────────────────

    suspend fun setValetMode(vin: String, enabled: Boolean): Result<Unit> {
        return Result.Error(
            "Valet Mode is saved locally, but the Hyundai valet endpoint is not mapped in this project yet."
        )
    }

    suspend fun getSurroundViewSnapshot(vin: String): Result<SurroundViewSnapshot> {
        return Result.Error(
            "Surround View Monitor requires Hyundai camera snapshot endpoints that are not mapped in this project yet."
        )
    }

}