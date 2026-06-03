package com.blueandroid.auto

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import com.blueandroid.data.models.Vehicle
import com.blueandroid.data.models.VehicleStatusData
import com.blueandroid.data.repository.PreferencesManager
import com.blueandroid.data.repository.Result
import com.blueandroid.data.repository.VehicleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * MediaBrowser fallback for Android Auto discovery.
 *
 * Android Auto surfaces media apps more reliably than sideloaded template/IoT apps on
 * some hosts. This exposes BlueAndroid as a read-only media-style status browser. It does
 * not play audio and does not expose vehicle commands.
 */
@AndroidEntryPoint
class BlueAndroidMediaBrowserService : MediaBrowserServiceCompat() {

    @Inject lateinit var vehicleRepository: VehicleRepository
    @Inject lateinit var preferencesManager: PreferencesManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "BlueAndroidMediaBrowser").apply {
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "BlueAndroid")
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "BlueAndroid Status")
                    .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "Read-only vehicle status")
                    .build()
            )
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, 0L, 0f)
                    .setActions(0L)
                    .build()
            )
            isActive = true
        }
        sessionToken = mediaSession.sessionToken
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.detach()
        serviceScope.launch {
            val items = when (parentId) {
                ROOT_ID -> rootItems()
                OVERVIEW_ID -> statusItems(StatusGroup.OVERVIEW)
                CHARGING_ID -> statusItems(StatusGroup.CHARGING)
                TIRES_ID -> statusItems(StatusGroup.TIRES)
                DIAGNOSTICS_ID -> statusItems(StatusGroup.DIAGNOSTICS)
                else -> mutableListOf()
            }
            result.sendResult(items)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (::mediaSession.isInitialized) mediaSession.release()
        super.onDestroy()
    }

    private fun rootItems(): MutableList<MediaBrowserCompat.MediaItem> = mutableListOf(
        browseItem(OVERVIEW_ID, "Overview", "Battery, range, locks, climate"),
        browseItem(CHARGING_ID, "Charging", "Plug, speed, AC/DC targets"),
        browseItem(TIRES_ID, "Tires", "Pressure and warning lamps"),
        browseItem(DIAGNOSTICS_ID, "Diagnostics", "12V, odometer, preconditioning")
    )

    private suspend fun statusItems(group: StatusGroup): MutableList<MediaBrowserCompat.MediaItem> {
        val vehicle = when (val vehicles = vehicleRepository.getVehicles()) {
            is Result.Success -> vehicles.data.firstOrNull()
            is Result.Error -> return mutableListOf(playableItem("error", "Vehicle list failed", vehicles.message))
            else -> null
        } ?: return mutableListOf(playableItem("no_vehicle", "No vehicle", "Open BlueAndroid on your phone first"))

        val status = when (val res = vehicleRepository.getVehicleStatus(
            vin = vehicle.vin,
            forceRefresh = false,
            registrationId = vehicle.regId,
            generation = vehicle.generation,
            brandIndicator = vehicle.brandIndicator
        )) {
            is Result.Success -> res.data
            is Result.Error -> return mutableListOf(playableItem("status_error", "Status unavailable", res.message))
            else -> null
        } ?: return mutableListOf(playableItem("status_empty", "Status unavailable", "No cached vehicle status"))

        val distanceUnit = preferencesManager.distanceUnit.first()
        return when (group) {
            StatusGroup.OVERVIEW -> overviewItems(vehicle, status, distanceUnit)
            StatusGroup.CHARGING -> chargingItems(status)
            StatusGroup.TIRES -> tireItems(status)
            StatusGroup.DIAGNOSTICS -> diagnosticItems(vehicle, status, distanceUnit)
        }
    }

    private fun overviewItems(vehicle: Vehicle, status: VehicleStatusData, distanceUnit: String): MutableList<MediaBrowserCompat.MediaItem> {
        val ev = status.evStatus
        return mutableListOf(
            playableItem("vehicle", vehicle.displayName.ifBlank { "Vehicle" }, "VIN • ${vehicle.vin.takeLast(6)}"),
            playableItem("battery", "HV Battery", "${ev?.batteryStatus ?: 0}% • ${formatDistance(ev?.rangeMiles ?: status.dte?.value ?: 0.0, distanceUnit)}"),
            playableItem("doors", "Doors", if (status.doorsLocked) "Locked" else "Unlocked"),
            playableItem("climate", "Climate", if (status.airCtrlOn) "On" else "Off"),
            playableItem("plug", "Plug", ev?.plugStatusLabel ?: "Unknown")
        )
    }

    private fun chargingItems(status: VehicleStatusData): MutableList<MediaBrowserCompat.MediaItem> {
        val ev = status.evStatus
        val acTarget = ev?.acChargeTarget?.let { "$it%" } ?: "—"
        val dcTarget = ev?.dcChargeTarget?.let { "$it%" } ?: "—"
        return mutableListOf(
            playableItem("charge_state", "Charging", ev?.chargingMethodLabel ?: "Unknown"),
            playableItem("charge_speed", "Speed", ev?.chargingSpeedLabel ?: "Unavailable"),
            playableItem("charge_targets", "Targets", "AC $acTarget • DC $dcTarget"),
            playableItem("charge_port", "Charge Port", "Code ${ev?.chargePortDoorOpen ?: -1}")
        )
    }

    private fun tireItems(status: VehicleStatusData): MutableList<MediaBrowserCompat.MediaItem> {
        val tire = status.tirePressureStatus
        val lamps = status.tirePressureLamp
        return mutableListOf(
            playableItem("tire_fl", "Front Left", "${tire?.frontLeftPsi ?: 0} PSI"),
            playableItem("tire_fr", "Front Right", "${tire?.frontRightPsi ?: 0} PSI"),
            playableItem("tire_rl", "Rear Left", "${tire?.rearLeftPsi ?: 0} PSI"),
            playableItem("tire_rr", "Rear Right", "${tire?.rearRightPsi ?: 0} PSI"),
            playableItem("tire_warning", "Tire Warning", if ((lamps?.allLow ?: 0) == 0) "None" else "Warning code ${lamps?.allLow}")
        )
    }

    private fun diagnosticItems(vehicle: Vehicle, status: VehicleStatusData, distanceUnit: String): MutableList<MediaBrowserCompat.MediaItem> {
        val ev = status.evStatus
        val latestTireTime = listOfNotNull(
            status.tirePressureStatus?.frontLeftTime,
            status.tirePressureStatus?.frontRightTime,
            status.tirePressureStatus?.rearLeftTime,
            status.tirePressureStatus?.rearRightTime
        ).filter { it.isNotBlank() }.maxOrNull()?.replace('T', ' ')?.removeSuffix("Z")

        return mutableListOf(
            playableItem("battery_12v", "12V Battery", "${status.battery?.batteryLevel ?: 0}%"),
            playableItem("odometer", "Odometer", formatDistance((status.totalMileage.takeIf { it > 0 } ?: vehicle.odometer).toDouble(), distanceUnit)),
            playableItem("precondition", "Battery Preconditioning", if (ev?.batteryPrecondition == true) "Active" else "Inactive"),
            playableItem("v2l", "V2L / Discharge", if (ev?.batteryDisCharge == true) "Active" else "Inactive"),
            playableItem("last_update", "Last Update", latestTireTime ?: "Status loaded")
        )
    }

    private fun browseItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun playableItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    private fun formatDistance(miles: Double, distanceUnit: String): String {
        val value = if (distanceUnit.equals("KM", ignoreCase = true)) miles * 1.609344 else miles
        val unit = if (distanceUnit.equals("KM", ignoreCase = true)) "km" else "mi"
        return String.format(Locale.US, "%.0f %s", value, unit)
    }

    private enum class StatusGroup { OVERVIEW, CHARGING, TIRES, DIAGNOSTICS }

    companion object {
        private const val ROOT_ID = "blueandroid_root"
        private const val OVERVIEW_ID = "blueandroid_overview"
        private const val CHARGING_ID = "blueandroid_charging"
        private const val TIRES_ID = "blueandroid_tires"
        private const val DIAGNOSTICS_ID = "blueandroid_diagnostics"
    }
}
