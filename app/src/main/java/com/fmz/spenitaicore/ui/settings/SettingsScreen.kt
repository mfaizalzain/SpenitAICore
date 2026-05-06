package com.fmz.spenitaicore.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FileDownload
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
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

    // ── Tax Export state ─────────────────────────────────────────
    val availableTaxYears by viewModel.availableTaxYears.collectAsStateWithLifecycle()
    val selectedTaxYear by viewModel.selectedTaxYear.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val exportResult by viewModel.exportResult.collectAsStateWithLifecycle()
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()

    // ── Backup state ────────────────────────────────────────────
    val isBackupEnabled by viewModel.isBackupEnabled.collectAsStateWithLifecycle()
    val isBackingUp by viewModel.isBackingUp.collectAsStateWithLifecycle()
    val backupAccountName by viewModel.backupAccountName.collectAsStateWithLifecycle()
    val lastBackupTime by viewModel.lastBackupTime.collectAsStateWithLifecycle()
    val backupError by viewModel.backupError.collectAsStateWithLifecycle()
    val driveConsentIntent by viewModel.driveConsentIntent.collectAsStateWithLifecycle()

    // ── Restore state ───────────────────────────────────────────
    val availableBackups by viewModel.availableBackups.collectAsStateWithLifecycle()
    val isLoadingBackups by viewModel.isLoadingBackups.collectAsStateWithLifecycle()
    val isRestoring by viewModel.isRestoring.collectAsStateWithLifecycle()
    val restoreSuccess by viewModel.restoreSuccess.collectAsStateWithLifecycle()
    val restoreError by viewModel.restoreError.collectAsStateWithLifecycle()
    val selectedBackupForRestore by viewModel.selectedBackupForRestore.collectAsStateWithLifecycle()

    var showTaxYearDropdown by remember { mutableStateOf(false) }
    var showCurrencyDropdown by remember { mutableStateOf(false) }
    var showPayDayDropdown by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Trigger share when export completes
    LaunchedEffect(exportResult) {
        exportResult?.let { result ->
            try {
                val zipFile = java.io.File(result.zipPath)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Tax Relief Export ${selectedTaxYear}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Tax Export"))
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            viewModel.clearExportResult()
        }
    }

    // Drive consent launcher
    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleDriveConsentResult(
            result.resultCode == android.app.Activity.RESULT_OK
        )
    }

    // Launch Drive consent when requested
    LaunchedEffect(driveConsentIntent) {
        driveConsentIntent?.let { intent ->
            driveConsentLauncher.launch(intent)
        }
    }

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

            // Tax Export section
            if (availableTaxYears.isNotEmpty()) {
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Tax Export", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp))
                }

                // Export error
                if (exportError != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = exportError!!,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // Year selector
                item {
                    Box {
                        SettingsItem(
                            title = "Tax Year",
                            subtitle = selectedTaxYear,
                            icon = Icons.Outlined.CalendarMonth,
                            onClick = { showTaxYearDropdown = true }
                        )
                        DropdownMenu(
                            expanded = showTaxYearDropdown,
                            onDismissRequest = { showTaxYearDropdown = false }
                        ) {
                            availableTaxYears.forEach { year ->
                                DropdownMenuItem(
                                    text = { Text(year) },
                                    onClick = {
                                        viewModel.setTaxYear(year)
                                        showTaxYearDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Export button
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { viewModel.exportTaxRelief() },
                        enabled = !isExporting && selectedTaxYear.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Exporting...")
                        } else {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Export Tax Relief for $selectedTaxYear")
                        }
                    }
                }
            }

            // Backup section
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Backup", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }

            // Backup error
            if (backupError != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = backupError!!,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            if (isBackupEnabled) {
                // Backup status
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Auto backup enabled", fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Nightly backup to Google Drive",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            backupAccountName?.let {
                                Text(
                                    "Account: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (lastBackupTime > 0) {
                                val formatter = remember { java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.US) }
                                Text(
                                    "Last backup: ${formatter.format(java.util.Date(lastBackupTime))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Manual backup button
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { viewModel.runManualBackup() },
                        enabled = !isBackingUp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isBackingUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Backing up...")
                        } else {
                            Icon(Icons.Outlined.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Back Up Now")
                        }
                    }
                }

                // Disable backup
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { viewModel.disableBackup() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Disable auto backup",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Restore from backup
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingsItem(
                        title = "Restore from Backup",
                        subtitle = "Replace current data with a backup from Drive",
                        icon = Icons.Filled.Restore,
                        onClick = { viewModel.loadBackups() }
                    )
                }
            } else {
                // Enable backup prompt
                item {
                    SettingsItem(
                        title = "Back Up to Google Drive",
                        subtitle = "Nightly auto backup of your data",
                        icon = Icons.Outlined.FileDownload,
                        onClick = { viewModel.enableBackup() }
                    )
                }
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

    // ── Restore dialogs ───────────────────────────────────────────

    // Backup list dialog
    if (isLoadingBackups || availableBackups.isNotEmpty() || restoreError != null) {
        AlertDialog(
            onDismissRequest = {
                viewModel.clearRestoreError()
                viewModel.clearSelectedBackup()
            },
            title = { Text("Restore from Backup") },
            text = {
                Column {
                    if (isLoadingBackups) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text("Loading backups...")
                        }
                    } else if (restoreError != null && availableBackups.isEmpty()) {
                        Text(restoreError!!, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(
                            "Select a backup to restore. This will replace all current data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        availableBackups.take(10).forEach { backup ->
                            val isSelected = selectedBackupForRestore?.id == backup.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable { viewModel.selectBackupForRestore(backup) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(backup.name, fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${formatFileSize(backup.sizeBytes)} · ${formatDriveDate(backup.createdTime)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmRestore() },
                    enabled = selectedBackupForRestore != null && !isRestoring
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.clearRestoreError()
                    viewModel.clearSelectedBackup()
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Restoring progress
    if (isRestoring) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Restoring...") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Downloading and restoring backup...")
                }
            },
            confirmButton = { },
            dismissButton = { }
        )
    }

    // Restore success
    if (restoreSuccess) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Restore Complete") },
            text = {
                Text("Your data has been restored. The app needs to restart to apply the changes.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.restartApp() }) {
                    Text("Restart Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearRestoreResult() }) {
                    Text("Later")
                }
            }
        )
    }

    // Restore error (when not in the backup list dialog)
    if (restoreError != null && availableBackups.isEmpty() && !isLoadingBackups) {
        // Error already shown in the backup list dialog above
    }
}

/**
 * Format file size in human-readable form.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}

/**
 * Format a Drive ISO 8601 date string to a readable form.
 */
private fun formatDriveDate(isoDate: String): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        val out = java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a", java.util.Locale.US)
        out.format(sdf.parse(isoDate.substringBefore("."))!!)
    } catch (_: Exception) {
        isoDate.take(16)
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
