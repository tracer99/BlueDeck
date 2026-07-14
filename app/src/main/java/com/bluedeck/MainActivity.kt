package com.bluedeck

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bluedeck.ui.screens.BiometricUnlockScreen
import com.bluedeck.ui.screens.ControlsScreen
import com.bluedeck.ui.screens.DashboardScreen
import com.bluedeck.ui.screens.DigitalKeyScreen
import com.bluedeck.ui.screens.DriverProfilesScreen
import com.bluedeck.ui.screens.EVChargingScreen
import com.bluedeck.ui.screens.LocationScreen
import com.bluedeck.ui.screens.ObdDiagnosticsScreen
import com.bluedeck.ui.screens.LoginScreen
import com.bluedeck.ui.screens.RemoteStartScreen
import com.bluedeck.ui.screens.SeatClimatePresetsScreen
import com.bluedeck.ui.screens.SettingsScreen
import com.bluedeck.ui.screens.StatusScreen
import com.bluedeck.ui.screens.SurroundViewScreen
import com.bluedeck.ui.screens.ValetModeScreen
import com.bluedeck.ui.theme.BlueDeckTheme
import com.bluedeck.ui.theme.ThemeMode
import com.bluedeck.viewmodel.AuthViewModel
import com.bluedeck.viewmodel.SettingsViewModel
import com.bluedeck.viewmodel.VehicleViewModel
import dagger.hilt.android.AndroidEntryPoint

private const val BIOMETRIC_REAUTH_GRACE_PERIOD_MS = 5 * 60 * 1000L
private const val BIOMETRIC_DAILY_GRACE_PERIOD_MS = 24 * 60 * 60 * 1000L

private fun biometricPromptWindowMs(mode: String): Long = when (mode) {
    "DAILY" -> BIOMETRIC_DAILY_GRACE_PERIOD_MS
    "APP_OPEN" -> BIOMETRIC_REAUTH_GRACE_PERIOD_MS
    else -> Long.MAX_VALUE
}

