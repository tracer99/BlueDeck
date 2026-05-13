package com.bluebridge.android

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bluebridge.android.ui.screens.BiometricUnlockScreen
import com.bluebridge.android.ui.screens.ControlsScreen
import com.bluebridge.android.ui.screens.DashboardScreen
import com.bluebridge.android.ui.screens.DigitalKeyScreen
import com.bluebridge.android.ui.screens.DriverProfilesScreen
import com.bluebridge.android.ui.screens.EVChargingScreen
import com.bluebridge.android.ui.screens.LocationScreen
import com.bluebridge.android.ui.screens.LoginScreen
import com.bluebridge.android.ui.screens.RegionOnboardingScreen
import com.bluebridge.android.ui.screens.RemoteStartScreen
import com.bluebridge.android.ui.screens.SeatClimatePresetsScreen
import com.bluebridge.android.ui.screens.SettingsScreen
import com.bluebridge.android.ui.screens.StatusScreen
import com.bluebridge.android.ui.screens.SurroundViewScreen
import com.bluebridge.android.ui.screens.ValetModeScreen
import com.bluebridge.android.ui.theme.BlueBridgeTheme
import com.bluebridge.android.viewmodel.AuthViewModel
import com.bluebridge.android.viewmodel.SettingsViewModel
import com.bluebridge.android.viewmodel.VehicleViewModel
import dagger.hilt.android.AndroidEntryPoint

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
            val biometricSessionRecoveryAvailable by authViewModel.biometricSessionRecoveryAvailable.collectAsStateWithLifecycle()
            val appTheme by settingsViewModel.appTheme.collectAsStateWithLifecycle()
            val uiColorOverrides by settingsViewModel.uiColorOverrides.collectAsStateWithLifecycle()
            val biometricEnabled by settingsViewModel.biometricEnabled.collectAsStateWithLifecycle()
            val regionSetupCompleted by settingsViewModel.regionSetupCompleted.collectAsStateWithLifecycle()

            var biometricUnlocked by remember { mutableStateOf(false) }
            var biometricReauthInProgress by remember { mutableStateOf(false) }
            val navController = rememberNavController()

            splashScreen.setKeepOnScreenCondition { isLoggedIn == null }

            LaunchedEffect(
                isLoggedIn,
                biometricEnabled,
                biometricUnlocked,
                biometricSessionRecoveryAvailable,
                biometricReauthInProgress,
                loginUiState.isLoading,
                regionSetupCompleted
            ) {
                when (isLoggedIn) {
                    true -> {
                        biometricReauthInProgress = false
                        if (biometricEnabled && !biometricUnlocked) {
                            navController.navigate("biometric_unlock") {
                                popUpTo(0) { inclusive = true }
                            }
                        } else {
                            navController.navigate("dashboard") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }

                    false -> {
                        if (biometricSessionRecoveryAvailable || biometricReauthInProgress) {
                            navController.navigate("biometric_unlock") {
                                popUpTo(0) { inclusive = true }
                            }
                        } else if (!regionSetupCompleted) {
                            navController.navigate("region_onboarding") {
                                popUpTo(0) { inclusive = true }
                            }
                        } else {
                            biometricUnlocked = false
                            biometricReauthInProgress = false
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }

                    null -> Unit
                }
            }

            BlueBridgeTheme(appThemeId = appTheme, uiColorOverrides = uiColorOverrides) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isLoggedIn != null) {
                        NavHost(
                            navController = navController,
                            startDestination = when {
                                isLoggedIn == true && biometricEnabled && !biometricUnlocked -> "biometric_unlock"
                                isLoggedIn == true -> "dashboard"
                                biometricSessionRecoveryAvailable -> "biometric_unlock"
                                !regionSetupCompleted -> "region_onboarding"
                                else -> "login"
                            }
                        ) {
                            composable("region_onboarding") {
                                RegionOnboardingScreen()
                            }

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
                                        if (isLoggedIn == true) {
                                            biometricUnlocked = true
                                        } else {
                                            biometricUnlocked = true
                                            biometricReauthInProgress = true
                                            authViewModel.loginWithSavedCredentials()
                                        }
                                    },
                                    onUsePassword = {
                                        biometricUnlocked = false
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
                                    onLogout = { authViewModel.logout() }
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
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("digital_key") {
                                DigitalKeyScreen(
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
