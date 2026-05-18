package com.bluebridge.android.data.models

import com.google.gson.annotations.SerializedName

// ─── Auth ─────────────────────────────────────────────────────────────────────

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
    @SerializedName("deviceKey") val deviceKey: String = "",
    @SerializedName("cvt") val cvt: String = "m"
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: String = "1799",
    @SerializedName("token_type") val tokenType: String = "",
    @SerializedName("username") val username: String = ""
)

data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val username: String,
    val expiresAt: Long
) {
    val isExpired get() = System.currentTimeMillis() > expiresAt
}


// ─── Command responses ───────────────────────────────────────────────────────

data class CommandResponse(
    @SerializedName("isBlueLinkServicePinValid") val isBlueLinkServicePinValid: String? = null,
    @SerializedName("remainingAttemptCount") val remainingAttemptCount: String? = null,
    @SerializedName("invalidAttemptMessage") val invalidAttemptMessage: String? = null,
    @SerializedName("escalationWindow") val escalationWindow: String? = null,
    @SerializedName("errorCode") val errorCode: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("status") val status: String? = null
) {
    val isInvalidPin: Boolean
        get() = isBlueLinkServicePinValid.equals("invalid", ignoreCase = true) ||
            invalidAttemptMessage?.contains("Incorrect PIN", ignoreCase = true) == true

    val userMessage: String?
        get() = invalidAttemptMessage ?: errorMessage ?: message
}

// ─── Vehicle ──────────────────────────────────────────────────────────────────

data class Vehicle(
    @SerializedName("vin") val vin: String = "",
    @SerializedName("vehicleIdentifier") val vehicleIdentifier: String = "",
    @SerializedName("enrollmentId") val enrollmentId: String = "",
    @SerializedName("regid") val regId: String = "",
    @SerializedName("vehicleKey") val vehicleKey: String = "",
    @SerializedName("vehicleGeneration") val generation: String = "3",
    @SerializedName("nickName") val nickname: String = "",
    @SerializedName("modelCode") val modelCode: String = "",
    @SerializedName("series") val modelName: String = "",
    @SerializedName("modelYear") val modelYear: String = "",
    @SerializedName("color") val colorName: String = "",
    @SerializedName("sapColorCode") val sapColorCode: String = "",
    @SerializedName("interiorColor") val interiorColor: String = "",
    @SerializedName("enrollmentDate") val regDate: String = "",
    @SerializedName("odometerUpdateDate") val odometerUpdateDate: String = "",
    @SerializedName("brandIndicator") val brandIndicator: String = "H",
    @SerializedName("imagePath") val imagePath: String = "",
    @SerializedName("deviceStatus") val enrollmentStatus: String = "ENROLLED",
    @SerializedName("enrollmentStatus") val bluelinkEnrollmentStatus: String = "",
    @SerializedName("odometer") val odometer: Int = 0,
    @SerializedName("accessoryCode") val accessoryCode: String = "",
    @SerializedName("mapProvider") val mapProvider: String = "",
    @SerializedName("hmaModel") val hmaModel: String = "",
    @SerializedName("transmissiontype") val transmissionType: String = "",
    @SerializedName("starttype") val startType: String = "",
    @SerializedName("targetSOCLevel") val targetSOCLevel: String = "",
    @SerializedName("userprofilestatus") val userProfileStatus: String = "",
    @SerializedName("additionalVehicleDetails") val additionalDetails: AdditionalVehicleDetails? = null,
    @SerializedName("seatConfigurations") val seatConfigurations: SeatConfigurations? = null,
    val packageDetails: List<PackageDetail> = emptyList()
) {
    val displayName get() = nickname.ifBlank { "$modelYear $modelName" }
    val isHyundai get() = brandIndicator.equals("H", ignoreCase = true)
    val isKia get() = brandIndicator.equals("K", ignoreCase = true)
    val serviceBrandName: String
        get() = when (brandIndicator.trim().uppercase()) {
            "K" -> "Kia"
            "G" -> "Genesis"
            else -> "Hyundai"
        }

    val powertrainLookupText: String
        get() = listOf(modelCode, modelName, displayName, hmaModel)
            .filter { it.isNotBlank() }
            .joinToString(" ")

    val isPlugInHybrid: Boolean
        get() = powertrainLookupText.contains("PHEV", ignoreCase = true) ||
            powertrainLookupText.contains("PLUG-IN", ignoreCase = true) ||
            powertrainLookupText.contains("PLUG IN", ignoreCase = true) ||
            powertrainLookupText.contains("PLUGIN", ignoreCase = true)

    val isLikelyPureBatteryElectric: Boolean
        get() {
            if (isPlugInHybrid) return false
            val text = powertrainLookupText
            return text.contains("IONIQ", ignoreCase = true) ||
                text.contains("KONA ELECTRIC", ignoreCase = true) ||
                text.contains("KONA EV", ignoreCase = true) ||
                text.contains("EV6", ignoreCase = true) ||
                text.contains("EV 6", ignoreCase = true) ||
                text.contains("EV9", ignoreCase = true) ||
                text.contains("EV 9", ignoreCase = true) ||
                text.contains("NEXO", ignoreCase = true)
        }

    val isEV get() = isLikelyPureBatteryElectric
}

