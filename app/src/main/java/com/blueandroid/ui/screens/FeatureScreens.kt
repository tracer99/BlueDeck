package com.blueandroid.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blueandroid.data.models.DriverProfile
import com.blueandroid.data.models.VehicleLocation
import com.blueandroid.ui.components.CommandStatusBanner
import com.blueandroid.ui.components.ControlSection
import com.blueandroid.ui.components.ToggleControlRow
import com.blueandroid.ui.theme.*
import com.blueandroid.viewmodel.CommandStatus
import com.blueandroid.viewmodel.VehicleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    vehicleViewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    val status by vehicleViewModel.vehicleStatus.collectAsStateWithLifecycle()
    val isLoading by vehicleViewModel.isStatusLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val location = status?.location
    val coord = location?.coord

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle Location", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vehicleViewModel.refreshLocation() }) {
                        if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Filled.Refresh, "Refresh", tint = MaterialTheme.colorScheme.primary)
                    }
                },
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
            if (coord == null || (coord.lat == 0.0 && coord.lon == 0.0)) {
                EmptyFeatureCard(
                    icon = Icons.Filled.LocationOff,
                    title = "No GPS location returned",
                    message = "Refresh location from the vehicle. This calls the Bluelinky-mapped findMyCar endpoint: /ac/v2/rcs/rfc/findMyCar. Some vehicles or accounts may still omit GPS data."
                )
            } else {
                LocationMapCard(location)
                ControlSection(title = "Coordinates") {
                    StatusRow(Icons.Filled.Place, "Latitude", coord.lat.toString(), MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    StatusRow(Icons.Filled.Place, "Longitude", coord.lon.toString(), MaterialTheme.colorScheme.onSurface)
                    if (coord.alt != 0.0) {
                        Spacer(Modifier.height(8.dp))
                        StatusRow(Icons.Filled.Terrain, "Altitude", "${coord.alt} m", MaterialTheme.colorScheme.onSurface)
                    }
                    if (location.heading != 0) {
                        Spacer(Modifier.height(8.dp))
                        StatusRow(Icons.Filled.Explore, "Heading", "${location.heading}°", MaterialTheme.colorScheme.onSurface)
                    }
                    location.speed?.let {
                        if (it.value > 0) {
                            Spacer(Modifier.height(8.dp))
                            StatusRow(Icons.Filled.Speed, "Speed", it.value.toString(), MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Button(
                    onClick = {
                        val uri = Uri.parse("geo:${coord.lat},${coord.lon}?q=${coord.lat},${coord.lon}(Vehicle)")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.Map, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open in Maps")
                }
            }
        }
    }
}

@Composable
private fun LocationMapCard(location: VehicleLocation) {
    val coord = location.coord ?: return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFF10182A)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.MyLocation, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("${coord.lat}, ${coord.lon}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Tap Open in Maps for live map rendering", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValetModeScreen(
    vehicleViewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    val enabled by vehicleViewModel.valetModeEnabled.collectAsStateWithLifecycle()
    val commandState by vehicleViewModel.commandState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Valet Mode", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ControlSection(title = "Valet Mode") {
                    ToggleControlRow(
                        label = if (enabled) "Local valet mode is on" else "Local valet mode is off",
                        icon = Icons.Filled.AdminPanelSettings,
                        checked = enabled,
                        onChecked = { vehicleViewModel.setValetMode(it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This stores the valet preference locally. The project still needs the verified Hyundai valet-mode command endpoint before this can change the vehicle-side setting.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                ControlSection(title = "What to wire next") {
                    FeatureBullet("Add the captured official-app endpoint, headers, and payload to BluelinkApiService.")
                    FeatureBullet("Call that endpoint from VehicleRepository.setValetMode().")
                    FeatureBullet("Replace the local-only warning with a success/failure result from Hyundai.")
                }
            }
            AnimatedVisibility(
                visible = commandState.status != CommandStatus.IDLE,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) { CommandStatusBanner(commandState = commandState) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverProfilesScreen(
    vehicleViewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    val profiles by vehicleViewModel.driverProfiles.collectAsStateWithLifecycle()
    val activeProfile = profiles.firstOrNull { it.isActive } ?: profiles.firstOrNull()
    val commandState by vehicleViewModel.commandState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var photoTargetProfileId by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetId = photoTargetProfileId
        if (uri != null && targetId != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers do not offer persistable permissions. The URI is still saved.
            }
            vehicleViewModel.updateDriverProfilePhoto(targetId, uri.toString())
        }
        photoTargetProfileId = null
    }

    fun choosePhoto(profileId: String) {
        photoTargetProfileId = profileId
        photoPicker.launch(arrayOf("image/*"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Driver Profiles", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                activeProfile?.let { profile ->
                    CurrentProfileCard(
                        profile = profile,
                        onChangePhoto = { choosePhoto(profile.id) },
                        onRemovePhoto = { vehicleViewModel.updateDriverProfilePhoto(profile.id, null) }
                    )
                }

                Text(
                    "Profiles",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                profiles.forEach { profile ->
                    DriverProfileCard(
                        profile = profile,
                        onSelect = { vehicleViewModel.setActiveDriverProfile(profile.id) },
                        onChangePhoto = { choosePhoto(profile.id) },
                        onRemovePhoto = { vehicleViewModel.updateDriverProfilePhoto(profile.id, null) }
                    )
                }
            }

            AnimatedVisibility(
                visible = commandState.status != CommandStatus.IDLE,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) { CommandStatusBanner(commandState = commandState) }
        }
    }
}

@Composable
private fun CurrentProfileCard(
    profile: DriverProfile,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileAvatar(profile = profile, size = 104.dp)
            Text(profile.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("Current user • ${profile.role}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onChangePhoto) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Change Photo")
                }
                if (!profile.photoUri.isNullOrBlank()) {
                    OutlinedButton(onClick = onRemovePhoto) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun DriverProfileCard(
    profile: DriverProfile,
    onSelect: () -> Unit,
    onChangePhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(profile = profile, size = 58.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(profile.role, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onChangePhoto, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text("Change photo")
                    }
                    if (!profile.photoUri.isNullOrBlank()) {
                        TextButton(onClick = onRemovePhoto, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text("Remove")
                        }
                    }
                }
            }
            if (profile.isActive) {
                AssistChip(onClick = {}, label = { Text("Active") }, leadingIcon = { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) })
            } else {
                TextButton(onClick = onSelect) { Text("Use") }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(profile: DriverProfile, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (profile.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (profile.photoUri.isNullOrBlank()) {
            Text(
                profile.initials,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = if (size > 80.dp) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleMedium
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    imageView.setImageURI(Uri.parse(profile.photoUri))
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurroundViewScreen(
    vehicleViewModel: VehicleViewModel,
    onNavigateBack: () -> Unit
) {
    val snapshot by vehicleViewModel.surroundViewSnapshot.collectAsStateWithLifecycle()
    val commandState by vehicleViewModel.commandState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Surround View", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { vehicleViewModel.requestSurroundView() }) { Icon(Icons.Filled.Refresh, "Refresh", tint = MaterialTheme.colorScheme.primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SurroundViewGrid(snapshot?.hasAnyImage == true)
                Button(
                    onClick = { vehicleViewModel.requestSurroundView() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Request Snapshot")
                }
                ControlSection(title = "Endpoint status") {
                    Text(
                        "The UI and state plumbing are in place. The actual remote camera retrieval still needs the Hyundai Surround View endpoint and image URL/token response mapped into VehicleRepository.getSurroundViewSnapshot().",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            AnimatedVisibility(
                visible = commandState.status != CommandStatus.IDLE,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) { CommandStatusBanner(commandState = commandState) }
        }
    }
}

@Composable
private fun SurroundViewGrid(hasImage: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Camera Views", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        listOf("Front", "Rear", "Left", "Right").chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(135.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Videocam, null, tint = if (hasImage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFeatureCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FeatureBullet(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(8.dp))
}
