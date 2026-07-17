package com.bluedeck.data.models

import android.content.Context
import kotlin.math.roundToInt

/**
 * Saved climate start presets (cabin + optional seat/wheel comfort).
 * Temperatures are stored as Fahrenheit for the API; defaults and UI use the
 * user's preferred unit from Settings.
 * Stored in SharedPreferences under the legacy [PREFS_NAME] key for migration.
 *
 * [stopsClimate] presets (All Off) are fixed stop actions — not configurable start settings.
 */
data class ClimatePreset(
    val name: String,
    val tempF: String = "72",
    val defrost: Boolean = false,
    val heatedSteering: Boolean = false,
    val durationMinutes: Int = DEFAULT_CLIMATE_DURATION_MINUTES,
    val driverSeat: Int = 2,
    val passengerSeat: Int = 2,
    val rearLeftSeat: Int = 2,
    val rearRightSeat: Int = 2,
    val stopsClimate: Boolean = false
)

const val MIN_CLIMATE_DURATION_MINUTES = 5
const val MAX_CLIMATE_DURATION_MINUTES = 30
const val DEFAULT_CLIMATE_DURATION_MINUTES = 10

fun coerceClimateDurationMinutes(minutes: Int): Int =
    minutes.coerceIn(MIN_CLIMATE_DURATION_MINUTES, MAX_CLIMATE_DURATION_MINUTES)

object ClimatePresetsStore {
    private const val PREFS_NAME = "seat_climate_presets"

    /**
     * Default presets use round values in [preferredUnit] (C or F), then store °F.
     * Celsius: Warm 24°C, Cool 20°C.
     * Fahrenheit: Warm 76°F, Cool 68°F.
     * All Off is a fixed stop-climate action.
     */
    fun defaults(preferredUnit: String = "F"): List<ClimatePreset> {
        val celsius = preferredUnit.equals("C", ignoreCase = true)
        fun storedTempF(displayValue: Int): String =
            if (celsius) celsiusToFahrenheit(displayValue).toString() else displayValue.toString()

        return listOf(
            ClimatePreset(
                name = "Warm",
                tempF = storedTempF(if (celsius) 24 else 76),
                defrost = false,
                heatedSteering = true,
                durationMinutes = DEFAULT_CLIMATE_DURATION_MINUTES,
                driverSeat = 7,
                passengerSeat = 7,
                rearLeftSeat = 7,
                rearRightSeat = 7
            ),
            ClimatePreset(
                name = "Cool",
                tempF = storedTempF(if (celsius) 20 else 68),
                defrost = false,
                heatedSteering = false,
                durationMinutes = DEFAULT_CLIMATE_DURATION_MINUTES,
                driverSeat = 4,
                passengerSeat = 4,
                rearLeftSeat = 2,
                rearRightSeat = 2
            ),
            ClimatePreset(
                name = "All Off",
                stopsClimate = true
            )
        )
    }

    fun load(context: Context, preferredUnit: String = "F"): List<ClimatePreset> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return defaults(preferredUnit).mapIndexed { index, fallback ->
            if (fallback.stopsClimate) {
                fallback
            } else {
                ClimatePreset(
                    name = prefs.getString("preset_${index}_name", fallback.name) ?: fallback.name,
                    tempF = prefs.getString("preset_${index}_temp_f", fallback.tempF)
                        ?.takeIf { it.toIntOrNull() != null }
                        ?: fallback.tempF,
                    defrost = if (prefs.contains("preset_${index}_defrost")) {
                        prefs.getBoolean("preset_${index}_defrost", fallback.defrost)
                    } else {
                        fallback.defrost
                    },
                    heatedSteering = if (prefs.contains("preset_${index}_heated_steering")) {
                        prefs.getBoolean("preset_${index}_heated_steering", fallback.heatedSteering)
                    } else {
                        fallback.heatedSteering
                    },
                    durationMinutes = if (prefs.contains("preset_${index}_duration")) {
                        coerceClimateDurationMinutes(
                            prefs.getInt("preset_${index}_duration", fallback.durationMinutes)
                        )
                    } else {
                        fallback.durationMinutes
                    },
                    driverSeat = prefs.getInt("preset_${index}_driver", fallback.driverSeat),
                    passengerSeat = prefs.getInt("preset_${index}_passenger", fallback.passengerSeat),
                    rearLeftSeat = prefs.getInt("preset_${index}_rear_left", fallback.rearLeftSeat),
                    rearRightSeat = prefs.getInt("preset_${index}_rear_right", fallback.rearRightSeat),
                    stopsClimate = false
                )
            }
        }
    }

    fun save(context: Context, presets: List<ClimatePreset>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                presets.take(3).forEachIndexed { index, preset ->
                    if (preset.stopsClimate) return@forEachIndexed
                    putString("preset_${index}_name", preset.name.trim().ifBlank { "Preset ${index + 1}" })
                    putString(
                        "preset_${index}_temp_f",
                        preset.tempF.toIntOrNull()?.coerceIn(62, 82)?.toString() ?: "72"
                    )
                    putBoolean("preset_${index}_defrost", preset.defrost)
                    putBoolean("preset_${index}_heated_steering", preset.heatedSteering)
                    putInt("preset_${index}_duration", coerceClimateDurationMinutes(preset.durationMinutes))
                    putInt("preset_${index}_driver", preset.driverSeat)
                    putInt("preset_${index}_passenger", preset.passengerSeat)
                    putInt("preset_${index}_rear_left", preset.rearLeftSeat)
                    putInt("preset_${index}_rear_right", preset.rearRightSeat)
                }
            }
            .apply()
    }

    private fun celsiusToFahrenheit(celsius: Int): Int =
        (celsius * 9.0 / 5.0 + 32).roundToInt()
}