data class PackageDetail(
    @SerializedName("assetNumber") val assetNumber: String = "",
    @SerializedName("subscriptionType") val subscriptionType: String = "",
    @SerializedName("displayCategory") val displayCategory: String = "",
    @SerializedName("packageId") val packageId: String = "",
    @SerializedName("packageType") val packageType: String = "",
    @SerializedName("term") val term: String = "",
    @SerializedName("startDate") val startDate: String = "",
    @SerializedName("renewalDate") val renewalDate: String = ""
)

data class AdditionalVehicleDetails(
    @SerializedName("vehicleModemType") val vehicleModemType: String = "",
    @SerializedName("dkCapable") val digitalKeyCapable: String = "",
    @SerializedName("dkEnrolled") val digitalKeyEnrolled: String = "",
    @SerializedName("dkType") val digitalKeyType: String = "",
    @SerializedName("wifiHotspotCapable") val wifiHotspotCapable: String = "",
    @SerializedName("v2LOption") val v2lOption: String = "",
    @SerializedName("v2GOption") val v2gOption: String = "",
    @SerializedName("v2XOption") val v2xOption: String = "",
    @SerializedName("batteryPreconditioningOpton") val batteryPreconditioningOption: String = "",
    @SerializedName("chargePortDoorOption") val chargePortDoorOption: String = "",
    @SerializedName("frunkOption") val frunkOption: String = "",
    @SerializedName("nacsEligibleInd") val nacsEligible: String = "",
    @SerializedName("remoteLockConsentForRemoteStart") val remoteLockConsent: String = "",
    @SerializedName("remoteLockConsentForRemoteStartCapable") val remoteLockConsentCapable: String = "",
    @SerializedName("enableValetActivate") val valetActivateCapable: String = "",
    @SerializedName("mapOtaPackage") val mapOtaPackage: String = "",
    @SerializedName("msCapableOption") val multimediaStreamingCapable: String = "",
    @SerializedName("enableEVTrip") val evTripCapable: String = "",
    @SerializedName("energyConsoleCapable") val energyConsoleCapable: String = "",
    @SerializedName("targetSOCLevelMax") val targetSocLevelMax: Int = 0,
    @SerializedName("myqGarageDoorServiceCapable") val myqGarageDoorServiceCapable: Int = 0
)

data class SeatConfigurations(
    @SerializedName("seatConfigs") val seatConfigs: List<SeatConfig> = emptyList()
)

data class SeatConfig(
    @SerializedName("seatLocationID") val seatLocationId: String = "",
    @SerializedName("heatingCapable") val heatingCapable: String = "",
    @SerializedName("ventCapable") val ventCapable: String = "",
    @SerializedName("supportedLevels") val supportedLevels: String = ""
)

data class VehicleListResponse(
    @SerializedName("enrolledVehicleDetails") val vehicles: List<VehicleDetail> = emptyList()
)

data class VehicleDetail(
    @SerializedName("vehicleDetails") val vehicle: Vehicle,
    @SerializedName("packageDetails") val packageDetails: List<PackageDetail> = emptyList()
)

// ─── Vehicle Status ───────────────────────────────────────────────────────────

data class VehicleStatus(
    @SerializedName("vehicleStatus") val vehicleStatus: VehicleStatusData? = null
)

data class VehicleStatusReport(
    @SerializedName("vehicleStatus") val vehicleStatus: VehicleStatusData? = null
)

data class ReportDate(
    @SerializedName("utc") val utc: String = ""
)

