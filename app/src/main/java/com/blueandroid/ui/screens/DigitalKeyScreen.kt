package com.blueandroid.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blueandroid.data.models.AdditionalVehicleDetails
import com.blueandroid.ui.theme.SuccessGreen
import com.blueandroid.ui.theme.WarningAmber
import com.blueandroid.viewmodel.VehicleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalKeyScreen(
    vehicleViewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val selectedVehicle by vehicleViewModel.selectedVehicle.collectAsStateWithLifecycle()
    val details = selectedVehicle?.additionalDetails
    val deviceCapabilities = rememberDigitalKeyDeviceCapabilities(context)
    val appInstallState = rememberDigitalKeyAppInstallState(context)
    val setupText = buildDigitalKeySetupText(
        vehicleName = selectedVehicle?.displayName ?: "Selected vehicle",
        details = details,
        capabilities = deviceCapabilities,
        appInstallState = appInstallState
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Digital Key", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DigitalKeyHeroCard(details = details)

            DigitalKeyStatusCard(
                details = details,
                vehicleName = selectedVehicle?.displayName ?: "Selected vehicle"
            )

            DeviceCapabilityCard(capabilities = deviceCapabilities)

            InstalledAppsCard(appInstallState = appInstallState)

            DigitalKeySetupCard(
                details = details,
                onOpenMyHyundai = { context.openPackageOrStore(MY_HYUNDAI_PACKAGE) },
                onOpenHyundaiDigitalKey = { context.openPackageOrStore(HYUNDAI_DIGITAL_KEY_PACKAGE) },
                onOpenGoogleWallet = { context.openPackageOrStore(GOOGLE_WALLET_PACKAGE) },
                onOpenGoogleWalletHelp = { context.openUrl(GOOGLE_WALLET_CAR_KEY_HELP_URL) },
                onOpenNfcSettings = { context.openNfcSettings() },
                onOpenBluetoothSettings = { context.openBluetoothSettings() },
                onCopySetupNotes = { context.copyToClipboard("Digital Key setup notes", setupText) }
            )

            SecurityBoundaryCard()
        }
    }
}

@Composable
private fun DigitalKeyHeroCard(details: AdditionalVehicleDetails?) {
    val capable = details.digitalKeyCapableFlag()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                modifier = Modifier.size(54.dp)
            ) {
                Icon(
                    Icons.Filled.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(14.dp)
                )
            }
            Text(
                text = if (capable) "This vehicle reports Digital Key support" else "Digital Key setup assistant",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp
            )
            Text(
                text = "BlueAndroid can now act as a Digital Key launchpad: it checks phone readiness, shows the vehicle-reported enrollment flags, opens the official Hyundai/Wallet apps, and gives you a copyable setup checklist.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun DigitalKeyStatusCard(
    details: AdditionalVehicleDetails?,
    vehicleName: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(vehicleName, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            StatusLine("Digital Key capable", details.digitalKeyCapableLabel(), details.digitalKeyCapableFlag())
            StatusLine("Digital Key enrolled", details.digitalKeyEnrolledLabel(), details.digitalKeyEnrolledFlag())
            StatusLine("Digital Key type", details?.digitalKeyType?.takeIf { it.isNotBlank() } ?: "Unknown", null)
        }
    }
}

@Composable
private fun DeviceCapabilityCard(capabilities: DigitalKeyDeviceCapabilities) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Phone readiness", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            CapabilityLine(Icons.Filled.Nfc, "NFC", capabilities.hasNfc, "Required for tap-to-unlock and in-car pairing on many Hyundai Digital Key flows")
            CapabilityLine(Icons.Filled.Bluetooth, "Bluetooth LE", capabilities.hasBle, "Used by Hyundai Digital Key 1 and some proximity/pairing flows")
            CapabilityLine(Icons.Filled.Smartphone, "UWB", capabilities.hasUwb, "Required for passive Digital Key 2 Premium proximity unlock on supported phones")
            CapabilityLine(Icons.Filled.Security, "Screen lock", capabilities.hasSecureLockScreen, "Wallet car keys require a secure device lock on most Android phones")
        }
    }
}

