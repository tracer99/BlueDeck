package com.bluedeck.data.repository

import com.bluedeck.data.models.CommandHistoryEntry
import com.bluedeck.data.models.WidgetVehicleSnapshot
import com.bluedeck.data.models.coerceClimateDurationMinutes
import com.bluedeck.data.obd.ObdTransportType
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.UUID
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bluedeck_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USERNAME = stringPreferencesKey("username")
        val SERVICE_PIN = stringPreferencesKey("service_pin")
        val TOKEN_EXPIRES_AT = longPreferencesKey("token_expires_at")
        val SESSION_EXPIRES_AT = longPreferencesKey("session_expires_at")
        val STAY_LOGGED_IN_30_DAYS = booleanPreferencesKey("stay_logged_in_30_days")
        val PASSWORD_REQUIRED = booleanPreferencesKey("password_required")
        val OTP_PENDING = booleanPreferencesKey("otp_pending")
        val OTP_PENDING_USERNAME = stringPreferencesKey("otp_pending_username")
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val SELECTED_VIN = stringPreferencesKey("selected_vin")
        val REGION = stringPreferencesKey("region")
        val TEMPERATURE_UNIT = stringPreferencesKey("temp_unit")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val TIME_ZONE_MODE = stringPreferencesKey("time_zone_mode")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val BIOMETRIC_UNLOCK_MODE = stringPreferencesKey("biometric_unlock_mode")
        val LAST_BIOMETRIC_UNLOCK_AT = longPreferencesKey("last_biometric_unlock_at")
        val WALK_AWAY_LOCK_ENABLED = booleanPreferencesKey("walk_away_lock_enabled")
        val WALK_AWAY_LOCK_DELAY_SECONDS = intPreferencesKey("walk_away_lock_delay_seconds")
        val WALK_AWAY_BLUETOOTH_ADDRESS = stringPreferencesKey("walk_away_bluetooth_address")
        val WALK_AWAY_BLUETOOTH_NAME = stringPreferencesKey("walk_away_bluetooth_name")
        val WALK_AWAY_LOCK_PENDING = booleanPreferencesKey("walk_away_lock_pending")
        val LAST_STATUS_REFRESH = longPreferencesKey("last_status_refresh")
        val DEFAULT_CLIMATE_TEMP = stringPreferencesKey("default_climate_temp")
        val DEFAULT_CLIMATE_DURATION_MINUTES = intPreferencesKey("default_climate_duration_minutes")
        val VALET_MODE_ENABLED = booleanPreferencesKey("valet_mode_enabled")
        val ACTIVE_DRIVER_PROFILE_ID = stringPreferencesKey("active_driver_profile_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val PROFILE_PRIMARY_PHOTO_URI = stringPreferencesKey("profile_primary_photo_uri")
        val PROFILE_GUEST_PHOTO_URI = stringPreferencesKey("profile_guest_photo_uri")
        val PROFILE_VALET_PHOTO_URI = stringPreferencesKey("profile_valet_photo_uri")
        val CUSTOM_DASHBOARD_IMAGE_URI = stringPreferencesKey("custom_dashboard_image_uri")
        val COMMAND_HISTORY_JSON = stringPreferencesKey("command_history_json")
        val SHOW_RECENT_COMMANDS = booleanPreferencesKey("show_recent_commands")
        val WIDGET_VEHICLE_NAME = stringPreferencesKey("widget_vehicle_name")
        val WIDGET_VEHICLE_VIN = stringPreferencesKey("widget_vehicle_vin")
        val WIDGET_VEHICLE_ID = stringPreferencesKey("widget_vehicle_id")
        val WIDGET_REGISTRATION_ID = stringPreferencesKey("widget_registration_id")
        val WIDGET_GENERATION = stringPreferencesKey("widget_generation")
        val WIDGET_BRAND_INDICATOR = stringPreferencesKey("widget_brand_indicator")
        val WIDGET_MODEL_CODE = stringPreferencesKey("widget_model_code")
        val WIDGET_DOORS_LOCKED = booleanPreferencesKey("widget_doors_locked")
        val WIDGET_BATTERY_PERCENT = intPreferencesKey("widget_battery_percent")
        val WIDGET_RANGE_MILES = intPreferencesKey("widget_range_miles")
        val WIDGET_CHARGING_LABEL = stringPreferencesKey("widget_charging_label")
        val WIDGET_MESSAGE = stringPreferencesKey("widget_message")
        val WIDGET_DETAIL_ONE = stringPreferencesKey("widget_detail_one")
        val WIDGET_DETAIL_TWO = stringPreferencesKey("widget_detail_two")
        val WIDGET_DETAIL_THREE = stringPreferencesKey("widget_detail_three")
        val WIDGET_UPDATED_AT = longPreferencesKey("widget_updated_at")
        val CANADA_DEVICE_ID = stringPreferencesKey("canada_device_id")
        val EU_DEVICE_ID = stringPreferencesKey("eu_device_id")
        val EU_DEVICE_REGION = stringPreferencesKey("eu_device_region")
        val AU_DEVICE_ID = stringPreferencesKey("au_device_id")
        val KIA_US_DEVICE_ID = stringPreferencesKey("kia_us_device_id")
        val OBD_TRANSPORT_TYPE = stringPreferencesKey("obd_transport_type")
        val OBD_BLUETOOTH_ADDRESS = stringPreferencesKey("obd_bluetooth_address")
        val OBD_BLUETOOTH_NAME = stringPreferencesKey("obd_bluetooth_name")
        val OBD_WIFI_HOST = stringPreferencesKey("obd_wifi_host")
        val OBD_WIFI_PORT = intPreferencesKey("obd_wifi_port")
        val OBD_PROFILE_ID = stringPreferencesKey("obd_profile_id")
        val OBD_SAMPLE_INTERVAL_SECONDS = intPreferencesKey("obd_sample_interval_seconds")
        val OBD_AUTO_CONNECT = booleanPreferencesKey("obd_auto_connect")
        val OBD_AUTO_START_LOGGING = booleanPreferencesKey("obd_auto_start_logging")
        val OBD_LOG_RETENTION_DAYS = intPreferencesKey("obd_log_retention_days")
        val OBD_LOG_MAX_STORAGE_MB = intPreferencesKey("obd_log_max_storage_mb")
        val OBD_DRIVE_SYNC_ENABLED = booleanPreferencesKey("obd_drive_sync_enabled")
        val OBD_DRIVE_LAST_SYNC_AT = longPreferencesKey("obd_drive_last_sync_at")
        const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L
    }

    val accessToken: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[ACCESS_TOKEN] }

    val refreshToken: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[REFRESH_TOKEN] }

    val tokenExpiresAt: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[TOKEN_EXPIRES_AT] ?: 0L }

    val sessionExpiresAt: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[SESSION_EXPIRES_AT] ?: it[TOKEN_EXPIRES_AT] ?: 0L }

    val stayLoggedIn30Days: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[STAY_LOGGED_IN_30_DAYS] ?: false }

    val username: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[USERNAME] }

    val servicePin: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[SERVICE_PIN] }

    val isLoggedIn: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val accessToken = prefs[ACCESS_TOKEN]
            val refreshToken = prefs[REFRESH_TOKEN]
            val expiresAt = prefs[SESSION_EXPIRES_AT] ?: prefs[TOKEN_EXPIRES_AT] ?: 0L
            // Stay signed in for the session window whenever we still have tokens to refresh with.
            // Access-token TTL alone must not bounce the UI to login before a refresh attempt.
            (!accessToken.isNullOrEmpty() || !refreshToken.isNullOrEmpty()) &&
                System.currentTimeMillis() < expiresAt
        }

    val passwordRequired: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PASSWORD_REQUIRED] ?: false }

    val otpPending: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OTP_PENDING] ?: false }

    val otpPendingUsername: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OTP_PENDING_USERNAME] }

    val demoMode: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[DEMO_MODE] ?: false }

    val hasRecoverableSession: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val accessToken = prefs[ACCESS_TOKEN]
            val refreshToken = prefs[REFRESH_TOKEN]
            val passwordRequired = prefs[PASSWORD_REQUIRED] ?: false
            val expiresAt = prefs[SESSION_EXPIRES_AT] ?: prefs[TOKEN_EXPIRES_AT] ?: 0L
            (!accessToken.isNullOrBlank() || !refreshToken.isNullOrBlank()) &&
                !passwordRequired &&
                System.currentTimeMillis() < expiresAt
        }

    val selectedVin: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[SELECTED_VIN] }

    val region: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[REGION] ?: "US_HYUNDAI" }

    val temperatureUnit: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[TEMPERATURE_UNIT] ?: "F" }

    val distanceUnit: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[DISTANCE_UNIT] ?: "MI" }

    val timeZoneMode: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[TIME_ZONE_MODE] ?: "DEVICE" }

    val timeFormat: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[TIME_FORMAT] ?: "12_HOUR" }

    val biometricEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[BIOMETRIC_ENABLED] ?: false }

    val biometricUnlockMode: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[BIOMETRIC_UNLOCK_MODE] ?: "APP_OPEN" }

    val lastBiometricUnlockAt: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[LAST_BIOMETRIC_UNLOCK_AT] ?: 0L }

    val walkAwayLockEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[WALK_AWAY_LOCK_ENABLED] ?: false }

    val walkAwayLockDelaySeconds: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { (it[WALK_AWAY_LOCK_DELAY_SECONDS] ?: 60).coerceIn(0, 600) }

    val walkAwayBluetoothAddress: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[WALK_AWAY_BLUETOOTH_ADDRESS] }

    val walkAwayBluetoothName: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[WALK_AWAY_BLUETOOTH_NAME] }

    val walkAwayLockPending: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[WALK_AWAY_LOCK_PENDING] ?: false }

    val obdTransportType: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OBD_TRANSPORT_TYPE] ?: ObdTransportType.BLUETOOTH.name }

    val obdBluetoothAddress: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OBD_BLUETOOTH_ADDRESS] }

    val obdBluetoothName: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OBD_BLUETOOTH_NAME] }

    val obdWifiHost: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OBD_WIFI_HOST] ?: "192.168.0.10" }

    val obdWifiPort: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OBD_WIFI_PORT] ?: 35000 }

    val obdProfileId: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OBD_PROFILE_ID] ?: "KONA_NIRO_64" }

    val obdSampleIntervalSeconds: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { (it[OBD_SAMPLE_INTERVAL_SECONDS] ?: 10).coerceIn(5, 60) }

    val obdAutoConnect: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OBD_AUTO_CONNECT] ?: false }

    val obdAutoStartLogging: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OBD_AUTO_START_LOGGING] ?: false }

    val obdLogRetentionDays: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OBD_LOG_RETENTION_DAYS] ?: 30 }

    val obdLogMaxStorageMb: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OBD_LOG_MAX_STORAGE_MB] ?: 100 }

    val obdDriveSyncEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OBD_DRIVE_SYNC_ENABLED] ?: false }

    val obdDriveLastSyncAt: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[OBD_DRIVE_LAST_SYNC_AT] ?: 0L }

    val defaultClimateTemp: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[DEFAULT_CLIMATE_TEMP] ?: "72" }

    val defaultClimateDurationMinutes: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map {
            coerceClimateDurationMinutes(
                it[DEFAULT_CLIMATE_DURATION_MINUTES]
                    ?: com.bluedeck.data.models.DEFAULT_CLIMATE_DURATION_MINUTES
            )
        }

    val valetModeEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[VALET_MODE_ENABLED] ?: false }

    val activeDriverProfileId: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[ACTIVE_DRIVER_PROFILE_ID] ?: "primary" }

    val themeMode: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[THEME_MODE] ?: "system" }

    val useDynamicColor: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[USE_DYNAMIC_COLOR] ?: false }

    val driverProfilePhotoUris: Flow<Map<String, String?>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            mapOf(
                "primary" to prefs[PROFILE_PRIMARY_PHOTO_URI],
                "guest" to prefs[PROFILE_GUEST_PHOTO_URI],
                "valet" to prefs[PROFILE_VALET_PHOTO_URI]
            )
        }

    val customDashboardImageUri: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[CUSTOM_DASHBOARD_IMAGE_URI] }

    val lastStatusRefresh: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[LAST_STATUS_REFRESH] ?: 0L }

    val commandHistory: Flow<List<CommandHistoryEntry>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> parseCommandHistory(prefs[COMMAND_HISTORY_JSON].orEmpty()) }

    val showRecentCommands: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[SHOW_RECENT_COMMANDS] ?: true }

    val widgetVehicleSnapshot: Flow<WidgetVehicleSnapshot> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            WidgetVehicleSnapshot(
                vehicleName = prefs[WIDGET_VEHICLE_NAME] ?: "BlueDeck",
                vehicleVin = prefs[WIDGET_VEHICLE_VIN].orEmpty(),
                vehicleId = prefs[WIDGET_VEHICLE_ID].orEmpty(),
                registrationId = prefs[WIDGET_REGISTRATION_ID].orEmpty(),
                generation = prefs[WIDGET_GENERATION] ?: "3",
                brandIndicator = prefs[WIDGET_BRAND_INDICATOR] ?: "H",
                modelCode = prefs[WIDGET_MODEL_CODE].orEmpty(),
                doorsLocked = prefs[WIDGET_DOORS_LOCKED],
                batteryPercent = prefs[WIDGET_BATTERY_PERCENT],
                rangeMiles = prefs[WIDGET_RANGE_MILES],
                chargingLabel = prefs[WIDGET_CHARGING_LABEL] ?: "Open app to refresh",
                message = prefs[WIDGET_MESSAGE] ?: "Tap to open app",
                detailOne = prefs[WIDGET_DETAIL_ONE] ?: "Doors —",
                detailTwo = prefs[WIDGET_DETAIL_TWO] ?: "Climate —",
                detailThree = prefs[WIDGET_DETAIL_THREE] ?: "Tires —",
                updatedAtMillis = prefs[WIDGET_UPDATED_AT] ?: 0L
            )
        }


    suspend fun getOrCreateCanadaDeviceId(): String {
        val existing = dataStore.data.first()[CANADA_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing

        val generated = Base64.encodeToString(
            UUID.randomUUID().toString().replace("-", "").toByteArray(),
            Base64.NO_WRAP
        )
        dataStore.edit { prefs -> prefs[CANADA_DEVICE_ID] = generated }
        return generated
    }

    suspend fun getStoredEuDeviceId(): String? = dataStore.data.first()[EU_DEVICE_ID]

    suspend fun getStoredEuDeviceId(region: String): String? {
        val prefs = dataStore.data.first()
        val id = prefs[EU_DEVICE_ID]
        val storedRegion = prefs[EU_DEVICE_REGION]
        return if (!id.isNullOrBlank() && storedRegion == region) id else null
    }

    suspend fun getOrCreateEuDeviceId(): String {
        val existing = dataStore.data.first()[EU_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing

        val generated = UUID.randomUUID().toString()
        dataStore.edit { prefs -> prefs[EU_DEVICE_ID] = generated }
        return generated
    }

    suspend fun setEuDeviceId(deviceId: String) {
        if (deviceId.isBlank()) return
        dataStore.edit { prefs -> prefs[EU_DEVICE_ID] = deviceId }
    }

    suspend fun setEuDeviceId(deviceId: String, region: String) {
        if (deviceId.isBlank()) return
        dataStore.edit { prefs ->
            prefs[EU_DEVICE_ID] = deviceId
            prefs[EU_DEVICE_REGION] = region
        }
    }

    suspend fun getOrCreateAuDeviceId(): String {
        val existing = dataStore.data.first()[AU_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing

        val generated = UUID.randomUUID().toString()
        dataStore.edit { prefs -> prefs[AU_DEVICE_ID] = generated }
        return generated
    }

    suspend fun setAuDeviceId(deviceId: String) {
        if (deviceId.isBlank()) return
        dataStore.edit { prefs -> prefs[AU_DEVICE_ID] = deviceId }
    }

    suspend fun getOrCreateKiaUsDeviceId(): String {
        val existing = dataStore.data.first()[KIA_US_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing

        val generated = UUID.randomUUID().toString().uppercase()
        dataStore.edit { prefs -> prefs[KIA_US_DEVICE_ID] = generated }
        return generated
    }

    suspend fun setKiaUsDeviceId(deviceId: String) {
        if (deviceId.isBlank()) return
        dataStore.edit { prefs -> prefs[KIA_US_DEVICE_ID] = deviceId }
    }

    suspend fun saveSession(accessToken: String, refreshToken: String, username: String, expiresIn: Int, servicePin: String? = null) {
        dataStore.edit { prefs ->
            val now = System.currentTimeMillis()
            val tokenExpiresAt = now + (expiresIn.coerceAtLeast(60) * 1000L)
            val stayLoggedIn = prefs[STAY_LOGGED_IN_30_DAYS] ?: false
            prefs[ACCESS_TOKEN] = accessToken
            prefs[REFRESH_TOKEN] = refreshToken
            prefs[USERNAME] = username
            if (!servicePin.isNullOrBlank()) prefs[SERVICE_PIN] = servicePin
            prefs[TOKEN_EXPIRES_AT] = tokenExpiresAt
            // Access tokens are short-lived (~30m). Keep the UI session alive for the trust
            // window whenever a refresh token exists so we can renew without bouncing to login.
            prefs[SESSION_EXPIRES_AT] = when {
                stayLoggedIn || refreshToken.isNotBlank() -> now + THIRTY_DAYS_MS
                else -> tokenExpiresAt
            }
            prefs[PASSWORD_REQUIRED] = false
            prefs[OTP_PENDING] = false
            prefs.remove(OTP_PENDING_USERNAME)
        }
    }

    suspend fun setOtpPending(username: String) {
        dataStore.edit { prefs ->
            prefs[OTP_PENDING] = true
            prefs[OTP_PENDING_USERNAME] = username
            prefs[PASSWORD_REQUIRED] = true
            prefs.remove(ACCESS_TOKEN)
            prefs.remove(REFRESH_TOKEN)
            prefs.remove(TOKEN_EXPIRES_AT)
            prefs.remove(SESSION_EXPIRES_AT)
        }
    }

    suspend fun clearOtpPending() {
        dataStore.edit { prefs ->
            prefs[OTP_PENDING] = false
            prefs.remove(OTP_PENDING_USERNAME)
        }
    }

    suspend fun clearSession(requirePassword: Boolean = true) {
        dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN)
            prefs.remove(REFRESH_TOKEN)
            prefs.remove(USERNAME)
            prefs.remove(SERVICE_PIN)
            prefs.remove(TOKEN_EXPIRES_AT)
            prefs.remove(SESSION_EXPIRES_AT)
            prefs[DEMO_MODE] = false
            prefs[PASSWORD_REQUIRED] = requirePassword
            if (!requirePassword) {
                prefs[OTP_PENDING] = false
                prefs.remove(OTP_PENDING_USERNAME)
            }
        }
    }

    suspend fun setDemoMode(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[DEMO_MODE] = enabled }
    }

    suspend fun isDemoMode(): Boolean = dataStore.data.first()[DEMO_MODE] ?: false

    suspend fun setSelectedVin(vin: String) {
        dataStore.edit { it[SELECTED_VIN] = vin }
    }

    suspend fun setServicePin(pin: String) {
        dataStore.edit { prefs ->
            if (pin.isBlank()) prefs.remove(SERVICE_PIN) else prefs[SERVICE_PIN] = pin
        }
        clearOneShotServicePin()
    }

    /** In-memory PIN for a single command when the user declines "Save PIN". */
    @Volatile
    private var oneShotServicePin: String? = null

    fun setOneShotServicePin(pin: String?) {
        oneShotServicePin = pin?.trim()?.takeIf { it.length == 4 }
    }

    fun clearOneShotServicePin() {
        oneShotServicePin = null
    }

    suspend fun effectiveServicePin(): String {
        oneShotServicePin?.let { return it }
        return servicePin.first().orEmpty().trim()
    }

    suspend fun setRegion(region: String) {
        dataStore.edit { it[REGION] = region }
    }

    suspend fun setTemperatureUnit(unit: String) {
        dataStore.edit { it[TEMPERATURE_UNIT] = unit }
    }

    suspend fun setDistanceUnit(unit: String) {
        dataStore.edit { it[DISTANCE_UNIT] = unit }
    }

    suspend fun setTimeZoneMode(mode: String) {
        dataStore.edit { it[TIME_ZONE_MODE] = mode }
    }

    suspend fun setTimeFormat(format: String) {
        dataStore.edit { it[TIME_FORMAT] = format }
    }

    suspend fun setStayLoggedIn30Days(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[STAY_LOGGED_IN_30_DAYS] = enabled
            val token = prefs[ACCESS_TOKEN]
            if (!token.isNullOrBlank()) {
                val tokenExpiresAt = prefs[TOKEN_EXPIRES_AT] ?: 0L
                prefs[SESSION_EXPIRES_AT] = if (enabled) {
                    System.currentTimeMillis() + THIRTY_DAYS_MS
                } else {
                    tokenExpiresAt
                }
            }
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[BIOMETRIC_ENABLED] = enabled
            if (!enabled) prefs.remove(LAST_BIOMETRIC_UNLOCK_AT)
        }
    }

    suspend fun setBiometricUnlockMode(mode: String) {
        val normalized = when (mode) {
            "APP_OPEN", "DAILY", "COMMANDS_ONLY", "NEVER" -> mode
            else -> "APP_OPEN"
        }
        dataStore.edit { prefs -> prefs[BIOMETRIC_UNLOCK_MODE] = normalized }
    }

    suspend fun setLastBiometricUnlockAt(timestamp: Long) {
        dataStore.edit { prefs ->
            if (timestamp > 0L) prefs[LAST_BIOMETRIC_UNLOCK_AT] = timestamp else prefs.remove(LAST_BIOMETRIC_UNLOCK_AT)
        }
    }

    suspend fun setWalkAwayLockEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[WALK_AWAY_LOCK_ENABLED] = enabled
            if (!enabled) prefs[WALK_AWAY_LOCK_PENDING] = false
        }
    }

    suspend fun setWalkAwayLockDelaySeconds(seconds: Int) {
        dataStore.edit { prefs -> prefs[WALK_AWAY_LOCK_DELAY_SECONDS] = seconds.coerceIn(0, 600) }
    }

    suspend fun setWalkAwayBluetoothDevice(name: String, address: String) {
        dataStore.edit { prefs ->
            prefs[WALK_AWAY_BLUETOOTH_NAME] = name.ifBlank { "Vehicle Bluetooth" }
            prefs[WALK_AWAY_BLUETOOTH_ADDRESS] = address
        }
    }

    suspend fun clearWalkAwayBluetoothDevice() {
        dataStore.edit { prefs ->
            prefs.remove(WALK_AWAY_BLUETOOTH_NAME)
            prefs.remove(WALK_AWAY_BLUETOOTH_ADDRESS)
            prefs[WALK_AWAY_LOCK_ENABLED] = false
            prefs[WALK_AWAY_LOCK_PENDING] = false
        }
    }

    /** Returns true if this call transitioned pending from false → true. */
    suspend fun tryMarkWalkAwayLockPending(): Boolean {
        var marked = false
        dataStore.edit { prefs ->
            if (prefs[WALK_AWAY_LOCK_PENDING] != true) {
                prefs[WALK_AWAY_LOCK_PENDING] = true
                marked = true
            }
        }
        return marked
    }

    suspend fun setWalkAwayLockPending(pending: Boolean) {
        dataStore.edit { prefs -> prefs[WALK_AWAY_LOCK_PENDING] = pending }
    }

    suspend fun setObdTransportType(type: ObdTransportType) {
        dataStore.edit { it[OBD_TRANSPORT_TYPE] = type.name }
    }

    suspend fun setObdBluetoothDevice(name: String, address: String) {
        dataStore.edit { prefs ->
            prefs[OBD_BLUETOOTH_NAME] = name.ifBlank { "OBD Adapter" }
            prefs[OBD_BLUETOOTH_ADDRESS] = address
        }
    }

    suspend fun clearObdBluetoothDevice() {
        dataStore.edit { prefs ->
            prefs.remove(OBD_BLUETOOTH_NAME)
            prefs.remove(OBD_BLUETOOTH_ADDRESS)
        }
    }

    suspend fun setObdWifiEndpoint(host: String, port: Int) {
        dataStore.edit { prefs ->
            prefs[OBD_WIFI_HOST] = host.ifBlank { "192.168.0.10" }
            prefs[OBD_WIFI_PORT] = port.coerceIn(1, 65535)
        }
    }

    suspend fun setObdProfileId(profileId: String) {
        dataStore.edit { it[OBD_PROFILE_ID] = profileId }
    }

    suspend fun setObdSampleIntervalSeconds(seconds: Int) {
        dataStore.edit { it[OBD_SAMPLE_INTERVAL_SECONDS] = seconds.coerceIn(5, 60) }
    }

    suspend fun setObdAutoConnect(enabled: Boolean) {
        dataStore.edit { it[OBD_AUTO_CONNECT] = enabled }
    }

    suspend fun setObdAutoStartLogging(enabled: Boolean) {
        dataStore.edit { it[OBD_AUTO_START_LOGGING] = enabled }
    }

    suspend fun setObdLogRetentionDays(days: Int) {
        dataStore.edit { it[OBD_LOG_RETENTION_DAYS] = days.coerceAtLeast(0) }
    }

    suspend fun setObdLogMaxStorageMb(mb: Int) {
        dataStore.edit { it[OBD_LOG_MAX_STORAGE_MB] = mb.coerceAtLeast(0) }
    }

    suspend fun setObdDriveSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[OBD_DRIVE_SYNC_ENABLED] = enabled }
    }

    suspend fun setObdDriveLastSyncAt(timestamp: Long) {
        dataStore.edit { it[OBD_DRIVE_LAST_SYNC_AT] = timestamp }
    }

    suspend fun setDefaultClimateTemp(temp: String) {
        dataStore.edit { it[DEFAULT_CLIMATE_TEMP] = temp }
    }

    suspend fun setDefaultClimateDurationMinutes(minutes: Int) {
        dataStore.edit {
            it[DEFAULT_CLIMATE_DURATION_MINUTES] = coerceClimateDurationMinutes(minutes)
        }
    }

    suspend fun setValetModeEnabled(enabled: Boolean) {
        dataStore.edit { it[VALET_MODE_ENABLED] = enabled }
    }

    suspend fun setActiveDriverProfileId(profileId: String) {
        dataStore.edit { it[ACTIVE_DRIVER_PROFILE_ID] = profileId }
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        dataStore.edit { it[USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setShowRecentCommands(enabled: Boolean) {
        dataStore.edit { it[SHOW_RECENT_COMMANDS] = enabled }
    }

    suspend fun setDriverProfilePhotoUri(profileId: String, photoUri: String?) {
        val key = when (profileId) {
            "primary" -> PROFILE_PRIMARY_PHOTO_URI
            "guest" -> PROFILE_GUEST_PHOTO_URI
            "valet" -> PROFILE_VALET_PHOTO_URI
            else -> return
        }
        dataStore.edit { prefs ->
            if (photoUri.isNullOrBlank()) prefs.remove(key) else prefs[key] = photoUri
        }
    }

    suspend fun setCustomDashboardImageUri(photoUri: String?) {
        dataStore.edit { prefs ->
            if (photoUri.isNullOrBlank()) prefs.remove(CUSTOM_DASHBOARD_IMAGE_URI) else prefs[CUSTOM_DASHBOARD_IMAGE_URI] = photoUri
        }
    }

    suspend fun setLastStatusRefresh(timestamp: Long) {
        dataStore.edit { it[LAST_STATUS_REFRESH] = timestamp }
    }

    suspend fun addCommandHistoryEntry(entry: CommandHistoryEntry) {
        dataStore.edit { prefs ->
            val current = parseCommandHistory(prefs[COMMAND_HISTORY_JSON].orEmpty())
            val updated = (listOf(entry) + current).take(30)
            prefs[COMMAND_HISTORY_JSON] = encodeCommandHistory(updated)
        }
    }

    suspend fun clearCommandHistory() {
        dataStore.edit { it.remove(COMMAND_HISTORY_JSON) }
    }

    suspend fun cacheWidgetSnapshot(snapshot: WidgetVehicleSnapshot) {
        dataStore.edit { prefs ->
            prefs[WIDGET_VEHICLE_NAME] = snapshot.vehicleName.ifBlank { "BlueDeck" }
            if (snapshot.vehicleVin.isNotBlank()) prefs[WIDGET_VEHICLE_VIN] = snapshot.vehicleVin
            if (snapshot.vehicleId.isNotBlank()) prefs[WIDGET_VEHICLE_ID] = snapshot.vehicleId
            if (snapshot.registrationId.isNotBlank()) prefs[WIDGET_REGISTRATION_ID] = snapshot.registrationId
            if (snapshot.generation.isNotBlank()) prefs[WIDGET_GENERATION] = snapshot.generation
            if (snapshot.brandIndicator.isNotBlank()) prefs[WIDGET_BRAND_INDICATOR] = snapshot.brandIndicator
            if (snapshot.modelCode.isNotBlank()) prefs[WIDGET_MODEL_CODE] = snapshot.modelCode
            snapshot.doorsLocked?.let { prefs[WIDGET_DOORS_LOCKED] = it } ?: prefs.remove(WIDGET_DOORS_LOCKED)
            snapshot.batteryPercent?.let { prefs[WIDGET_BATTERY_PERCENT] = it.coerceIn(0, 100) } ?: prefs.remove(WIDGET_BATTERY_PERCENT)
            snapshot.rangeMiles?.let { prefs[WIDGET_RANGE_MILES] = it.coerceAtLeast(0) } ?: prefs.remove(WIDGET_RANGE_MILES)
            prefs[WIDGET_CHARGING_LABEL] = snapshot.chargingLabel.ifBlank { "Status unavailable" }
            prefs[WIDGET_MESSAGE] = snapshot.message.ifBlank { "Tap to open app" }
            prefs[WIDGET_DETAIL_ONE] = snapshot.detailOne.ifBlank { "Doors —" }
            prefs[WIDGET_DETAIL_TWO] = snapshot.detailTwo.ifBlank { "Climate —" }
            prefs[WIDGET_DETAIL_THREE] = snapshot.detailThree.ifBlank { "Tires —" }
            prefs[WIDGET_UPDATED_AT] = snapshot.updatedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        }
    }

    suspend fun setWidgetMessage(message: String) {
        dataStore.edit { prefs ->
            prefs[WIDGET_MESSAGE] = message
            prefs[WIDGET_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    private fun parseCommandHistory(raw: String): List<CommandHistoryEntry> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                CommandHistoryEntry(
                    timestampMillis = item.optLong("timestampMillis"),
                    title = item.optString("title"),
                    detail = item.optString("detail"),
                    successful = item.optBoolean("successful", true),
                    vehicleName = item.optString("vehicleName")
                )
            }.filter { it.title.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun encodeCommandHistory(entries: List<CommandHistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("timestampMillis", entry.timestampMillis)
                    .put("title", entry.title)
                    .put("detail", entry.detail)
                    .put("successful", entry.successful)
                    .put("vehicleName", entry.vehicleName)
            )
        }
        return array.toString()
    }
}