data class VehicleStatusData(
    @SerializedName("doorLock") val doorLock: Boolean = false,
    @SerializedName("doorLockStatus") val doorLockStatus: String = "false",
    @SerializedName("doorOpen") val doorOpenStatus: DoorOpenStatus? = null,
    @SerializedName("trunkOpen") val trunkOpenStatus: Boolean = false,
    @SerializedName("hoodOpen") val hoodOpenStatus: Boolean = false,
    @SerializedName("engine") val engineStatus: Boolean = false,
    @SerializedName("ignitionStatus") val ignitionStatus: String = "false",
    @SerializedName("airCtrlOn") val airCtrlOn: Boolean = false,
    @SerializedName("airTemp") val airTemp: AirTemp? = null,
    @SerializedName("defrost") val defrost: Boolean = false,
    @SerializedName("sideBackWindowHeat") val sideBackWindowHeat: Int = 0,
    @SerializedName("steerWheelHeat") val steerWheelHeat: Int = 0,
    @SerializedName("dte") val dte: Dte? = null,
    @SerializedName(
        value = "fuelLevel",
        alternate = [
            "fuelLevelPercent",
            "fuelPercent",
            "fuelPct",
            "fuelPCT",
            "gasLevel",
            "remainFuelRat",
            "fuelRatio"
        ]
    ) val fuelLevelPercent: Double? = null,
    @SerializedName("lowFuelLight") val lowFuelLight: Boolean = false,
    @SerializedName("battery") val battery: BatteryStatus? = null,
    @SerializedName("evStatus") val evStatus: EVStatus? = null,
    @SerializedName("tirePressureLamp") val tirePressureLamp: TirePressure? = null,
    @SerializedName("tirePressure") val tirePressureStatus: TirePressureStatus? = null,
    @SerializedName("windowOpen") val windowOpenStatus: WindowOpenStatus? = null,
    @SerializedName("seatHeaterVentInfo") val seatHeaterVentInfo: SeatHeaterVentInfo? = null,
    @SerializedName("washerFluidStatus") val washerFluidStatus: Boolean = false,
    @SerializedName("smartKeyBatteryWarning") val smartKeyBatteryWarning: Boolean = false,
    @SerializedName("lampWireStatus") val lampWireStatus: LampWireStatus? = null,
    @SerializedName("vehicleLocation") val location: VehicleLocation? = null,
    @SerializedName("odometer") val totalMileage: Int = 0
) {
    val doorsLocked: Boolean
        get() = doorLock || doorLockStatus.equals("LOCKED", ignoreCase = true) ||
            doorLockStatus.equals("true", ignoreCase = true) || doorLockStatus == "1"

    val ignitionOn: Boolean
        get() = ignitionStatus.equals("ON", ignoreCase = true) ||
            ignitionStatus.equals("true", ignoreCase = true) || ignitionStatus == "1"

    val normalizedFuelLevelPercent: Int?
        get() {
            val raw = fuelLevelPercent ?: dte?.fuelLevelPercent ?: return null
            val percent = if (raw in 0.0..1.0) raw * 100.0 else raw
            return percent.toInt().coerceIn(0, 100)
        }

    val fuelRangeMiles: Double
        get() = evStatus?.fuelRangeMiles?.takeIf { it > 0.0 }
            ?: dte?.rangeMiles?.takeIf { it > 0.0 }
            ?: 0.0

    val totalRangeMiles: Double
        get() = evStatus?.totalRangeMiles?.takeIf { it > 0.0 }
            ?: fuelRangeMiles.takeIf { it > 0.0 }
            ?: evStatus?.rangeMiles?.takeIf { it > 0.0 }
            ?: 0.0

    val hasFuelTelemetry: Boolean
        get() = normalizedFuelLevelPercent != null || fuelRangeMiles > 0.0 || lowFuelLight

    val isPlugInHybridStatus: Boolean
        get() = evStatus != null && hasFuelTelemetry
}

fun VehicleStatusData.hasFuelTelemetryFor(vehicle: Vehicle?): Boolean {
    if (!hasFuelTelemetry) return false

    // Some Hyundai/Kia BEV payloads expose placeholder fuel/DTE fields even though the
    // vehicle has no fuel tank. Treat explicit BEV model identity as authoritative so
    // IONIQ/Kona Electric/EV6/EV9 dashboards do not show a gas gauge.
    if (evStatus != null && vehicle?.isLikelyPureBatteryElectric == true && vehicle.isPlugInHybrid.not()) {
        return false
    }

    return true
}