private fun biometricPromptsOnAppOpen(mode: String): Boolean = mode == "APP_OPEN" || mode == "DAILY"

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val authViewModel: AuthViewModel = hiltViewModel()
            val vehicleViewModel: VehicleViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()

            val isLoggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle()
            val loginUiState by authViewModel.loginUiState.collectAsStateWithLifecycle()
            val otpPending by authViewModel.otpPending.collectAsStateWithLifecycle()
            val passwordRequired by authViewModel.passwordRequired.collectAsStateWithLifecycle()
            val biometricSessionRecoveryAvailable by authViewModel.biometricSessionRecoveryAvailable.collectAsStateWithLifecycle()
            val themeModeKey by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val useDynamicColor by settingsViewModel.useDynamicColor.collectAsStateWithLifecycle()
            val themeMode = ThemeMode.fromKey(themeModeKey)
            val biometricEnabled by settingsViewModel.biometricEnabled.collectAsStateWithLifecycle()
            val biometricUnlockMode by settingsViewModel.biometricUnlockMode.collectAsStateWithLifecycle()
            val persistedLastBiometricUnlockAt by settingsViewModel.lastBiometricUnlockAt.collectAsStateWithLifecycle()
            val shouldPromptOnAppOpen = biometricEnabled && biometricPromptsOnAppOpen(biometricUnlockMode)
            val biometricWindowMs = biometricPromptWindowMs(biometricUnlockMode)
            val biometricGateReady = !biometricEnabled || persistedLastBiometricUnlockAt >= 0L
            val persistedBiometricSessionActive = shouldPromptOnAppOpen && persistedLastBiometricUnlockAt > 0L &&
                System.currentTimeMillis() - persistedLastBiometricUnlockAt <= biometricWindowMs

            var biometricUnlocked by rememberSaveable { mutableStateOf(false) }
            var biometricReauthInProgress by rememberSaveable { mutableStateOf(false) }
            var lastBiometricUnlockAt by rememberSaveable { mutableStateOf(0L) }
            var backgroundedAt by rememberSaveable { mutableStateOf(0L) }
            val navController = rememberNavController()

            splashScreen.setKeepOnScreenCondition {
                isLoggedIn == null || (isLoggedIn == true && shouldPromptOnAppOpen && !biometricGateReady)
            }

            LaunchedEffect(biometricEnabled, biometricUnlockMode, persistedLastBiometricUnlockAt) {
                if (!shouldPromptOnAppOpen) {
                    biometricUnlocked = false
                    lastBiometricUnlockAt = 0L
                    return@LaunchedEffect
                }

                if (persistedBiometricSessionActive) {
                    biometricUnlocked = true
                    lastBiometricUnlockAt = persistedLastBiometricUnlockAt
                }
            }

            DisposableEffect(isLoggedIn, shouldPromptOnAppOpen, biometricUnlocked, persistedBiometricSessionActive, biometricWindowMs, biometricEnabled) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            backgroundedAt = System.currentTimeMillis()
                        }

                        Lifecycle.Event.ON_START -> {
                            val now = System.currentTimeMillis()
                            val timeAway = if (backgroundedAt > 0L) now - backgroundedAt else 0L
                            val unlockAge = if (lastBiometricUnlockAt > 0L) now - lastBiometricUnlockAt else 0L

                            if (
                                biometricEnabled &&
                                biometricUnlocked &&
                                backgroundedAt > 0L &&
                                shouldPromptOnAppOpen &&
                                timeAway > biometricWindowMs &&
                                unlockAge > biometricWindowMs
                            ) {
                                biometricUnlocked = false
                            }

                            val appUnlockedForRefresh = !shouldPromptOnAppOpen || biometricUnlocked || persistedBiometricSessionActive
                            if (isLoggedIn == true && appUnlockedForRefresh) {
                                vehicleViewModel.refreshSessionSnapshotOnAppOpen()
                            }
                        }

                        else -> Unit
                    }
                }

                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(
                isLoggedIn,
                biometricEnabled,
                biometricUnlockMode,
                biometricUnlocked,
                biometricSessionRecoveryAvailable,
                biometricReauthInProgress,
                loginUiState.isLoading,
                biometricGateReady,
                persistedLastBiometricUnlockAt
            ) {
                fun navigateRoot(route: String) {
                    if (navController.currentBackStackEntry?.destination?.route == route) return
                    navController.navigate(route) {
                        popUpTo(0) { inclusive = true }
                    }
                }

                when (isLoggedIn) {
                    true -> {
                        biometricReauthInProgress = false
                        if (shouldPromptOnAppOpen && !biometricGateReady) {
                            return@LaunchedEffect
                        }

                        if (shouldPromptOnAppOpen && !biometricUnlocked && !persistedBiometricSessionActive) {
                            navigateRoot("biometric_unlock")
                        } else {
                            navigateRoot("dashboard")
                        }
                    }

                    false -> {
                        if ((biometricSessionRecoveryAvailable || biometricReauthInProgress) && !otpPending) {
                            navigateRoot("biometric_unlock")
                        } else {
                            biometricUnlocked = false
                            biometricReauthInProgress = false
                            navigateRoot("login")
                            if (otpPending) {
                                authViewModel.resumePendingOtp()
                            }
                        }
                    }

                    null -> Unit
                }
            }

            BlueDeckTheme(themeMode = themeMode, useDynamicColor = useDynamicColor) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isLoggedIn != null) {
                        NavHost(
                            navController = navController,
                            startDestination = when {
                                otpPending && passwordRequired -> "login"
                                isLoggedIn == true && shouldPromptOnAppOpen && !biometricUnlocked && !persistedBiometricSessionActive -> "biometric_unlock"
                                isLoggedIn == true -> "dashboard"
                                biometricSessionRecoveryAvailable && !otpPending -> "biometric_unlock"
                                else -> "login"
                            }
                        ) {
                            composable("login") {
                                LoginScreen(
                                    authViewModel = authViewModel,
                                    onLoginSuccess = {}
                                )
                            }

                            composable("biometric_unlock") {
                                BackHandler(enabled = true) {}
                                BiometricUnlockScreen(
                                    isReauthenticating = isLoggedIn == false,
                                    isLoading = loginUiState.isLoading,
                                    errorMessage = loginUiState.error,
                                    onUnlocked = {
                                        val now = System.currentTimeMillis()
                                        if (isLoggedIn == true) {
                                            biometricUnlocked = true
                                            lastBiometricUnlockAt = now
                                            settingsViewModel.setLastBiometricUnlockAt(now)
                                        } else {
                                            biometricUnlocked = true
                                            lastBiometricUnlockAt = now
                                            settingsViewModel.setLastBiometricUnlockAt(now)
                                            biometricReauthInProgress = true
                                            authViewModel.loginWithSavedCredentials()
                                        }
                                    },
                                    onUsePassword = {
                                        biometricUnlocked = false
                                        lastBiometricUnlockAt = 0L
                                        settingsViewModel.setLastBiometricUnlockAt(0L)
                                        biometricReauthInProgress = false
                                        authViewModel.requirePasswordLogin()
                                    }
                                )
                            }

                            composable("dashboard") {
                                DashboardScreen(
                                    vehicleViewModel = vehicleViewModel,
                                    onNavigateToControls = { navController.navigate("controls") },
                                    onNavigateToStatus = { navController.navigate("status") },
                                    onNavigateToSettings = { navController.navigate("settings") },
                                    onNavigateToRemoteStart = { navController.navigate("remote_start") },
                                    onNavigateToEVCharging = { navController.navigate("ev_charging") },
                                    onNavigateToLocation = { navController.navigate("location") },
                                    onNavigateToValetMode = { navController.navigate("valet_mode") },
                                    onNavigateToDriverProfiles = { navController.navigate("driver_profiles") },
                                    onNavigateToSurroundView = { navController.navigate("surround_view") },
                                    onNavigateToSeatPresets = { navController.navigate("seat_presets") },
                                    onNavigateToDigitalKey = { navController.navigate("digital_key") },
                                    onNavigateToObd = { navController.navigate("obd_diagnostics") },
                                    onLogout = { authViewModel.logout() }
                                )
                            }

                            composable("controls") {
                                ControlsScreen(
                                    vehicleViewModel = vehicleViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("status") {
                                StatusScreen(
                                    vehicleViewModel = vehicleViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("settings") {
                                SettingsScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onLogout = { authViewModel.logout() },
                                    onNavigateToObd = { navController.navigate("obd_diagnostics") }
                                )
                            }

                            composable("remote_start") {
                                RemoteStartScreen(
                                    vehicleViewModel = vehicleViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("ev_charging") {
                                EVChargingScreen(
                                    vehicleViewModel = vehicleViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("location") {
                                LocationScreen(
                                    vehicleViewModel = vehicleViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("valet_mode") {
                                ValetModeScreen(
                                    vehicleViewModel = vehicleViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("driver_profiles") {
                                DriverProfilesScreen(
                                    vehicleViewModel = vehicleViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("surround_view") {
                                SurroundViewScreen(
                                    vehicleViewModel = vehicleViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("seat_presets") {
                                SeatClimatePresetsScreen(
                                    vehicleViewModel = vehicleViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("digital_key") {
                                DigitalKeyScreen(
                                    vehicleViewModel = vehicleViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("obd_diagnostics") {
                                ObdDiagnosticsScreen(
                                    vehicleViewModel = vehicleViewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
