package com.bluedeck.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bluedeck.data.api.Region
import com.bluedeck.data.auth.OtpDeliveryMethod
import com.bluedeck.ui.theme.*
import com.bluedeck.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val uiState by authViewModel.loginUiState.collectAsStateWithLifecycle()
    val biometricLoginAvailable by authViewModel.biometricLoginAvailable.collectAsStateWithLifecycle()
    val selectedRegionName by authViewModel.region.collectAsStateWithLifecycle()
    val otpPending by authViewModel.otpPending.collectAsStateWithLifecycle()
    val otpPendingUsername by authViewModel.otpPendingUsername.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val scrollState = rememberScrollState()
    val bringOtpIntoView = remember { BringIntoViewRequester() }
    val otpFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var servicePin by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var rememberDevice by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var saveForBiometrics by remember { mutableStateOf(true) }
    var stayLoggedIn30Days by remember { mutableStateOf(true) }
    var biometricPromptLaunched by remember { mutableStateOf(false) }
    var regionMenuExpanded by remember { mutableStateOf(false) }
    val selectedRegion = remember(selectedRegionName) {
        runCatching { Region.valueOf(selectedRegionName) }.getOrDefault(Region.US_HYUNDAI)
    }
    val otpRequired = uiState.otpChallenge != null

    LaunchedEffect(otpPending) {
        if (otpPending) authViewModel.resumePendingOtp()
    }

    LaunchedEffect(otpPendingUsername) {
        if (!otpPendingUsername.isNullOrBlank()) {
            username = otpPendingUsername.orEmpty()
        }
    }

    LaunchedEffect(uiState.otpChallenge) {
        uiState.otpChallenge?.rememberDeviceDefault?.let { rememberDevice = it }
        if (uiState.otpChallenge != null) {
            delay(150)
            bringOtpIntoView.bringIntoView()
        }
    }

    fun submitLogin() {
        if (otpRequired) {
            authViewModel.submitOtp(otpCode, rememberDevice)
        } else {
            authViewModel.login(username, password, servicePin, saveForBiometrics, stayLoggedIn30Days)
        }
    }

    fun launchBiometricLogin() {
        val fragmentActivity = activity
        if (fragmentActivity == null) {
            authViewModel.clearError()
            return
        }
        launchBlueDeckBiometricPrompt(
            activity = fragmentActivity,
            title = "Sign in to BlueDeck",
            subtitle = "Use fingerprint or face unlock to unlock your saved Hyundai login",
            onAuthenticated = { authViewModel.loginWithSavedCredentials() },
            onError = { message ->
                android.util.Log.d("BlueDeck", "Biometric login error: $message")
            }
        )
    }

    LaunchedEffect(uiState.success) {
        if (uiState.success) onLoginSuccess()
    }

    LaunchedEffect(biometricLoginAvailable) {
        if (biometricLoginAvailable && !biometricPromptLaunched) {
            biometricPromptLaunched = true
            launchBiometricLogin()
        }
    }

    val canSubmit = !uiState.isLoading &&
        username.isNotBlank() &&
        password.isNotBlank() &&
        (!otpRequired || otpCode.isNotBlank())

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                ) {
                    Button(
                        onClick = { submitLogin() },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                if (otpRequired) "Verify Code" else "Sign In",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (otpRequired) 56.dp else 72.dp)
            )
            Spacer(Modifier.height(if (otpRequired) 12.dp else 16.dp))
            Text(
                text = "BlueDeck",
                fontSize = if (otpRequired) 30.sp else 36.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!otpRequired) {
                Text(
                    text = "Hyundai & Kia Remote Access",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(if (otpRequired) 20.dp else 32.dp))

            ExposedDropdownMenuBox(
                expanded = regionMenuExpanded,
                onExpandedChange = { regionMenuExpanded = !regionMenuExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedRegion.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Region & Brand") },
                    leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionMenuExpanded) },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                    colors = loginFieldColors()
                )

                ExposedDropdownMenu(
                    expanded = regionMenuExpanded,
                    onDismissRequest = { regionMenuExpanded = false }
                ) {
                    Region.entries.filter { it != Region.AU }.forEach { region ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = region.label,
                                    maxLines = 2,
                                    overflow = TextOverflow.Visible,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                authViewModel.setRegion(region)
                                otpCode = ""
                                regionMenuExpanded = false
                            },
                            modifier = Modifier.heightIn(min = 64.dp),
                            leadingIcon = {
                                if (region == selectedRegion) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }

            if (!otpRequired) {
                Text(
                    text = "Choose this before signing in. Different regions use different Hyundai/Kia services.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; authViewModel.clearError() },
                label = { Text("Bluelink / UVO Email") },
                leadingIcon = { Icon(Icons.Filled.Email, null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = loginFieldColors()
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; authViewModel.clearError() },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Filled.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (otpRequired) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (otpRequired) {
                            focusManager.moveFocus(FocusDirection.Down)
                        } else {
                            focusManager.clearFocus()
                            submitLogin()
                        }
                    }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = loginFieldColors()
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = servicePin,
                onValueChange = { value ->
                    servicePin = value.filter { it.isDigit() }.take(4)
                    authViewModel.clearError()
                },
                label = { Text("Bluelink PIN") },
                leadingIcon = { Icon(Icons.Filled.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = if (otpRequired) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (otpRequired) {
                            focusManager.moveFocus(FocusDirection.Down)
                        } else {
                            focusManager.clearFocus()
                            submitLogin()
                        }
                    }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = loginFieldColors()
            )

            Text(
                text = "Required for unlock, remote start, horn, and lights.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )

            if (otpRequired) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(16.dp))
                    uiState.otpChallenge?.message?.let { message ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    uiState.otpChallenge?.let { challenge ->
                        if (challenge.availableMethods.size > 1) {
                            Text(
                                text = "Verification method",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                challenge.availableMethods.forEach { method ->
                                    FilterChip(
                                        selected = challenge.selectedMethod == method,
                                        onClick = { authViewModel.selectOtpMethod(method) },
                                        label = { Text(method.label()) },
                                        enabled = !uiState.isLoading
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { value ->
                            otpCode = value.filter { it.isDigit() }.take(8)
                            authViewModel.clearError()
                        },
                        label = { Text("Verification code") },
                        leadingIcon = { Icon(Icons.Filled.VerifiedUser, null) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                submitLogin()
                            }
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bringOtpIntoView)
                            .focusRequester(otpFocusRequester)
                            .onFocusEvent { focusState ->
                                if (focusState.isFocused) {
                                    coroutineScope.launch { bringOtpIntoView.bringIntoView() }
                                }
                            },
                        colors = loginFieldColors()
                    )
                    Text(
                        text = uiState.otpChallenge?.destinationLabel?.let { "Code sent to $it" }
                            ?: "Enter the verification code sent to your email or phone.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                    TextButton(
                        onClick = { authViewModel.resendOtp() },
                        enabled = !uiState.isLoading && uiState.resendCooldownSeconds == 0,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            if (uiState.resendCooldownSeconds > 0) {
                                "Resend in ${uiState.resendCooldownSeconds}s"
                            } else {
                                "Resend code"
                            }
                        )
                    }
                    if (uiState.otpChallenge?.supportsTrustDevice == true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberDevice,
                                onCheckedChange = { rememberDevice = it },
                                colors = loginCheckboxColors()
                            )
                            Text(
                                text = "Trust this device for 90 days",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.error?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = error,
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (!otpRequired) {
                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = stayLoggedIn30Days,
                        onCheckedChange = { stayLoggedIn30Days = it },
                        colors = loginCheckboxColors()
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Stay logged in for 30 days",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Keep the app session open and refresh tokens when needed.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = saveForBiometrics,
                        onCheckedChange = { saveForBiometrics = it },
                        colors = loginCheckboxColors()
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Save login for biometric sign-in",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Stored locally with Android Keystore encryption.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                AnimatedVisibility(visible = biometricLoginAvailable) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { launchBiometricLogin() },
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sign in with Biometrics", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (!otpRequired) {
                Text(
                    text = "Uses your existing Bluelink / Kia Connect credentials.\nThis app is not affiliated with Hyundai or Kia.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime))
        }
    }
}

@Composable
private fun loginCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = MaterialTheme.colorScheme.primary,
    uncheckedColor = MaterialTheme.colorScheme.outline,
    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
    disabledCheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    disabledUncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
)

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    cursorColor = MaterialTheme.colorScheme.primary
)

private fun launchBlueDeckBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onAuthenticated: () -> Unit,
    onError: (String) -> Unit
) {
    val biometricManager = BiometricManager.from(activity)
    val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG)
    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
        val message = when (canAuthenticate) {
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This device does not have biometric hardware."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware is currently unavailable."
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprint or face unlock is enrolled on this device."
            else -> "Biometric login is not available."
        }
        onError(message)
        return
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onAuthenticated()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Fingerprint not recognized. Try again.")
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setNegativeButtonText("Use password")
        .build()

    prompt.authenticate(promptInfo)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.findFragmentActivity(): FragmentActivity? = findActivity() as? FragmentActivity
