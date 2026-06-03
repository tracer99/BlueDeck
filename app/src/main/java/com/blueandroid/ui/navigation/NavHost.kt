package com.blueandroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blueandroid.ui.screens.*
import com.blueandroid.viewmodel.AuthViewModel
import com.blueandroid.viewmodel.VehicleViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object Controls : Screen("controls")
    object Status : Screen("status")
    object Settings : Screen("settings")
    object RemoteStart : Screen("remote_start")
    object EVCharging : Screen("ev_charging")
    object Location : Screen("location")
    object ValetMode : Screen("valet_mode")
    object DriverProfiles : Screen("driver_profiles")
    object SurroundView : Screen("surround_view")
    object SeatPresets : Screen("seat_presets")
    object DigitalKey : Screen("digital_key")
}

@Composable
fun BlueAndroidNavHost(
    startDestination: String,
    authViewModel: AuthViewModel
) {
    val navController = rememberNavController()
    val vehicleViewModel: VehicleViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                authViewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                vehicleViewModel = vehicleViewModel,
                onNavigateToControls = { navController.navigate(Screen.Controls.route) },
                onNavigateToStatus = { navController.navigate(Screen.Status.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToRemoteStart = { navController.navigate(Screen.RemoteStart.route) },
                onNavigateToEVCharging = { navController.navigate(Screen.EVCharging.route) },
                onNavigateToLocation = { navController.navigate(Screen.Location.route) },
                onNavigateToValetMode = { navController.navigate(Screen.ValetMode.route) },
                onNavigateToDriverProfiles = { navController.navigate(Screen.DriverProfiles.route) },
                onNavigateToSurroundView = { navController.navigate(Screen.SurroundView.route) },
                onNavigateToSeatPresets = { navController.navigate(Screen.SeatPresets.route) },
                onNavigateToDigitalKey = { navController.navigate(Screen.DigitalKey.route) },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Controls.route) {
            ControlsScreen(
                vehicleViewModel = vehicleViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Status.route) {
            StatusScreen(
                vehicleViewModel = vehicleViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.RemoteStart.route) {
            RemoteStartScreen(
                vehicleViewModel = vehicleViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.EVCharging.route) {
            EVChargingScreen(
                vehicleViewModel = vehicleViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Location.route) {
            LocationScreen(
                vehicleViewModel = vehicleViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ValetMode.route) {
            ValetModeScreen(
                vehicleViewModel = vehicleViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.DriverProfiles.route) {
            DriverProfilesScreen(
                vehicleViewModel = vehicleViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SurroundView.route) {
            SurroundViewScreen(
                vehicleViewModel = vehicleViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SeatPresets.route) {
            SeatClimatePresetsScreen(
                vehicleViewModel = vehicleViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.DigitalKey.route) {
            DigitalKeyScreen(
                vehicleViewModel = vehicleViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

}
}