@Composable
private fun InstalledAppsCard(appInstallState: DigitalKeyAppInstallState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Official app availability", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            StatusLine("MyHyundai with Bluelink", if (appInstallState.myHyundaiInstalled) "Installed" else "Missing", appInstallState.myHyundaiInstalled)
            StatusLine("Hyundai Digital Key", if (appInstallState.hyundaiDigitalKeyInstalled) "Installed" else "Missing", appInstallState.hyundaiDigitalKeyInstalled)
            StatusLine("Google Wallet", if (appInstallState.googleWalletInstalled) "Installed" else "Missing", appInstallState.googleWalletInstalled)
            Text(
                text = "For newer Hyundai Digital Key 2 vehicles, start in MyHyundai or the vehicle head unit. For older Digital Key 1 vehicles, the standalone Hyundai Digital Key app may be required.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                fontSize = 13.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun DigitalKeySetupCard(
    details: AdditionalVehicleDetails?,
    onOpenMyHyundai: () -> Unit,
    onOpenHyundaiDigitalKey: () -> Unit,
    onOpenGoogleWallet: () -> Unit,
    onOpenGoogleWalletHelp: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onCopySetupNotes: () -> Unit
) {
    val digitalKeyType = details?.digitalKeyType?.trim().orEmpty()
    val setupTitle = if (digitalKeyType.isNotBlank()) "Set up $digitalKeyType" else "Set up your phone as a key"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(setupTitle, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            SetupStep("1", "Make sure the physical key fob is inside the vehicle and the vehicle is on or in accessory mode.")
            SetupStep("2", "On the vehicle screen, open Setup → Vehicle → Digital Key → Smartphone Key, then choose Save, Pair, or Start Pairing.")
            SetupStep("3", "Start the matching Hyundai flow from MyHyundai, Hyundai Digital Key, or the setup prompt shown on the vehicle screen.")
            SetupStep("4", "Place the phone on the wireless charging pad/NFC reader when prompted, then finish the Wallet or Hyundai confirmation flow.")

            Spacer(Modifier.height(2.dp))

            Button(onClick = onOpenMyHyundai, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Key, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open MyHyundai")
                Spacer(Modifier.size(8.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
            }
            OutlinedButton(onClick = onOpenHyundaiDigitalKey, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Key, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open Hyundai Digital Key")
            }
            OutlinedButton(onClick = onOpenGoogleWallet, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CreditCard, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open Google Wallet")
            }
            OutlinedButton(onClick = onOpenGoogleWalletHelp, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CreditCard, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Google Wallet car key help")
            }
            OutlinedButton(onClick = onOpenNfcSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Nfc, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open NFC settings")
            }
            OutlinedButton(onClick = onOpenBluetoothSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Bluetooth, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open Bluetooth settings")
            }
            OutlinedButton(onClick = onCopySetupNotes, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Settings, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Copy setup checklist")
            }
        }
    }
}

@Composable
private fun SecurityBoundaryCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.14f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Filled.Security, null, tint = WarningAmber)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Security note", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(
                    "BlueAndroid cannot provision, emulate, clone, intercept, export, or store a vehicle key. Hyundai/Wallet owns the actual cryptographic key enrollment and secure-element storage.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, positive: Boolean?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (positive != null) {
                Icon(
                    imageVector = if (positive) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (positive) SuccessGreen else WarningAmber,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CapabilityLine(icon: ImageVector, title: String, enabled: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, null, tint = if (enabled) SuccessGreen else WarningAmber, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f), fontSize = 13.sp, lineHeight = 17.sp)
        }
        Text(if (enabled) "Ready" else "Missing", color = if (enabled) SuccessGreen else WarningAmber, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SetupStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(
            text = number,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        )
        Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f), lineHeight = 19.sp)
    }
}

private data class DigitalKeyDeviceCapabilities(
    val hasNfc: Boolean,
    val hasBle: Boolean,
    val hasUwb: Boolean,
    val hasSecureLockScreen: Boolean
)

private data class DigitalKeyAppInstallState(
    val myHyundaiInstalled: Boolean,
    val hyundaiDigitalKeyInstalled: Boolean,
    val googleWalletInstalled: Boolean
)

@Composable
private fun rememberDigitalKeyDeviceCapabilities(context: Context): DigitalKeyDeviceCapabilities {
    val pm = context.packageManager
    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
    return DigitalKeyDeviceCapabilities(
        hasNfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC),
        hasBle = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
        hasUwb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && pm.hasSystemFeature(PackageManager.FEATURE_UWB),
        hasSecureLockScreen = keyguardManager.isDeviceSecure
    )
}

@Composable
private fun rememberDigitalKeyAppInstallState(context: Context): DigitalKeyAppInstallState {
    return DigitalKeyAppInstallState(
        myHyundaiInstalled = context.canLaunchPackage(MY_HYUNDAI_PACKAGE),
        hyundaiDigitalKeyInstalled = context.canLaunchPackage(HYUNDAI_DIGITAL_KEY_PACKAGE),
        googleWalletInstalled = context.canLaunchPackage(GOOGLE_WALLET_PACKAGE)
    )
}

