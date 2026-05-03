package com.fmz.spenitaicore.ui.scan

import android.content.Intent
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmz.spenitaicore.data.db.entity.IncomeSources
import com.fmz.spenitaicore.ui.components.CompactTopAppBar
import com.fmz.spenitaicore.ui.components.DatePickerField
import com.fmz.spenitaicore.viewmodel.ReceiptScanViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaySlipScanScreen(
    viewModel: ReceiptScanViewModel,
    onNavigateBack: () -> Unit
) {
    val imagePath by viewModel.imagePath.collectAsStateWithLifecycle()
    val hasImage by viewModel.hasImage.collectAsStateWithLifecycle()
    val merchant by viewModel.merchant.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && capturedPhotoUri != null) {
            viewModel.setImage(capturedPhotoUri.toString())
        }
    }

    fun launchCamera() {
        try {
            val file = File(context.cacheDir, "payslip_${System.currentTimeMillis()}.jpg")
            file.createNewFile()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            capturedPhotoUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            viewModel.setImageUri(it)
        }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
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
                        enabled = !isBusy && total > 0 && merchant.isNotBlank()
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
                            Text("Income document selected",
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
                        onClick = { launchCamera() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CameraAlt, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Retake")
                    }
                    OutlinedButton(
                        onClick = { documentPickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Files")
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
                                onClick = { launchCamera() }
                            ) {
                                Icon(Icons.Filled.CameraAlt, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Camera")
                            }
                            FilledTonalButton(
                                onClick = { documentPickerLauncher.launch(arrayOf("image/*", "application/pdf")) }
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Files")
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

            // Employer / Source
            OutlinedTextField(
                value = merchant,
                onValueChange = { viewModel.setMerchant(it) },
                label = { Text("Employer / Source *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Business, null) }
            )

            // Income Category
            Box {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    label = { Text("Income Category") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    leadingIcon = { Icon(Icons.Filled.Category, null) },
                    trailingIcon = {
                        IconButton(onClick = { showCategoryDropdown = true }) {
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                    }
                )
                DropdownMenu(
                    expanded = showCategoryDropdown,
                    onDismissRequest = { showCategoryDropdown = false }
                ) {
                    IncomeSources.All.forEach { src ->
                        DropdownMenuItem(
                            text = { Text(src) },
                            onClick = {
                                viewModel.setCategory(src)
                                showCategoryDropdown = false
                            },
                            leadingIcon = {
                                val emoji = when (src) {
                                    "Salary" -> "\uD83D\uDCBC"
                                    "Freelance" -> "\uD83D\uDCBB"
                                    "Business" -> "\uD83C\uDFE2"
                                    "Investment" -> "\uD83D\uDCC8"
                                    "Rental" -> "\uD83C\uDFE0"
                                    "Bonus" -> "\uD83C\uDF81"
                                    "Gift" -> "\uD83C\uDF80"
                                    "Refund" -> "\uD83D\uDD04"
                                    "Commision" -> "\uD83E\uDD1D"
                                    else -> "\uD83D\uDCB0"
                                }
                                Text(emoji)
                            }
                        )
                    }
                }
            }

            // Income amount
            OutlinedTextField(
                value = if (total == 0.0) "" else total.toString(),
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it == '.' }
                    // Only allow one decimal point
                    if (filtered.count { it == '.' } <= 1) {
                        viewModel.setTotal(filtered.toDoubleOrNull() ?: 0.0)
                    }
                },
                label = { Text("Net Income Amount *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // Date
            DatePickerField(
                value = date,
                onValueChange = { viewModel.setDate(it) },
                label = "Date",
                modifier = Modifier.fillMaxWidth()
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
