package com.fmz.spenitaicore.ui.settings

import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lock
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
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.fmz.spenitaicore.ui.components.CompactTopAppBar
import com.fmz.spenitaicore.viewmodel.AuthViewModel
import com.fmz.spenitaicore.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onSignOut: () -> Unit
) {
    val selectedCurrency by viewModel.selectedCurrencyCode.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val salaryPayDay by viewModel.salaryPayDay.collectAsStateWithLifecycle()
    val isAppLockEnabled by viewModel.isAppLockEnabled.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val deleteInProgress by viewModel.deleteInProgress.collectAsStateWithLifecycle()

    // ── AI Provider state ──────────────────────────────────
    val aiProvider by viewModel.aiProvider.collectAsStateWithLifecycle()
    val aiApiKey by viewModel.aiApiKey.collectAsStateWithLifecycle()
    val aiModel by viewModel.aiModel.collectAsStateWithLifecycle()
    val aiCustomUrl by viewModel.aiCustomUrl.collectAsStateWithLifecycle()
    val aiProviderError by viewModel.aiProviderError.collectAsStateWithLifecycle()
    val aiCoreSupported by viewModel.aiCoreSupported.collectAsStateWithLifecycle()
    val aiProviderNames = viewModel.aiProviderNames
    val aiProviderKeys = viewModel.aiProviderKeys
    val aiProviderModels = viewModel.aiProviderModels

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // ── AI Provider dialog state ────────────────────────────
    var showAiProviderDialog by remember { mutableStateOf(false) }

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
    val isExportingAll by viewModel.isExportingAll.collectAsStateWithLifecycle()
    val fullExportResult by viewModel.fullExportResult.collectAsStateWithLifecycle()

    var showCurrencyDropdown by remember { mutableStateOf(false) }
    var showThemeDropdown by remember { mutableStateOf(false) }
    var showPayDayDropdown by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var pendingAppLockEnable by remember { mutableStateOf(false) }

    // Share the full-data export ZIP once it is ready.
    LaunchedEffect(fullExportResult) {
        fullExportResult?.let { result ->
            val zipFile = java.io.File(result.zipPath)
            if (zipFile.exists()) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        "SpenIt data export (${result.receiptCount} receipts, ${result.incomeCount} income entries)"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share data export"))
            }
            viewModel.clearFullExportResult()
        }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val canUseBiometrics = remember {
        try {
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                    BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) { false }
    }

    val appLockPrompt = remember(activity) {
        activity?.let { act ->
            BiometricPrompt(
                act,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        pendingAppLockEnable = false
                        viewModel.setAppLockEnabled(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        pendingAppLockEnable = false
                    }

                    override fun onAuthenticationFailed() {
                        Toast.makeText(context, "Biometric check failed", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    LaunchedEffect(pendingAppLockEnable) {
        if (pendingAppLockEnable && appLockPrompt != null) {
            pendingAppLockEnable = false
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable App Lock")
                .setSubtitle("Confirm biometrics to protect SpenIt")
                .setNegativeButtonText("Cancel")
                .build()
            appLockPrompt.authenticate(promptInfo)
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

    val appVersion = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { 
                    Text(
                        "Settings", 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
                        title = if (authState.isLoading) "Signing out..." else "Sign Out",
                        subtitle = if (authState.isLoading) "Disconnecting your Google account" else "Disconnect your Google account",
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        enabled = !authState.isLoading,
                        isLoading = authState.isLoading,
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
                            authViewModel.signInWithGoogle(context)
                        }
                    )
                }
            }

            // Preferences section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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

            // Theme
            item {
                Box {
                    SettingsItem(
                        title = "Theme",
                        subtitle = viewModel.availableThemeModes.firstOrNull { it.first == themeMode }?.second ?: "Day",
                        icon = Icons.Filled.DarkMode,
                        onClick = { showThemeDropdown = true }
                    )
                    DropdownMenu(
                        expanded = showThemeDropdown,
                        onDismissRequest = { showThemeDropdown = false }
                    ) {
                        viewModel.availableThemeModes.forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDropdown = false
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

            // Budgets
            item {
                SettingsItem(
                    title = "Budgets",
                    subtitle = "Set monthly limits per category",
                    icon = Icons.Filled.Insights,
                    onClick = onNavigateToBudgets
                )
            }

            // Security section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
                    onCheckedChange = { enabled ->
                        if (!enabled) {
                            viewModel.setAppLockEnabled(false)
                        } else if (!canUseBiometrics || appLockPrompt == null) {
                            Toast.makeText(
                                context,
                                "Biometric authentication is not available on this device",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            pendingAppLockEnable = true
                        }
                    },
                    icon = Icons.Outlined.Lock
                )
            }

            // ── AI Provider section ───────────────────────────────────────────
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("AI Provider", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }

            // AI Provider settings card
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
                                imageVector = when {
                                    aiApiKey.isNotEmpty() -> Icons.Filled.CheckCircle
                                    else -> Icons.Filled.SmartToy
                                },
                                contentDescription = null,
                                tint = when {
                                    aiApiKey.isNotEmpty() -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    aiProviderNames[aiProvider] ?: "On-device (AICore)",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    when {
                                        aiApiKey.isNotEmpty() -> "API key configured"
                                        else -> "Free on-device AI · No key needed"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (aiProviderError != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                aiProviderError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        if (aiApiKey.isNotEmpty() && aiProvider != "aicore") {
                            // Show configured provider with remove option
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showAiProviderDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit")
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.removeAiApiKey() },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remove")
                                }
                            }
                        } else if (aiProvider == "aicore") {
                            // On-device — let user switch to a remote provider
                            OutlinedButton(
                                onClick = { showAiProviderDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add API Key")
                            }

                            Spacer(Modifier.height(8.dp))
                            if (aiCoreSupported) {
                                Text(
                                    "Note: Built-in on-device AI (AICore) may not be as accurate as using an external API key.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "On-device AI is not supported on this phone. Configure an API key above to use a remote AI provider for document scanning and insights.",
                                        modifier = Modifier.padding(12.dp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Backup section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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

            // Backup toggle switch
            item {
                val backupSubtitle = if (isBackupEnabled) {
                    if (backupAccountName != null) "Backed up to $backupAccountName · Nightly"
                    else "Nightly auto backup enabled"
                } else {
                    "Off — back up your data to Google Drive"
                }
                SwitchSettingsItem(
                    title = "Back Up to Google Drive",
                    subtitle = backupSubtitle,
                    checked = isBackupEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        viewModel.toggleBackup(enabled)
                    },
                    icon = Icons.Outlined.FileDownload
                )
            }

            // Backup details (only when enabled)
            if (isBackupEnabled) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (lastBackupTime > 0) {
                                val formatter = remember { java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.US) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Last backup: ${formatter.format(java.util.Date(lastBackupTime))}",
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Manual backup button
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

                            Spacer(modifier = Modifier.height(4.dp))

                            // Restore from Backup
                            SettingsItem(
                                title = "Restore from Backup",
                                subtitle = "Replace current data with a backup from Drive",
                                icon = Icons.Filled.Restore,
                                onClick = { viewModel.loadBackups() }
                            )
                        }
                    }
                }
            }

            // Support section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Support", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                val ctx = LocalContext.current
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/faizalmzain")))
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("☕️", fontSize = MaterialTheme.typography.headlineMedium.fontSize)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Buy Me a Coffee",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold)
                            Text("Support development with a small donation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // About section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Data", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Export All Data",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold)
                        Text("Download every receipt and income entry as CSV (ZIP)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.exportAllData() },
                            enabled = !isExportingAll,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isExportingAll) {
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
                                Text("Export CSV")
                            }
                        }
                    }
                }
            }

            // About section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
                        Text("SpenIt AICore", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium)
                        Text("Version $appVersion", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Track your expenses and income with on-device AI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            // Delete Account
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SettingsItem(
                    title = "Delete Account & Data",
                    subtitle = "Permanently remove all your data",
                    icon = Icons.Filled.Delete,
                    onClick = { showDeleteConfirmDialog = true }
                )
            }
        }
    }

    RestoreBackupDialogs(
        isLoadingBackups = isLoadingBackups,
        availableBackups = availableBackups,
        restoreError = restoreError,
        isRestoring = isRestoring,
        restoreSuccess = restoreSuccess,
        selectedBackupForRestore = selectedBackupForRestore,
        onDismissList = { viewModel.clearBackupList() },
        onSelectBackup = { viewModel.selectBackupForRestore(it) },
        onConfirmRestore = { viewModel.confirmRestore() },
        onRestart = { viewModel.restartApp() },
        onClearRestoreResult = { viewModel.clearRestoreResult() }
    )

    AiProviderDialog(
        visible = showAiProviderDialog,
        currentApiKey = aiApiKey,
        currentProvider = aiProvider,
        currentModel = aiModel,
        currentCustomUrl = aiCustomUrl,
        providerNames = aiProviderNames,
        providerKeys = aiProviderKeys,
        providerModels = aiProviderModels,
        onSave = { provider, key, model, customUrl ->
            viewModel.saveAiApiKey(provider, key, model, customUrl)
        },
        onDismiss = { showAiProviderDialog = false }
    )

    DeleteAccountDialog(
        visible = showDeleteConfirmDialog,
        deleteInProgress = deleteInProgress,
        onConfirm = {
            showDeleteConfirmDialog = false
            viewModel.deleteAccount()
        },
        onDismiss = { showDeleteConfirmDialog = false }
    )
}
