package com.bluebridge.android.data.api

import com.bluebridge.android.data.models.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object CanadaStatusMapper {

    fun mapStatus(json: String): VehicleStatusData? {
        if (json.isBlank()) return null
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            mapStatusObject(root)
        } catch (_: Exception) {
            null
        }
    }

    fun mapStatusFromResult(result: com.google.gson.JsonElement?): VehicleStatusData? {
        if (result == null || !result.isJsonObject) return null
        val obj = result.asJsonObject
        val status = obj.get("status")?.takeIf { it.isJsonObject }?.asJsonObject ?: obj
        return mapStatusObject(status)
    }

    private fun mapStatusObject(s: JsonObject): VehicleStatusData {
        val doorLock = s.boolOr("doorLock", "doorLockStatus")
        val doorLockStatus = when {
            doorLock -> "LOCKED"
            else -> "UNLOCKED"
        }
        val doorOpen = s.obj("doorOpen")?.let { o ->
            DoorOpenStatus(
                frontLeft = o.intOr0("frontLeft"),
                frontRight = o.intOr0("frontRight"),
                backLeft = o.intOr0("backLeft"),
                backRight = o.intOr0("backRight")
            )
        }
        val air = s.obj("airTemp")
        val airTemp = if (air != null) {
            AirTemp(value = air.stringOr("value", "20"), unit = air.intOr0("unit"))
        } else null

        val ev = s.obj("evStatus")?.let { e ->
            val drv = e.array("drvDistance")?.firstOrNull()?.asJsonObject?.obj("rangeByFuel")
            val rangeVal = drv?.obj("evModeRange") ?: drv?.obj("totalAvailableRange")
            EVStatus(
                batteryCharge = e.boolOr("batteryCharge"),
                batteryStatus = e.intOr0("batteryStatus"),
                batteryPlugin = e.intOr0("batteryPlugin"),
                drvDistance = if (rangeVal != null) {
                    listOf(DriveDistance(rangeByFuel = RangeByFuel(evModeRange = rangeVal.toRangeValue())))
                } else emptyList()
            )
        }

        val dteObj = s.obj("dte")
        val dte = if (dteObj != null) {
            Dte(
                unit = dteObj.intOr0("unit"),
                value = dteObj.doubleOr0("value"),
                fuelLevelPercent = dteObj.doubleOrNull("fuelLevel")
            )
        } else null

        val tire = s.obj("tirePressureLamp")?.let { t ->
            TirePressure(
                allLow = t.intOr0("tirePressureWarningLampAll"),
                frontLeft = t.intOr0("tirePressureWarningLampFrontLeft"),
                frontRight = t.intOr0("tirePressureWarningLampFrontRight"),
                rearLeft = t.intOr0("tirePressureWarningLampRearLeft"),
                rearRight = t.intOr0("tirePressureWarningLampRearRight")
            )
        }

        return VehicleStatusData(
            doorLock = doorLock,
            doorLockStatus = doorLockStatus,
            doorOpenStatus = doorOpen,
            trunkOpenStatus = s.boolOr("trunkOpen"),
            hoodOpenStatus = s.boolOr("hoodOpen"),
            engineStatus = s.boolOr("engine"),
            ignitionStatus = if (s.boolOr("engine")) "ON" else "OFF",
            airCtrlOn = s.boolOr("airCtrlOn"),
            airTemp = airTemp,
            defrost = s.boolOr("defrost"),
            sideBackWindowHeat = s.intOr0("sideBackWindowHeat"),
            steerWheelHeat = if (s.boolOr("steerWheelHeat")) 1 else 0,
            dte = dte,
            evStatus = ev,
            tirePressureLamp = tire,
            totalMileage = s.intOr0("odometer")
        )
    }

    fun mapLocation(result: com.google.gson.JsonElement?): VehicleLocation? {
        if (result == null || !result.isJsonObject) return null
        val o = result.asJsonObject
        val coord = o.obj("coord") ?: o.obj("gpsDetail")?.obj("coord")
        if (coord == null) return VehicleLocation()
        val lat = coord.doubleOr0("lat")
        val lon = coord.doubleOr0("lon")
        return VehicleLocation(coord = Coordinate(lat = lat, lon = lon), heading = o.intOr0("head"))
    }

    private fun JsonObject.toRangeValue(): RangeValue =
        RangeValue(value = doubleOr0("value"), unit = intOr0("unit"))

    private fun JsonObject.obj(name: String): JsonObject? =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.array(name: String): List<com.google.gson.JsonElement>? =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList()

    private fun JsonObject.boolOr(vararg names: String): Boolean {
        for (n in names) {
            val el = get(n) ?: continue
            when {
                el.isJsonPrimitive && el.asJsonPrimitive.isBoolean -> return el.asBoolean
                el.isJsonPrimitive && el.asJsonPrimitive.isNumber -> return el.asInt != 0
                el.isJsonPrimitive -> {
                    val s = el.asString.lowercase()
                    if (s == "true" || s == "1" || s == "on") return true
                }
            }
        }
        return false
    }

    private fun JsonObject.intOr0(name: String): Int =
        get(name)?.takeIf { it.isJsonPrimitive && !it.asJsonPrimitive.isString }?.asInt ?: 0

    private fun JsonObject.doubleOr0(name: String): Double =
        get(name)?.asDouble ?: 0.0

    private fun JsonObject.doubleOrNull(name: String): Double? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asDouble

    private fun JsonObject.stringOr(name: String, default: String): String =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: default
}
