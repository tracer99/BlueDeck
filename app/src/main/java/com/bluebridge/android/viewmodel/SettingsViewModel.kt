package com.bluebridge.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluebridge.android.data.models.UiColorSlot
import com.bluebridge.android.data.repository.PreferencesManager
import com.bluebridge.android.data.repository.SecureCredentialsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val secureCredentialsManager: SecureCredentialsManager
) : ViewModel() {

    val region = preferencesManager.region
        .stateIn(viewModelScope, SharingStarted.Eagerly, "US_HYUNDAI")

    val temperatureUnit = preferencesManager.temperatureUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, "F")

    val distanceUnit = preferencesManager.distanceUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, "MI")

    val timeZoneMode = preferencesManager.timeZoneMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "DEVICE")

    val timeFormat = preferencesManager.timeFormat
        .stateIn(viewModelScope, SharingStarted.Eagerly, "12_HOUR")

    val biometricEnabled = preferencesManager.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val biometricUnlockMode = preferencesManager.biometricUnlockMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, "APP_OPEN")

    val stayLoggedIn30Days = preferencesManager.stayLoggedIn30Days
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val lastBiometricUnlockAt = preferencesManager.lastBiometricUnlockAt
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1L)

    val walkAwayLockEnabled = preferencesManager.walkAwayLockEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val walkAwayLockDelaySeconds = preferencesManager.walkAwayLockDelaySeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, 60)

    val walkAwayBluetoothName = preferencesManager.walkAwayBluetoothName
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val walkAwayBluetoothAddress = preferencesManager.walkAwayBluetoothAddress
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val appTheme = preferencesManager.appTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, "hyundai_night")

    val uiColorOverrides = preferencesManager.uiColorOverrides
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.bluebridge.android.data.models.UiColorOverrides.EMPTY)

    val servicePin = preferencesManager.servicePin
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setServicePin(pin: String) = viewModelScope.launch {
        preferencesManager.setServicePin(pin)
    }

    fun setRegion(region: String) = viewModelScope.launch {
        preferencesManager.setRegion(region)
    }

    fun setTemperatureUnit(unit: String) = viewModelScope.launch {
        preferencesManager.setTemperatureUnit(unit)
    }

    fun setDistanceUnit(unit: String) = viewModelScope.launch {
        preferencesManager.setDistanceUnit(unit)
    }

    fun setTimeZoneMode(mode: String) = viewModelScope.launch {
        preferencesManager.setTimeZoneMode(mode)
    }

    fun setTimeFormat(format: String) = viewModelScope.launch {
        preferencesManager.setTimeFormat(format)
    }

    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setBiometricEnabled(enabled)
        if (!enabled) secureCredentialsManager.clearSavedCredentials()
    }

    fun setBiometricUnlockMode(mode: String) = viewModelScope.launch {
        preferencesManager.setBiometricUnlockMode(mode)
    }

    fun setStayLoggedIn30Days(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setStayLoggedIn30Days(enabled)
    }

    fun setLastBiometricUnlockAt(timestamp: Long) = viewModelScope.launch {
        preferencesManager.setLastBiometricUnlockAt(timestamp)
    }

    fun setWalkAwayLockEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setWalkAwayLockEnabled(enabled)
    }

    fun setWalkAwayLockDelaySeconds(seconds: Int) = viewModelScope.launch {
        preferencesManager.setWalkAwayLockDelaySeconds(seconds)
    }

    fun setWalkAwayBluetoothDevice(name: String, address: String) = viewModelScope.launch {
        preferencesManager.setWalkAwayBluetoothDevice(name, address)
    }

    fun clearWalkAwayBluetoothDevice() = viewModelScope.launch {
        preferencesManager.clearWalkAwayBluetoothDevice()
    }

    fun setAppTheme(themeId: String) = viewModelScope.launch {
        preferencesManager.setAppTheme(themeId)
    }

    fun setUiColor(slot: UiColorSlot, hex: String?) = viewModelScope.launch {
        preferencesManager.setUiColorOverride(slot, hex)
    }

    fun resetUiColor(slot: UiColorSlot) = viewModelScope.launch {
        preferencesManager.resetUiColorOverride(slot)
    }

    fun resetUiColors() = viewModelScope.launch {
        preferencesManager.resetUiColorOverrides()
    }
}