private fun buildDigitalKeySetupText(
    vehicleName: String,
    details: AdditionalVehicleDetails?,
    capabilities: DigitalKeyDeviceCapabilities,
    appInstallState: DigitalKeyAppInstallState
): String = buildString {
    appendLine("Digital Key setup notes for $vehicleName")
    appendLine()
    appendLine("Vehicle-reported Digital Key capable: ${details.digitalKeyCapableLabel()}")
    appendLine("Vehicle-reported Digital Key enrolled: ${details.digitalKeyEnrolledLabel()}")
    appendLine("Vehicle-reported Digital Key type: ${details?.digitalKeyType?.takeIf { it.isNotBlank() } ?: "Unknown"}")
    appendLine()
    appendLine("Phone readiness:")
    appendLine("- NFC: ${if (capabilities.hasNfc) "Ready" else "Missing"}")
    appendLine("- Bluetooth LE: ${if (capabilities.hasBle) "Ready" else "Missing"}")
    appendLine("- UWB: ${if (capabilities.hasUwb) "Ready" else "Missing"}")
    appendLine("- Secure lock screen: ${if (capabilities.hasSecureLockScreen) "Ready" else "Missing"}")
    appendLine()
    appendLine("Installed official apps:")
    appendLine("- MyHyundai with Bluelink: ${if (appInstallState.myHyundaiInstalled) "Installed" else "Missing"}")
    appendLine("- Hyundai Digital Key: ${if (appInstallState.hyundaiDigitalKeyInstalled) "Installed" else "Missing"}")
    appendLine("- Google Wallet: ${if (appInstallState.googleWalletInstalled) "Installed" else "Missing"}")
    appendLine()
    appendLine("Setup path:")
    appendLine("1. Put the physical key fob inside the vehicle and turn the vehicle on or into accessory mode.")
    appendLine("2. On the vehicle screen, open Setup > Vehicle > Digital Key > Smartphone Key.")
    appendLine("3. Choose Save, Pair, or Start Pairing.")
    appendLine("4. Start the matching setup flow in MyHyundai, Hyundai Digital Key, or Google Wallet.")
    appendLine("5. Place the phone on the wireless charging pad/NFC reader when prompted.")
}

private fun AdditionalVehicleDetails?.digitalKeyCapableFlag(): Boolean = this?.digitalKeyCapable.isTruthyFlag()
private fun AdditionalVehicleDetails?.digitalKeyEnrolledFlag(): Boolean = this?.digitalKeyEnrolled.isTruthyFlag()
private fun AdditionalVehicleDetails?.digitalKeyCapableLabel(): String = this?.digitalKeyCapable.toStatusLabel()
private fun AdditionalVehicleDetails?.digitalKeyEnrolledLabel(): String = this?.digitalKeyEnrolled.toStatusLabel()

private fun String?.isTruthyFlag(): Boolean {
    val normalized = this?.trim()?.lowercase().orEmpty()
    return normalized in setOf("y", "yes", "true", "1", "capable", "enabled", "enrolled")
}

private fun String?.toStatusLabel(): String {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return "Unknown"
    return when (value.lowercase()) {
        "y", "yes", "true", "1" -> "Yes"
        "n", "no", "false", "0" -> "No"
        else -> value
    }
}

private fun Context.canLaunchPackage(packageName: String): Boolean {
    return packageManager.getLaunchIntentForPackage(packageName) != null
}

private fun Context.openPackageOrStore(packageName: String) {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
    if (launchIntent != null) {
        startActivity(launchIntent)
        return
    }
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    } catch (_: ActivityNotFoundException) {
        openUrl("https://play.google.com/store/apps/details?id=$packageName")
    }
}

private fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun Context.openNfcSettings() {
    try {
        startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun Context.openBluetoothSettings() {
    try {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun Context.copyToClipboard(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, "Digital Key setup notes copied", Toast.LENGTH_SHORT).show()
}

private const val MY_HYUNDAI_PACKAGE = "com.stationdm.bluelink"
private const val HYUNDAI_DIGITAL_KEY_PACKAGE = "com.hyundaiusa.hyundai.digitalcarkey"
private const val GOOGLE_WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"
private const val GOOGLE_WALLET_CAR_KEY_HELP_URL = "https://support.google.com/wallet/answer/12060041"
