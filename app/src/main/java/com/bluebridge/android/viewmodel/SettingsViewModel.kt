package com.bluebridge.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluebridge.android.data.models.UiColorSlot
import com.bluebridge.android.data.repository.PreferencesManager
import com.bluebridge.android.data.repository.SecureCredentialsManager
import com.bluebridge.android.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val secureCredentialsManager: SecureCredentialsManager,
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    val region = preferencesManager.region
        .stateIn(viewModelScope, SharingStarted.Eagerly, "US_HYUNDAI")

    val regionSetupCompleted = preferencesManager.regionSetupCompleted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val temperatureUnit = preferencesManager.temperatureUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, "F")

    val biometricEnabled = preferencesManager.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
        val prev = preferencesManager.region.first()
        if (prev != region) {
            val loggedIn = preferencesManager.isLoggedIn.first()
            if (loggedIn) {
                vehicleRepository.logout(requirePassword = true)
            }
        }
        preferencesManager.setRegion(region)
    }

    fun setRegionFromOnboarding(region: String) = viewModelScope.launch {
        val prev = preferencesManager.region.first()
        if (prev != region) {
            val loggedIn = preferencesManager.isLoggedIn.first()
            if (loggedIn) {
                vehicleRepository.logout(requirePassword = true)
            }
        }
        preferencesManager.setRegion(region)
        preferencesManager.setRegionSetupCompleted(true)
    }

    fun setTemperatureUnit(unit: String) = viewModelScope.launch {
        preferencesManager.setTemperatureUnit(unit)
    }

    fun setBiometricEnabled(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setBiometricEnabled(enabled)
        if (!enabled) secureCredentialsManager.clearSavedCredentials()
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