fun VehicleStatusData.isPlugInHybridStatusFor(vehicle: Vehicle?): Boolean =
    evStatus != null && hasFuelTelemetryFor(vehicle) && vehicle?.isLikelyPureBatteryElectric != true

fun VehicleStatusData.totalRangeMilesFor(vehicle: Vehicle?): Double {
    if (evStatus != null && vehicle?.isLikelyPureBatteryElectric == true && vehicle.isPlugInHybrid.not()) {
        return evStatus.rangeMiles.takeIf { it > 0.0 } ?: 0.0
    }
    return totalRangeMiles
}

data class DoorOpenStatus(
    @SerializedName("frontLeft") val frontLeft: Int = 0,
    @SerializedName("frontRight") val frontRight: Int = 0,
    @SerializedName("backLeft") val backLeft: Int = 0,
    @SerializedName("backRight") val backRight: Int = 0
) {
    val anyOpen get() = frontLeft == 1 || frontRight == 1 || backLeft == 1 || backRight == 1
}

data class AirTemp(
    @SerializedName("value") val value: String = "70",
    @SerializedName("unit") val unit: Int = 0
)

data class Dte(
    @SerializedName("unit") val unit: Int = 0,
    @SerializedName("value") val value: Double = 0.0,
    @SerializedName(
        value = "fuelLevel",
        alternate = [
            "fuelLevelPercent",
            "fuelPercent",
            "fuelPct",
            "fuelPCT",
            "gasLevel",
            "remainFuelRat",
            "fuelRatio"
        ]
    ) val fuelLevelPercent: Double? = null
) {
    val normalizedFuelLevelPercent: Int?
        get() {
            val raw = fuelLevelPercent ?: return null
            val percent = if (raw in 0.0..1.0) raw * 100.0 else raw
            return percent.toInt().coerceIn(0, 100)
        }

    val rangeMiles: Double
        get() = when (unit) {
            1 -> value
            2 -> value * 0.621371
            else -> value
        }
}

data class BatteryStatus(
    @SerializedName("batSoc") val batteryLevel: Int = 0,
    @SerializedName("batState") val batteryState: Int = 0,
    @SerializedName("powerAutoCutMode") val powerAutoCutMode: Int = 0,
    @SerializedName("batSignalReferenceValue") val batSignalReferenceValue: BatterySignalReference? = null
)

data class BatterySignalReference(
    @SerializedName("batWarning") val warningThreshold: Int = 0
)

