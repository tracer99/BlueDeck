package com.bluedeck.ui.theme

import kotlin.math.roundToInt

private fun prefersCelsius(unit: String): Boolean = unit.equals("C", ignoreCase = true)

/** API air-temp unit code: 0 = Celsius, 1 = Fahrenheit. */
private fun apiValueIsFahrenheit(apiUnit: Int): Boolean = apiUnit == 1

private fun parseTemperatureValue(value: String): Int? {
    val trimmed = value.trim()
    if (trimmed.isBlank() || trimmed.equals("OFF", ignoreCase = true) || trimmed.equals("00H", ignoreCase = true)) {
        return null
    }
    return trimmed.filter { it.isDigit() || it == '-' }.toIntOrNull()
        ?: trimmed.toDoubleOrNull()?.roundToInt()
}

fun fahrenheitToCelsius(fahrenheit: Int): Int = ((fahrenheit - 32) * 5.0 / 9.0).roundToInt()

fun celsiusToFahrenheit(celsius: Int): Int = (celsius * 9.0 / 5.0 + 32).roundToInt()

fun apiTemperatureToFahrenheit(value: String, apiUnit: Int): Int {
    val parsed = parseTemperatureValue(value) ?: return 72
    return if (apiValueIsFahrenheit(apiUnit)) parsed else celsiusToFahrenheit(parsed)
}

fun apiTemperatureToPreferredValue(value: String, apiUnit: Int, preferredUnit: String): Int {
    val fahrenheit = apiTemperatureToFahrenheit(value, apiUnit)
    return if (prefersCelsius(preferredUnit)) fahrenheitToCelsius(fahrenheit) else fahrenheit
}

fun apiTemperatureToPreferredLabel(value: String, apiUnit: Int, preferredUnit: String): String =
    apiTemperatureToPreferredValue(value, apiUnit, preferredUnit).toString()

fun climateDisplayValueFromF(tempF: String, preferredUnit: String): Int {
    val fahrenheit = tempF.toIntOrNull() ?: 72
    return if (prefersCelsius(preferredUnit)) fahrenheitToCelsius(fahrenheit) else fahrenheit
}

fun climateFahrenheitFromDisplay(displayValue: Int, preferredUnit: String): Int =
    if (prefersCelsius(preferredUnit)) celsiusToFahrenheit(displayValue) else displayValue

fun climateTemperatureLabelFromF(tempF: String, preferredUnit: String): String {
    val display = climateDisplayValueFromF(tempF, preferredUnit)
    val suffix = if (prefersCelsius(preferredUnit)) "C" else "F"
    return "${display}°$suffix"
}

fun climateSliderRange(preferredUnit: String): ClosedFloatingPointRange<Float> =
    if (prefersCelsius(preferredUnit)) 17f..28f else 62f..82f

fun climateSliderSteps(preferredUnit: String): Int =
    if (prefersCelsius(preferredUnit)) 11 else 20
