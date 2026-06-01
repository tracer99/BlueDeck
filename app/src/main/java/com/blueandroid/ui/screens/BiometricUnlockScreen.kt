package com.blueandroid.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun BiometricUnlockScreen(
    isReauthenticating: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onUnlocked: () -> Unit,
    onUsePassword: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    var message by remember { mutableStateOf<String?>(null) }
    var promptLaunched by remember { mutableStateOf(false) }
    var promptInProgress by remember { mutableStateOf(false) }

    fun launchBiometricPrompt() {
        if (isLoading || promptInProgress) return
        val fragmentActivity = activity
        if (fragmentActivity == null) {
            message = "Biometric unlock is unavailable in this activity."
            return
        }

        val biometricManager = BiometricManager.from(fragmentActivity)
        when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                message = "This device does not have biometric hardware."
                return
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                message = "Biometric hardware is currently unavailable."
                return
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                message = "No fingerprint or face unlock is enrolled on this device."
                return
            }
            else -> {
                message = "Biometric unlock is not available."
                return
            }
        }

        val executor = ContextCompat.getMainExecutor(fragmentActivity)
        val prompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    promptInProgress = false
                    message = null
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    promptInProgress = false
                    message = errString.toString()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    message = "Fingerprint not recognized. Try again."
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (isReauthenticating) "Sign back in" else "Unlock BlueAndroid")
            .setSubtitle("Use fingerprint or face unlock")
            .setDescription(
                if (isReauthenticating) {
                    "Your Hyundai session expired. BlueAndroid will sign back in with your saved encrypted credentials."
                } else {
                    "Biometric lock is enabled for this app."
                }
            )
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText("Use password")
            .build()

        promptInProgress = true
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(isLoading) {
        if (!isLoading && !promptLaunched) {
            promptLaunched = true
            launchBiometricPrompt()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        Color(0xFF001D36),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(88.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (isReauthenticating) "Sign back in" else "Unlock BlueAndroid",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isReauthenticating) {
                    "Use your fingerprint or face unlock to refresh your Hyundai session."
                } else {
                    "Use your fingerprint or face unlock to continue."
                },
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            val visibleMessage = errorMessage ?: message
            visibleMessage?.let {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { launchBiometricPrompt() },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Signing in…", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isReauthenticating) "Sign in with Biometrics" else "Unlock with Biometrics",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = onUsePassword, enabled = !isLoading) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(Modifier.width(8.dp))
                Text("Use password instead", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.findFragmentActivity(): FragmentActivity? = findActivity() as? FragmentActivity