data class EVStatus(
    @SerializedName("batteryCharge") val batteryCharge: Boolean = false,
    @SerializedName("batteryStatus") val batteryStatus: Int = 0,
    @SerializedName("batteryPlugin") val batteryPlugin: Int = 0,
    @SerializedName("remainChargeTime") val remainChargeTime: List<ChargeTime> = emptyList(),
    @SerializedName("remainTime2") val remainTime2: RemainTime2? = null,
    @SerializedName(
        value = "chargingPower",
        alternate = ["chargePower", "chargePwr", "chargingSpeed", "chargeSpeed", "realTimePower", "power"]
    ) val chargingPowerKw: Double? = null,
    @SerializedName("drvDistance") val drvDistance: List<DriveDistance> = emptyList(),
    @SerializedName("reservChargeInfos") val reservChargeInfos: ReservChargeInfos? = null,
    @SerializedName("chargePortDoorOpen") val chargePortDoorOpen: Int = -1,
    @SerializedName("batteryPrecondition") val batteryPrecondition: Boolean = false,
    @SerializedName("batteryDisCharge") val batteryDisCharge: Boolean = false,
    @SerializedName("batteryDisChargePlugin") val batteryDisChargePlugin: Int = 0,
    @SerializedName("dischargingLimit") val dischargingLimit: Int = 0
) {
    private val rangeValue: RangeValue?
        get() = drvDistance.firstOrNull()?.rangeByFuel?.evModeRange
            ?: drvDistance.firstOrNull()?.rangeByFuel?.totalAvailableRange

    /**
     * Hyundai's US EV range payload commonly returns the value in miles even when older
     * code paths treated it as kilometers. That caused 83 mi to be displayed as 51 mi
     * after an unnecessary km-to-mi conversion. Unit 1 is handled as miles here.
     */
    val rangeMiles: Double
        get() {
            val range = rangeValue ?: return 0.0
            return when (range.unit) {
                1 -> range.value
                2 -> range.value * 0.621371
                else -> range.value
            }
        }

    val rangeKm: Double
        get() = rangeMiles * 1.609344

    val evModeRangeMiles: Double
        get() {
            val range = drvDistance.firstOrNull()?.rangeByFuel?.evModeRange ?: return 0.0
            return when (range.unit) {
                1 -> range.value
                2 -> range.value * 0.621371
                else -> range.value
            }
        }

    private val fuelRangeValue: RangeValue?
        get() = drvDistance.firstOrNull()?.rangeByFuel?.gasModeRange

    val fuelRangeMiles: Double
        get() {
            val range = fuelRangeValue ?: return 0.0
            return when (range.unit) {
                1 -> range.value
                2 -> range.value * 0.621371
                else -> range.value
            }
        }

    val totalRangeMiles: Double
        get() = drvDistance.firstOrNull()?.rangeByFuel?.totalAvailableRange?.let { range ->
            when (range.unit) {
                1 -> range.value
                2 -> range.value * 0.621371
                else -> range.value
            }
        } ?: (evModeRangeMiles + fuelRangeMiles).takeIf { it > 0.0 } ?: 0.0

    /**
     * Hyundai's batteryPlugin code is plug type, not simply AC-vs-DC.
     * Bluelinky maps: 0 = unplugged, 1 = fast/DC, 2 = portable AC, 3 = station AC.
     * Some Hyundai Gen 3 EVs report 4 when AC-plugged but paused/scheduled charging is inactive.
     * Treating value 2 as DC caused Level 2 charging to display incorrectly.
     */
    val plugStatusLabel: String
        get() = when (batteryPlugin) {
            0 -> if (batteryCharge) "Plugged in" else "Not plugged in"
            1 -> "DC fast plugged in"
            2 -> "AC plugged in"
            3 -> "AC Level 2 plugged in"
            4 -> "AC plugged in · Charging paused"
            else -> "Unknown plug type ($batteryPlugin)"
        }

    val chargingMethodLabel: String
        get() = when {
            batteryPlugin == 1 -> "DC fast charging"
            batteryPlugin == 2 || batteryPlugin == 3 -> "AC charging"
            batteryPlugin == 4 -> "AC plugged in · Paused"
            batteryCharge -> "Charging"
            else -> "Not charging"
        }

    val chargingSpeedLabel: String
        get() {
            val speed = chargingPowerKw?.takeIf { it > 0.0 }
            return if (speed != null) {
                "${String.format(java.util.Locale.US, "%.1f", speed)} kW"
            } else {
                "Unavailable from vehicle status"
            }
        }

    /**
     * Hyundai reports charge targets in vehicleStatus.evStatus.reservChargeInfos.targetSOClist.
     * In the observed US IONIQ 9 payload, target plugType 1 is AC and plugType 0 is DC.
     * These plugType values are separate from batteryPlugin, which is used for current plug state.
     */
    private fun chargeTargetForPlugTypes(vararg plugTypes: Int): Int? {
        return reservChargeInfos
            ?.targets
            ?.firstOrNull { target -> plugTypes.contains(target.plugType) }
            ?.targetSoc
            ?.takeIf { it in 50..100 }
    }

    val acChargeTarget: Int?
        get() = chargeTargetForPlugTypes(1, 2, 3)

    val dcChargeTarget: Int?
        get() = chargeTargetForPlugTypes(0)


}

data class ChargeTime(
    @SerializedName("remainChargeType") val type: Int = 1,
    @SerializedName("remainTime") val time: RemainTime? = null
)

data class RemainTime(
    @SerializedName("timeUnit") val unit: Int = 1,
    @SerializedName("value") val value: Int = 0
)

data class RemainTime2(
    @SerializedName("atc") val currentCharge: RemainTimeValue? = null,
    @SerializedName("etc1") val fastCharge: RemainTimeValue? = null,
    @SerializedName("etc2") val portableCharge: RemainTimeValue? = null,
    @SerializedName("etc3") val stationCharge: RemainTimeValue? = null
)

data class RemainTimeValue(
    @SerializedName("unit") val unit: Int = 0,
    @SerializedName("value") val value: Int = 0
)

