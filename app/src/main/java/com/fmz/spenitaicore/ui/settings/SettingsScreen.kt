package com.fmz.spenitaicore.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fmz.spenitaicore.ui.components.CompactTopAppBar
import com.fmz.spenitaicore.viewmodel.AuthViewModel
import com.fmz.spenitaicore.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    onSignOut: () -> Unit
) {
    val selectedCurrency by viewModel.selectedCurrencyCode.collectAsStateWithLifecycle()
    val salaryPayDay by viewModel.salaryPayDay.collectAsStateWithLifecycle()
    val isAppLockEnabled by viewModel.isAppLockEnabled.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    var showCurrencyDropdown by remember { mutableStateOf(false) }
    var showPayDayDropdown by remember { mutableStateOf(false) }

    // Navigate to login on sign-out
    LaunchedEffect(authState.isLoggedIn) {
        if (!authState.isLoggedIn) {
            onSignOut()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Account section
            item {
                Text("Account", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }

            if (authState.isLoggedIn) {
                // Signed-in profile card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (authState.userPhotoUrl != null) {
                                AsyncImage(
                                    model = authState.userPhotoUrl,
                                    contentDescription = "Profile photo",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = authState.userName.ifEmpty { "User" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (authState.userEmail.isNotEmpty()) {
                                    Text(
                                        text = authState.userEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Sign out
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsItem(
                        title = "Sign Out",
                        subtitle = "Disconnect your Google account",
                        icon = Icons.Outlined.Logout,
                        onClick = { authViewModel.signOut() }
                    )
                }
            } else {
                // Sign in prompt
                item {
                    SettingsItem(
                        title = "Sign in with Google",
                        subtitle = "Back up your data and sync across devices",
                        icon = Icons.Outlined.AccountCircle,
                        onClick = {
                            authViewModel.signInWithGoogle()
                        }
                    )
                }
            }

            // Preferences section
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Preferences", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }

            // Currency
            item {
                Box {
                    SettingsItem(
                        title = "Currency",
                        subtitle = selectedCurrency,
                        icon = Icons.Outlined.AttachMoney,
                        onClick = { showCurrencyDropdown = true }
                    )
                    DropdownMenu(expanded = showCurrencyDropdown,
                        onDismissRequest = { showCurrencyDropdown = false }) {
                        viewModel.availableCurrencies.forEach { code ->
                            DropdownMenuItem(
                                text = { Text(code) },
                                onClick = {
                                    viewModel.setCurrency(code)
                                    showCurrencyDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Pay Day
            item {
                Box {
                    SettingsItem(
                        title = "Pay Day",
                        subtitle = "Day $salaryPayDay of month",
                        icon = Icons.Outlined.CalendarMonth,
                        onClick = { showPayDayDropdown = true }
                    )
                    DropdownMenu(expanded = showPayDayDropdown,
                        onDismissRequest = { showPayDayDropdown = false }) {
                        viewModel.availablePayDays.forEach { day ->
                            DropdownMenuItem(
                                text = { Text("Day $day") },
                                onClick = {
                                    viewModel.setPayDay(day)
                                    showPayDayDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Security section
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Security", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                SwitchSettingsItem(
                    title = "App Lock",
                    subtitle = "Require biometrics to open app",
                    checked = isAppLockEnabled,
                    onCheckedChange = { viewModel.setAppLockEnabled(it) },
                    icon = Icons.Outlined.Lock
                )
            }

            // About section
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text("About", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("SpenIt", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Track your expenses and income with on-device AI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SwitchSettingsItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
