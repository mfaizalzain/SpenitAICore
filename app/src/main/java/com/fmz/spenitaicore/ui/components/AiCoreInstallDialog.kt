package com.fmz.spenitaicore.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.fmz.spenitaicore.ai.AiCoreService

@Composable
fun AiCoreInstallDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isInstalled = remember { AiCoreService.isAiCoreAvailable(context) }

    if (!isInstalled) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("AI Features Unavailable", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Google AICore is required for on-device AI expense scanning and insights. " +
                    "Download it from the Play Store (free, ~2GB). " +
                    "Basic manual entry is still available without AICore."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(AiCoreService.getInstallIntent())
                    onDismiss()
                }) { Text("Install") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        )
    }
}