data class DriveDistance(
    @SerializedName("rangeByFuel") val rangeByFuel: RangeByFuel? = null
)

data class RangeByFuel(
    @SerializedName("totalAvailableRange") val totalAvailableRange: RangeValue? = null,
    @SerializedName("evModeRange") val evModeRange: RangeValue? = null,
    @SerializedName("gasModeRange") val gasModeRange: RangeValue? = null
)

data class RangeValue(
    @SerializedName("value") val value: Double = 0.0,
    @SerializedName("unit") val unit: Int = 1
)

data class TirePressure(
    @SerializedName("tirePressureWarningLampAll") val allLow: Int = 0,
    @SerializedName("tirePressureWarningLampFrontLeft") val frontLeft: Int = 0,
    @SerializedName("tirePressureWarningLampFrontRight") val frontRight: Int = 0,
    @SerializedName("tirePressureWarningLampRearLeft") val rearLeft: Int = 0,
    @SerializedName("tirePressureWarningLampRearRight") val rearRight: Int = 0
) {
    val anyLow get() = allLow == 1 || frontLeft == 1 || frontRight == 1 || rearLeft == 1 || rearRight == 1
}

data class TirePressureStatus(
    @SerializedName("tirePressureFrontLeft") val frontLeftPsi: Int = 0,
    @SerializedName("tirePressureFrontRight") val frontRightPsi: Int = 0,
    @SerializedName("tirePressureRearLeft") val rearLeftPsi: Int = 0,
    @SerializedName("tirePressureRearRight") val rearRightPsi: Int = 0,
    @SerializedName("datetimeFrontLeft") val frontLeftTime: String = "",
    @SerializedName("datetimeFrontRight") val frontRightTime: String = "",
    @SerializedName("datetimeRearLeft") val rearLeftTime: String = "",
    @SerializedName("datetimeRearRight") val rearRightTime: String = ""
)

data class WindowOpenStatus(
    @SerializedName("frontLeft") val frontLeft: Int = 0,
    @SerializedName("frontRight") val frontRight: Int = 0,
    @SerializedName("backLeft") val backLeft: Int = 0,
    @SerializedName("backRight") val backRight: Int = 0,
    @SerializedName("flOpenLevel") val frontLeftLevel: Int = 0,
    @SerializedName("frOpenLevel") val frontRightLevel: Int = 0,
    @SerializedName("blOpenLevel") val rearLeftLevel: Int = 0,
    @SerializedName("brOpenLevel") val rearRightLevel: Int = 0
) {
    val anyOpen: Boolean
        get() = frontLeft == 1 || frontRight == 1 || backLeft == 1 || backRight == 1 ||
            frontLeftLevel > 0 || frontRightLevel > 0 || rearLeftLevel > 0 || rearRightLevel > 0
}



data class SeatHeaterVentInfo(
    @SerializedName("drvSeatHeatState") val driverSeatHeatState: Int = 0,
    @SerializedName("astSeatHeatState") val passengerSeatHeatState: Int = 0,
    @SerializedName("rlSeatHeatState") val rearLeftSeatHeatState: Int = 0,
    @SerializedName("rrSeatHeatState") val rearRightSeatHeatState: Int = 0
)

data class LampWireStatus(
    @SerializedName("headLamp") val headLamp: LampGroupStatus? = null,
    @SerializedName("stopLamp") val stopLamp: LampGroupStatus? = null,
    @SerializedName("turnSignalLamp") val turnSignalLamp: LampGroupStatus? = null
)

data class LampGroupStatus(
    @SerializedName("headLampStatus") val headLampStatus: Boolean = false,
    @SerializedName("stopLampStatus") val stopLampStatus: Boolean = false,
    @SerializedName("turnSignalLampStatus") val turnSignalLampStatus: Boolean = false
) {
    val active: Boolean
        get() = headLampStatus || stopLampStatus || turnSignalLampStatus
}

data class VehicleLocation(
    @SerializedName("coord") val coord: Coordinate? = null,
    @SerializedName("speed") val speed: Speed? = null,
    @SerializedName(value = "heading", alternate = ["head"]) val heading: Int = 0
)

data class Coordinate(
    @SerializedName("lat") val lat: Double = 0.0,
    @SerializedName("lon") val lon: Double = 0.0,
    @SerializedName("alt") val alt: Double = 0.0,
    @SerializedName("type") val type: Int = 0
)

