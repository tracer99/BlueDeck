package com.bluedeck.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluedeck.data.api.Region
import com.bluedeck.data.repository.PreferencesManager
import com.bluedeck.data.repository.SecureCredentialsManager
import com.bluedeck.data.repository.Result
import com.bluedeck.data.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val kiaOtpRequired: Boolean = false
)

private data class PendingLoginCredentials(
    val username: String,
    val password: String,
    val servicePin: String,
    val saveForBiometrics: Boolean
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: VehicleRepository,
    private val preferencesManager: PreferencesManager,
    private val secureCredentialsManager: SecureCredentialsManager
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean?> = preferencesManager.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val region: StateFlow<String> = preferencesManager.region
        .stateIn(viewModelScope, SharingStarted.Eagerly, Region.US_HYUNDAI.name)

    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()
    private var pendingLoginCredentials: PendingLoginCredentials? = null

    private val savedCredentialsAvailable = MutableStateFlow(secureCredentialsManager.hasSavedCredentials())

    val passwordRequired: StateFlow<Boolean> = preferencesManager.passwordRequired
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val biometricLoginAvailable: StateFlow<Boolean> = combine(
        preferencesManager.biometricEnabled,
        savedCredentialsAvailable,
        passwordRequired
    ) { biometricEnabled, hasSavedCredentials, requiresPassword ->
        biometricEnabled && hasSavedCredentials && !requiresPassword
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val biometricSessionRecoveryAvailable: StateFlow<Boolean> = combine(
        biometricLoginAvailable,
        preferencesManager.hasRecoverableSession
    ) { biometricAvailable, hasRecoverableSession ->
        biometricAvailable && hasRecoverableSession
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setRegion(region: Region) {
        viewModelScope.launch {
            preferencesManager.setRegion(region.name)
            pendingLoginCredentials = null
            _loginUiState.value = LoginUiState()
        }
    }

    fun login(username: String, password: String, servicePin: String = "", saveForBiometrics: Boolean = true) {
        if (username.isBlank() || password.isBlank()) {
            _loginUiState.value = LoginUiState(error = "Please enter your email and password")
            return
        }
        viewModelScope.launch {
            performLogin(
                username = username.trim(),
                password = password,
                servicePin = servicePin.trim(),
                saveForBiometrics = saveForBiometrics
            )
        }
    }

    fun loginWithSavedCredentials() {
        val savedCredentials = secureCredentialsManager.getSavedCredentials()
        if (savedCredentials == null) {
            _loginUiState.value = LoginUiState(error = "No saved biometric login is available. Sign in with your password once first.")
            savedCredentialsAvailable.value = false
            return
        }

        viewModelScope.launch {
            performLogin(
                username = savedCredentials.username,
                password = savedCredentials.password,
                servicePin = savedCredentials.servicePin,
                saveForBiometrics = true
            )
        }
    }

    private suspend fun performLogin(
        username: String,
        password: String,
        servicePin: String,
        saveForBiometrics: Boolean
    ) {
        _loginUiState.value = LoginUiState(isLoading = true)
        when (val result = repository.login(username, password, servicePin)) {
            is Result.Success -> {
                if (saveForBiometrics) {
                    secureCredentialsManager.saveCredentials(username, password, servicePin)
                    savedCredentialsAvailable.value = true
                }
                android.util.Log.d("BlueDeck", "Login success")
                _loginUiState.value = LoginUiState(success = true)
            }
            is Result.Error -> {
                android.util.Log.d("BlueDeck", "Login error: ${result.message}")
                if (result.code == 460) {
                    pendingLoginCredentials = PendingLoginCredentials(username, password, servicePin, saveForBiometrics)
                    _loginUiState.value = LoginUiState(error = result.message, kiaOtpRequired = true)
                } else {
                    _loginUiState.value = LoginUiState(error = result.message)
                }
            }
            else -> {}
        }
    }

    fun submitKiaOtp(code: String) {
        val pending = pendingLoginCredentials
        if (pending == null) {
            _loginUiState.value = LoginUiState(error = "Kia verification expired. Sign in again to request a new code.", kiaOtpRequired = false)
            return
        }
        if (code.isBlank()) {
            _loginUiState.value = LoginUiState(error = "Enter the Kia verification code.", kiaOtpRequired = true)
            return
        }
        viewModelScope.launch {
            _loginUiState.value = LoginUiState(isLoading = true, kiaOtpRequired = true)
            when (val result = repository.completeKiaUsOtpLogin(code)) {
                is Result.Success -> {
                    if (pending.saveForBiometrics) {
                        secureCredentialsManager.saveCredentials(pending.username, pending.password, pending.servicePin)
                        savedCredentialsAvailable.value = true
                    }
                    pendingLoginCredentials = null
                    _loginUiState.value = LoginUiState(success = true)
                }
                is Result.Error -> {
                    _loginUiState.value = LoginUiState(error = result.message, kiaOtpRequired = true)
                }
            }
        }
    }

    fun requirePasswordLogin() {
        viewModelScope.launch {
            repository.logout(requirePassword = true)
            pendingLoginCredentials = null
            _loginUiState.value = LoginUiState()
        }
    }

    fun logout(clearSavedCredentials: Boolean = false) {
        viewModelScope.launch {
            repository.logout(requirePassword = true)
            if (clearSavedCredentials) {
                secureCredentialsManager.clearSavedCredentials()
                savedCredentialsAvailable.value = false
            }
            pendingLoginCredentials = null
            _loginUiState.value = LoginUiState()
        }
    }

    fun clearError() {
        _loginUiState.value = _loginUiState.value.copy(error = null)
    }
}
