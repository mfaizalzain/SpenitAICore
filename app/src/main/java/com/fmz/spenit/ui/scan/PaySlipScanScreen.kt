package com.fmz.spenit.ui.scan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmz.spenit.viewmodel.ReceiptScanViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaySlipScanScreen(
    viewModel: ReceiptScanViewModel,
    onNavigateBack: () -> Unit
) {
    val imagePath by viewModel.imagePath.collectAsStateWithLifecycle()
    val hasImage by viewModel.hasImage.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            viewModel.setImage(photoUri.toString())
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.setImageUri(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Pay Slip") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveReceipt()
                            onNavigateBack()
                        },
                        enabled = !isBusy && total > 0
                    ) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Image capture
            if (hasImage) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("\uD83D\uDCB0", style = MaterialTheme.typography.displayMedium)
                            Text("Pay slip image captured",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val file = File(context.cacheDir, "payslip_${System.currentTimeMillis()}.jpg")
                            photoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            cameraLauncher.launch(photoUri!!)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CameraAlt, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Retake")
                    }
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Gallery")
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("\uD83D\uDCF7", style = MaterialTheme.typography.displayMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Capture Pay Slip",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    val file = File(context.cacheDir, "payslip_${System.currentTimeMillis()}.jpg")
                                    photoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    cameraLauncher.launch(photoUri!!)
                                }
                            ) {
                                Icon(Icons.Filled.CameraAlt, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Camera")
                            }
                            FilledTonalButton(
                                onClick = { imagePickerLauncher.launch("image/*") }
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Gallery")
                            }
                        }
                    }
                }
            }

            // Extract
            if (hasImage) {
                Button(
                    onClick = { viewModel.extractReceiptData() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Processing...")
                    } else {
                        Icon(Icons.Filled.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Extract Data")
                    }
                }
            }

            // Income amount
            OutlinedTextField(
                value = if (total == 0.0) "" else total.toString(),
                onValueChange = { viewModel.setTotal(it.toDoubleOrNull() ?: 0.0) },
                label = { Text("Net Income Amount *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Date
            OutlinedTextField(
                value = date,
                onValueChange = { viewModel.setDate(it) },
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("yyyy-MM-dd") }
            )

            // Notes
            OutlinedTextField(
                value = notes ?: "",
                onValueChange = { viewModel.setNotes(it.ifBlank { null }) },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