data class Speed(
    @SerializedName("unit") val unit: Int = 1,
    @SerializedName("value") val value: Double = 0.0
)

// ─── Remote Control Requests ──────────────────────────────────────────────────

data class LockUnlockRequest(
    @SerializedName("userName") val userName: String,
    @SerializedName("vin") val vin: String
)

data class RemoteStartRequest(
    @SerializedName("Ims") val ims: Int = 0,
    @SerializedName("airCtrl") val airCtrl: Int = 0,
    @SerializedName("airTemp") val airTemp: AirTempRequest,
    @SerializedName("defrost") val defrost: Boolean = false,
    @SerializedName("heating1") val heating1: Int = 0,
    @SerializedName("igniOnDuration") val igniOnDuration: Int = 10,
    @SerializedName("seatHeaterVentInfo") val seatHeaterVentInfo: SeatInfo? = null,
    @SerializedName("username") val username: String,
    @SerializedName("vin") val vin: String
)

data class AirTempRequest(
    @SerializedName("hvacTempType") val hvacTempType: Int = 0,
    @SerializedName("unit") val unit: Int = 1,
    @SerializedName("value") val value: String = "70"
)

data class SeatInfo(
    // Hyundai USA / Bluelinky expects the same seat-state keys used by the
    // vehicle status payload. The prior driverSeatHeatCool-style keys were
    // accepted by the JSON serializer but ignored by Hyundai's climate service.
    @SerializedName("drvSeatHeatState") val driverSeatHeatCool: Int = 0,
    @SerializedName("astSeatHeatState") val passengerSeatHeatCool: Int = 0,
    @SerializedName("rlSeatHeatState") val rearLeftSeatHeatCool: Int = 0,
    @SerializedName("rrSeatHeatState") val rearRightSeatHeatCool: Int = 0
)

data class EVChargeRequest(
    // Hyundai USA charge start/stop uses this body shape:
    // { "userName": "...", "vin": "...", "action": "start|stop" }
    @SerializedName("userName") val userName: String,
    @SerializedName("vin") val vin: String,
    @SerializedName("action") val action: String
)

data class ChargeControlRequest(
    @SerializedName("action") val action: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("userName") val userName: String,
    @SerializedName("vin") val vin: String
)

// ─── API Response Wrapper ─────────────────────────────────────────────────────

data class ApiResponse<T>(
    @SerializedName("status") val status: String = "",
    @SerializedName("error") val error: ApiError? = null,
    val data: T? = null
)

data class ApiError(
    @SerializedName("errorCode") val code: String = "",
    @SerializedName("errorDesc") val description: String = ""
)

// ─── Trip / History ───────────────────────────────────────────────────────────

data class TripSummary(
    val date: String,
    val distanceMiles: Double,
    val durationMinutes: Int,
    val avgSpeedMph: Double,
    val fuelUsed: Double? = null,
    val energyUsedKwh: Double? = null
)

// ─── Charge Targets ───────────────────────────────────────────────────────────

data class ReservChargeInfos(
    @SerializedName("targetSOClist") val targets: List<ChargeTarget> = emptyList(),
    @SerializedName("ect") val chargeWindow: ChargeScheduleWindow? = null,
    @SerializedName("offpeakPowerInfo") val offPeakPowerInfo: OffPeakPowerInfo? = null,
    @SerializedName("reservChargeInfo") val reservChargeInfo: ReservationChargeInfo? = null,
    @SerializedName("reserveChargeInfo2") val reserveChargeInfo2: ReservationChargeInfo2? = null
)

data class ChargeScheduleWindow(
    @SerializedName("start") val start: ScheduleEndpoint? = null,
    @SerializedName("end") val end: ScheduleEndpoint? = null
)

data class ScheduleEndpoint(
    @SerializedName("time") val time: ScheduleTime? = null,
    @SerializedName("day") val day: Int = -1
)

data class ScheduleTime(
    @SerializedName("timeSection") val timeSection: Int = 0,
    @SerializedName("time") val time: String = ""
)

data class OffPeakPowerInfo(
    @SerializedName("offPeakPowerFlag") val offPeakPowerFlag: Int = 0,
    @SerializedName("offPeakPowerTime1") val offPeakPowerTime1: OffPeakPowerTime? = null
)

