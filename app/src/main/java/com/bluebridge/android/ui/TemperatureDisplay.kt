package com.bluebridge.android.ui

import com.bluebridge.android.data.models.AirTemp
import kotlin.math.roundToInt

/** Display helpers; vehicle commands treat setpoints as Fahrenheit unless noted in repository. */
object TemperatureDisplay {
    fun fahrenheitToCelsius(f: Double): Double = (f - 32.0) * 5.0 / 9.0

    fun celsiusToFahrenheit(c: Double): Double = c * 9.0 / 5.0 + 32.0

    /** Status API: [AirTemp.unit] 0 = Fahrenheit, non-zero = Celsius (existing Status screen convention). */
    fun formatVehicleAirTemp(airTemp: AirTemp, displayUnit: String): String {
        val v = airTemp.value.toDoubleOrNull() ?: return "--"
        return when (displayUnit) {
            "C" -> when (airTemp.unit) {
                0 -> "${fahrenheitToCelsius(v).roundToInt()}°C"
                else -> "${v.roundToInt()}°C"
            }
            else -> when (airTemp.unit) {
                0 -> "${v.roundToInt()}°F"
                else -> "${celsiusToFahrenheit(v).roundToInt()}°F"
            }
        }
    }

    fun formatHvacSetpoint(setpointFahrenheit: Float, displayUnit: String): String =
        if (displayUnit == "C") "${fahrenheitToCelsius(setpointFahrenheit.toDouble()).roundToInt()}°C"
        else "${setpointFahrenheit.toInt()}°F"

    fun sliderDisplayValue(setpointFahrenheit: Float, displayUnit: String): Float =
        if (displayUnit == "C") fahrenheitToCelsius(setpointFahrenheit.toDouble()).toFloat().coerceIn(17f, 30f)
        else setpointFahrenheit.coerceIn(62f, 82f)

    fun setpointFahrenheitFromSlider(sliderValue: Float, displayUnit: String): Float =
        if (displayUnit == "C") celsiusToFahrenheit(sliderValue.toDouble()).toFloat().coerceIn(62f, 82f)
        else sliderValue.coerceIn(62f, 82f)

    fun hvacSliderRange(displayUnit: String): ClosedFloatingPointRange<Float> =
        if (displayUnit == "C") 17f..30f else 62f..82f

    fun hvacSliderSteps(displayUnit: String): Int =
        if (displayUnit == "C") 25 else 19
}
