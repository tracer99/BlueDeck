package com.bluedeck.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluedeck.data.api.Region
import com.bluedeck.data.auth.OtpChallengeUi
import com.bluedeck.data.auth.OtpDeliveryMethod
import com.bluedeck.data.auth.OTP_REQUIRED_CODE
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
    val otpChallenge: OtpChallengeUi? = null,
    val resendCooldownSeconds: Int = 0
)

private data class PendingLoginCredentials(
    val username: String,
    val password: String,
    val servicePin: String,
    val saveForBiometrics: Boolean,
    val stayLoggedIn30Days: Boolean
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

    val otpPending: StateFlow<Boolean> = preferencesManager.otpPending
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val otpPendingUsername: StateFlow<String?> = preferencesManager.otpPendingUsername
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState: StateFlow<LoginUiState> = _loginUiState.asStateFlow()
    private var pendingLoginCredentials: PendingLoginCredentials? = null

    private val savedCredentialsAvailable = MutableStateFlow(secureCredentialsManager.hasSavedCredentials())

    val passwordRequired: StateFlow<Boolean> = preferencesManager.passwordRequired
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val biometricLoginAvailable: StateFlow<Boolean> = combine(
        preferencesManager.biometricEnabled,
        savedCredentialsAvailable,
        passwordRequired,
        otpPending
    ) { biometricEnabled, hasSavedCredentials, requiresPassword, otpIsPending ->
        biometricEnabled && hasSavedCredentials && !requiresPassword && !otpIsPending
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val biometricSessionRecoveryAvailable: StateFlow<Boolean> = combine(
        biometricLoginAvailable,
        preferencesManager.hasRecoverableSession
    ) { biometricAvailable, hasRecoverableSession ->
        biometricAvailable && hasRecoverableSession
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            if (preferencesManager.otpPending.first()) {
                resumePendingOtp()
            }
        }
    }

    fun setRegion(region: Region) {
        viewModelScope.launch {
            preferencesManager.setRegion(region.name)
            pendingLoginCredentials = null
            _loginUiState.value = LoginUiState()
        }
    }

    fun login(
        username: String,
        password: String,
        servicePin: String = "",
        saveForBiometrics: Boolean = true,
        stayLoggedIn30Days: Boolean = true
    ) {
        if (username.isBlank() || password.isBlank()) {
            _loginUiState.value = LoginUiState(error = "Please enter your email and password")
            return
        }
        viewModelScope.launch {
            performLogin(
                username = username.trim(),
                password = password,
                servicePin = servicePin.trim(),
                saveForBiometrics = saveForBiometrics,
                stayLoggedIn30Days = stayLoggedIn30Days
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
            val stayLoggedIn30Days = preferencesManager.stayLoggedIn30Days.first()
            performLogin(
                username = savedCredentials.username,
                password = savedCredentials.password,
                servicePin = savedCredentials.servicePin,
                saveForBiometrics = true,
                stayLoggedIn30Days = stayLoggedIn30Days
            )
        }
    }

    private suspend fun performLogin(
        username: String,
        password: String,
        servicePin: String,
        saveForBiometrics: Boolean,
        stayLoggedIn30Days: Boolean
    ) {
        preferencesManager.setStayLoggedIn30Days(stayLoggedIn30Days)
        _loginUiState.value = LoginUiState(isLoading = true)
        when (val result = repository.login(username, password, servicePin)) {
            is Result.Success -> {
                if (saveForBiometrics || stayLoggedIn30Days) {
                    secureCredentialsManager.saveCredentials(username, password, servicePin)
                    savedCredentialsAvailable.value = true
                }
                android.util.Log.d("BlueDeck", "Login success")
                pendingLoginCredentials = null
                _loginUiState.value = LoginUiState(success = true)
            }
            is Result.Error -> {
                android.util.Log.d("BlueDeck", "Login error: ${result.message}")
                if (result.code == OTP_REQUIRED_CODE) {
                    pendingLoginCredentials = PendingLoginCredentials(
                        username = username,
                        password = password,
                        servicePin = servicePin,
                        saveForBiometrics = saveForBiometrics,
                        stayLoggedIn30Days = stayLoggedIn30Days
                    )
                    _loginUiState.value = LoginUiState(
                        otpChallenge = repository.getOtpChallengeUi()
                    )
                } else {
                    _loginUiState.value = LoginUiState(error = result.message)
                }
            }
            else -> {}
        }
    }

    fun resumePendingOtp() {
        viewModelScope.launch {
            val stayLoggedIn30Days = preferencesManager.stayLoggedIn30Days.first()
            val savedCredentials = secureCredentialsManager.getSavedCredentials()
            if (savedCredentials != null) {
                pendingLoginCredentials = PendingLoginCredentials(
                    username = savedCredentials.username,
                    password = savedCredentials.password,
                    servicePin = savedCredentials.servicePin,
                    saveForBiometrics = true,
                    stayLoggedIn30Days = stayLoggedIn30Days
                )
            }
            val challenge = repository.resumeOtpIfPending() ?: repository.getOtpChallengeUi()
            if (challenge != null) {
                _loginUiState.value = LoginUiState(
                    otpChallenge = challenge
                )
            }
        }
    }

    fun submitOtp(code: String, rememberDevice: Boolean = true) {
        val pending = pendingLoginCredentials
        if (pending == null) {
            _loginUiState.value = LoginUiState(error = "Verification expired. Sign in again to request a new code.")
            return
        }
        if (code.isBlank()) {
            _loginUiState.value = _loginUiState.value.copy(
                error = "Enter the verification code.",
                otpChallenge = repository.getOtpChallengeUi()
            )
            return
        }
        viewModelScope.launch {
            val challenge = repository.getOtpChallengeUi()
            _loginUiState.value = LoginUiState(isLoading = true, otpChallenge = challenge)
            when (val result = repository.completeOtpLogin(code, rememberDevice)) {
                is Result.Success -> {
                    if (pending.saveForBiometrics || pending.stayLoggedIn30Days) {
                        secureCredentialsManager.saveCredentials(pending.username, pending.password, pending.servicePin)
                        savedCredentialsAvailable.value = true
                    }
                    pendingLoginCredentials = null
                    _loginUiState.value = LoginUiState(success = true)
                }
                is Result.Error -> {
                    _loginUiState.value = LoginUiState(
                        error = result.message,
                        otpChallenge = repository.getOtpChallengeUi()
                    )
                }
                else -> {}
            }
        }
    }

    fun resendOtp() {
        viewModelScope.launch {
            val challenge = _loginUiState.value.otpChallenge
            _loginUiState.value = _loginUiState.value.copy(isLoading = true)
            when (val result = repository.resendOtp()) {
                is Result.Success -> {
                    _loginUiState.value = LoginUiState(
                        otpChallenge = repository.getOtpChallengeUi(),
                        resendCooldownSeconds = 30
                    )
                    startResendCooldown()
                }
                is Result.Error -> {
                    _loginUiState.value = LoginUiState(
                        error = result.message,
                        otpChallenge = challenge ?: repository.getOtpChallengeUi()
                    )
                }
                else -> {}
            }
        }
    }

    fun selectOtpMethod(method: OtpDeliveryMethod) {
        viewModelScope.launch {
            _loginUiState.value = _loginUiState.value.copy(isLoading = true)
            when (val result = repository.selectOtpDeliveryMethod(method)) {
                is Result.Success -> {
                    _loginUiState.value = LoginUiState(
                        otpChallenge = repository.getOtpChallengeUi(),
                        resendCooldownSeconds = 30
                    )
                    startResendCooldown()
                }
                is Result.Error -> {
                    _loginUiState.value = LoginUiState(
                        error = result.message,
                        otpChallenge = repository.getOtpChallengeUi()
                    )
                }
                else -> {}
            }
        }
    }

    private fun startResendCooldown() {
        viewModelScope.launch {
            for (remaining in 30 downTo 0) {
                _loginUiState.value = _loginUiState.value.copy(resendCooldownSeconds = remaining)
                if (remaining == 0) break
                kotlinx.coroutines.delay(1_000)
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

    fun enterDemoMode() {
        viewModelScope.launch {
            _loginUiState.value = LoginUiState(isLoading = true)
            when (val result = repository.enterDemoMode()) {
                is Result.Success -> {
                    pendingLoginCredentials = null
                    _loginUiState.value = LoginUiState(success = true)
                }
                is Result.Error -> {
                    _loginUiState.value = LoginUiState(error = result.message)
                }
            }
        }
    }
}