data class OffPeakPowerTime(
    @SerializedName(value = "startTime", alternate = ["starttime"]) val startTime: ScheduleTime? = null,
    @SerializedName(value = "endTime", alternate = ["endtime"]) val endTime: ScheduleTime? = null
)



data class ReservationChargeInfo(
    @SerializedName("reservChargeInfoDetail") val detail: ReservationChargeInfoDetail? = null,
    @SerializedName("reservInfo") val reservInfo: ReservationInfo? = null,
    @SerializedName("dateTime") val dateTime: String = ""
)

data class ReservationChargeInfo2(
    @SerializedName("reservChargeInfoDetail") val detail: ReservationChargeInfoDetail? = null,
    @SerializedName("reservInfo") val reservInfo: ReservationInfo? = null
)

data class ReservationChargeInfoDetail(
    @SerializedName("reservChargeSet") val reservChargeSet: Boolean = false,
    @SerializedName("reservFatcSet") val reservFatcSet: ReservFatcSet? = null
)

data class ReservFatcSet(
    @SerializedName("defrost") val defrost: Boolean = false,
    @SerializedName("airTemp") val airTemp: AirTemp? = null,
    @SerializedName("airCtrl") val airCtrl: Int = 0
)

data class ReservationInfo(
    @SerializedName("time") val time: ScheduleTime? = null,
    @SerializedName("day") val day: List<Int> = emptyList()
)

data class ChargeTargetRequest(
    @SerializedName("username") val username: String,
    @SerializedName("vin") val vin: String,
    @SerializedName("targetSOClist") val targets: List<ChargeTarget>
)

data class SpaChargeTargetRequest(
    @SerializedName("targetSOClist") val targets: List<ChargeTarget>
)

data class ChargeTargetResponse(
    @SerializedName("resMsg") val resMsg: ChargeTargetResponseBody? = null,
    @SerializedName("targetSOClist") val targets: List<ChargeTarget> = emptyList(),
    @SerializedName("errorCode") val errorCode: String? = null,
    @SerializedName("errorMessage") val errorMessage: String? = null,
    @SerializedName("message") val message: String? = null
) {
    val reportedTargets: List<ChargeTarget>
        get() = resMsg?.targets ?: targets
}

data class ChargeTargetResponseBody(
    @SerializedName("targetSOClist") val targets: List<ChargeTarget> = emptyList()
)

data class ChargeTarget(
    @SerializedName("plugType") val plugType: Int = -1,
    @SerializedName("targetSOClevel") val targetSoc: Int = 0
)



// ─── Local UX models ─────────────────────────────────────────────────────────

data class CommandHistoryEntry(
    val timestampMillis: Long = 0L,
    val title: String = "",
    val detail: String = "",
    val successful: Boolean = true,
    val vehicleName: String = ""
)

data class WidgetVehicleSnapshot(
    val vehicleName: String = "BlueBridge",
    val vehicleVin: String = "",
    val vehicleId: String = "",
    val registrationId: String = "",
    val generation: String = "3",
    val brandIndicator: String = "H",
    val modelCode: String = "",
    val doorsLocked: Boolean? = null,
    val batteryPercent: Int? = null,
    val rangeMiles: Int? = null,
    val chargingLabel: String = "Open app to refresh",
    val message: String = "Tap to open app",
    val detailOne: String = "Doors —",
    val detailTwo: String = "Climate —",
    val detailThree: String = "Tires —",
    val updatedAtMillis: Long = 0L
)

// ─── Official-app-style feature models ───────────────────────────────────────

data class DriverProfile(
    val id: String,
    val displayName: String,
    val role: String = "Driver",
    val photoUri: String? = null,
    val isActive: Boolean = false
) {
    val initials: String
        get() = displayName
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "D" }
}

data class SurroundViewSnapshot(
    val frontImageUrl: String? = null,
    val rearImageUrl: String? = null,
    val leftImageUrl: String? = null,
    val rightImageUrl: String? = null,
    val capturedAtMillis: Long = 0L
) {
    val hasAnyImage: Boolean
        get() = !frontImageUrl.isNullOrBlank() ||
                !rearImageUrl.isNullOrBlank() ||
                !leftImageUrl.isNullOrBlank() ||
                !rightImageUrl.isNullOrBlank()
}

data class FeatureCommandRequest(
    @SerializedName("username") val username: String,
    @SerializedName("vin") val vin: String,
    @SerializedName("enabled") val enabled: Boolean? = null
)
