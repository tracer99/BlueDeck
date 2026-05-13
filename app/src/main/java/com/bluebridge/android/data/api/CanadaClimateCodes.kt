package com.bluebridge.android.data.api

import kotlin.math.roundToInt

/**
 * Port of bluelinky's Canadian Celsius → HVAC temp code mapping (hex index + "H" suffix).
 */
object CanadaClimateCodes {
    private val tempRangeC: List<Double> = buildList {
        var v = 16.0
        while (v <= 32.0 + 1e-9) {
            add(v)
            v += 0.5
        }
    }

    fun celsiusToTempCode(celsius: Double): String {
        val snapped = (celsius * 2).roundToInt() / 2.0
        val idx = tempRangeC.indexOfFirst { kotlin.math.abs(it - snapped) < 0.01 }
            .takeIf { it >= 0 } ?: tempRangeC.lastIndex
        val hex = idx.toString(16).uppercase().padStart(2, '0')
        return "${hex}H".padStart(3, '0')
    }

    fun fahrenheitToCelsius(f: Double): Double = (f - 32.0) * 5.0 / 9.0
}
