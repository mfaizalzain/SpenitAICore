package com.fmz.spenitaicore.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fmz.spenitaicore.data.backup.DriveBackupService

/**
 * All restore-related dialogs: the backup picker, the in-progress dialog
 * and the post-restore restart prompt.
 */
@Composable
fun RestoreBackupDialogs(
    isLoadingBackups: Boolean,
    availableBackups: List<DriveBackupService.BackupFile>,
    restoreError: String?,
    isRestoring: Boolean,
    restoreSuccess: Boolean,
    selectedBackupForRestore: DriveBackupService.BackupFile?,
    onDismissList: () -> Unit,
    onSelectBackup: (DriveBackupService.BackupFile) -> Unit,
    onConfirmRestore: () -> Unit,
    onRestart: () -> Unit,
    onClearRestoreResult: () -> Unit
) {
    // Backup list dialog
    if (isLoadingBackups || availableBackups.isNotEmpty() || restoreError != null) {
        AlertDialog(
            onDismissRequest = onDismissList,
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
                        Text(restoreError, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(
                            "Select a backup to restore. This will replace all current data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        availableBackups.take(5).forEach { backup ->
                            val isSelected = selectedBackupForRestore?.id == backup.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable { onSelectBackup(backup) },
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
                    onClick = onConfirmRestore,
                    enabled = selectedBackupForRestore != null && !isRestoring
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissList) {
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
                TextButton(onClick = onRestart) {
                    Text("Restart Now")
                }
            },
            dismissButton = {
                TextButton(onClick = onClearRestoreResult) {
                    Text("Later")
                }
            }
        )
    }
}

/**
 * Add/edit dialog for the remote AI provider configuration. Holds its own
 * local form state, seeded from the current values whenever it opens.
 */
@Composable
fun AiProviderDialog(
    visible: Boolean,
    currentApiKey: String,
    currentProvider: String,
    currentModel: String,
    currentCustomUrl: String,
    providerNames: Map<String, String>,
    providerKeys: List<String>,
    providerModels: Map<String, List<String>>,
    onSave: (provider: String, apiKey: String, model: String, customUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    var dialogProvider by remember { mutableStateOf("") }
    var dialogKey by remember { mutableStateOf("") }
    var dialogModel by remember { mutableStateOf("") }
    var dialogCustomUrl by remember { mutableStateOf("") }
    var showProviderDropdown by remember { mutableStateOf(false) }
    var showModelDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            dialogProvider = currentProvider.ifBlank { "gemini" }
            dialogKey = currentApiKey
            dialogModel = currentModel
            dialogCustomUrl = currentCustomUrl
            showProviderDropdown = false
            showModelDropdown = false
        }
    }

    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (currentApiKey.isNotEmpty()) "Edit AI Provider" else "Add AI Provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Provider selector
                Box {
                    OutlinedTextField(
                        value = providerNames[dialogProvider] ?: dialogProvider,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = currentApiKey.isEmpty()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showProviderDropdown = true }
                    )
                }

                DropdownMenu(
                    expanded = showProviderDropdown,
                    onDismissRequest = { showProviderDropdown = false }
                ) {
                    providerKeys.forEach { key ->
                        DropdownMenuItem(
                            text = { Text(providerNames[key] ?: key) },
                            onClick = {
                                dialogProvider = key
                                dialogKey = ""
                                dialogModel = providerModels[key]?.firstOrNull() ?: ""
                                dialogCustomUrl = ""
                                showProviderDropdown = false
                            },
                            enabled = key != "aicore"
                        )
                    }
                }

                OutlinedTextField(
                    value = dialogKey,
                    onValueChange = { dialogKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("Enter your API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (dialogProvider != "custom") {
                    val models = providerModels[dialogProvider] ?: emptyList()
                    if (models.isNotEmpty()) {
                        Box {
                            OutlinedTextField(
                                value = dialogModel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Model") },
                                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showModelDropdown = true }
                            )
                        }
                        DropdownMenu(
                            expanded = showModelDropdown,
                            onDismissRequest = { showModelDropdown = false }
                        ) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        dialogModel = model
                                        showModelDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (dialogProvider == "custom") {
                    OutlinedTextField(
                        value = dialogCustomUrl,
                        onValueChange = { dialogCustomUrl = it },
                        label = { Text("API Base URL") },
                        placeholder = { Text("https://openrouter.ai/api") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("The base URL of an OpenAI-compatible API endpoint")
                        }
                    )
                    OutlinedTextField(
                        value = dialogModel,
                        onValueChange = { dialogModel = it },
                        label = { Text("Model Name") },
                        placeholder = { Text("openai/gpt-4o-mini") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    "Your API key is stored securely on-device and never shared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(dialogProvider, dialogKey, dialogModel, dialogCustomUrl)
                    onDismiss()
                },
                enabled = dialogKey.isNotBlank() &&
                    (dialogProvider != "custom" || dialogCustomUrl.isNotBlank()) &&
                    dialogProvider != "aicore"
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteAccountDialog(
    visible: Boolean,
    deleteInProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete Account & Data") },
        text = {
            Text(
                "This will permanently delete all your expenses, income entries, and settings. " +
                "This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                enabled = !deleteInProgress
            ) {
                if (deleteInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (deleteInProgress) "Deleting..." else "Delete Everything")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !deleteInProgress
            ) {
                Text("Cancel")
            }
        }
    )
}
