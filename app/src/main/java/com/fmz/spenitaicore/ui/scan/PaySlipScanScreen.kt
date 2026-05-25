package com.fmz.spenitaicore.ui.scan

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fmz.spenitaicore.data.db.entity.IncomeSources
import com.fmz.spenitaicore.R
import com.fmz.spenitaicore.ui.components.CompactTopAppBar
import com.fmz.spenitaicore.ui.components.DatePickerField
import com.fmz.spenitaicore.viewmodel.ReceiptScanViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

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
    val pdfPasswordError by viewModel.pdfPasswordError.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showConvertConfirm by remember { mutableStateOf(false) }

    // ---------- ML Kit Document Scanner (auto-crop payslips) ----------
    val documentScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            try {
                val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val firstPage = scanResult?.pages?.firstOrNull()
                val imageUri = firstPage?.imageUri
                if (imageUri != null) {
                    viewModel.setImageUri(imageUri)
                } else {
                    Toast.makeText(context, "No image returned", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("PaySlipScan", "Scan result failed", e)
                Toast.makeText(context, "Scan failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun launchDocumentScanner() {
        try {
            val options = GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .build()

            val scanner = GmsDocumentScanning.getClient(options)
            scanner.getStartScanIntent(context as android.app.Activity)
                .addOnSuccessListener { intentSender ->
                    documentScannerLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("PaySlipScan", "Scanner launch failed", e)
                    Toast.makeText(context, "Scanner not available: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            android.util.Log.e("PaySlipScan", "Scanner init failed", e)
            Toast.makeText(context, "${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_LONG).show()
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

    LaunchedEffect(pdfPasswordError) {
        if (pdfPasswordError) {
            Toast.makeText(context, context.getString(R.string.pdf_password_protected), Toast.LENGTH_LONG).show()
            viewModel.dismissPdfPasswordError()
        }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { 
                    Text(
                        "Add Income",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (viewModel.isEditing) {
                        TextButton(
                            onClick = { showConvertConfirm = true },
                            enabled = !isBusy
                        ) {
                            Text("Convert to Expense")
                        }
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
            val currentImagePath = imagePath
            if (hasImage && currentImagePath != null) {
                val imageFile = java.io.File(currentImagePath)
                val isPdf = currentImagePath.endsWith(".pdf", ignoreCase = true)
                
                if (isPdf) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clickable {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    imageFile
                                )
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("PDF Document",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(imageFile.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clickable {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    imageFile
                                )
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "image/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                    ) {
                        AsyncImage(
                            model = imageFile,
                            contentDescription = "Income document",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { launchDocumentScanner() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CameraAlt, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Re-scan")
                    }
                    FilledTonalButton(
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
                        Text("Capture Income",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { launchDocumentScanner() }
                            ) {
                                Icon(Icons.Filled.CameraAlt, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Scan Document")
                            }
                            Button(
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
                    // Transparent overlay to catch taps on the text field
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(end = 48.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) { showCategoryDropdown = true }
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

            Button(
                onClick = {
                    viewModel.saveReceipt()
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy && total > 0 && merchant.isNotBlank()
            ) {
                Text("Save Income")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Convert confirmation dialog
    if (showConvertConfirm) {
        AlertDialog(
            onDismissRequest = { showConvertConfirm = false },
            title = { Text("Convert to Expense") },
            text = { Text("Convert this income entry to an expense? The income record will be deleted and a new expense created.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.convertToOppositeType(onNavigateBack)
                        showConvertConfirm = false
                    }
                ) {
                    Text("Convert", color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConvertConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
