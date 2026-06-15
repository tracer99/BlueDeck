package com.bluedeck.ui.theme

import java.util.Locale
import kotlin.math.roundToInt

private fun prefersKilometers(distanceUnit: String): Boolean = distanceUnit.equals("KM", ignoreCase = true)

fun milesToDisplayDistance(miles: Double, distanceUnit: String): Double =
    if (prefersKilometers(distanceUnit)) miles * 1.609344 else miles

fun formatDistanceFromMiles(miles: Double, distanceUnit: String): String {
    val value = milesToDisplayDistance(miles, distanceUnit).roundToInt()
    return if (prefersKilometers(distanceUnit)) "$value km" else "$value mi"
}

fun formatDistanceFromMiles(miles: Int, distanceUnit: String): String =
    formatDistanceFromMiles(miles.toDouble(), distanceUnit)

fun formatOdometerFromMiles(miles: Int, distanceUnit: String): String {
    if (miles <= 0) return "—"
    val value = milesToDisplayDistance(miles.toDouble(), distanceUnit).roundToInt()
    val unit = if (prefersKilometers(distanceUnit)) "km" else "mi"
    return String.format(Locale.US, "%,d %s", value, unit)
}
