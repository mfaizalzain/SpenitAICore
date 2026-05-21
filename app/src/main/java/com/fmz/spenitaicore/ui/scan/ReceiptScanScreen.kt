package com.fmz.spenitaicore.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.fmz.spenitaicore.data.db.entity.ReceiptItem
import com.fmz.spenitaicore.ui.components.CompactTopAppBar
import com.fmz.spenitaicore.ui.components.DatePickerField
import com.fmz.spenitaicore.viewmodel.ExpensesViewModel
import com.fmz.spenitaicore.viewmodel.ReceiptScanViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScanScreen(
    viewModel: ReceiptScanViewModel,
    onNavigateBack: () -> Unit
) {
    val imagePath by viewModel.imagePath.collectAsStateWithLifecycle()
    val hasImage by viewModel.hasImage.collectAsStateWithLifecycle()
    val merchant by viewModel.merchant.collectAsStateWithLifecycle()
    val date by viewModel.date.collectAsStateWithLifecycle()
    val total by viewModel.total.collectAsStateWithLifecycle()
    val taxAmount by viewModel.taxAmount.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val isTaxDeductible by viewModel.isTaxDeductible.collectAsStateWithLifecycle()
    val taxCategory by viewModel.taxCategory.collectAsStateWithLifecycle()
    val tagsInput by viewModel.tagsInput.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showConvertConfirm by remember { mutableStateOf(false) }
    var showTaxCategoryDropdown by remember { mutableStateOf(false) }

    // ---------- ML Kit Document Scanner (auto-crop receipts) ----------
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
                android.util.Log.e("ReceiptScan", "Scan result failed", e)
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
                    android.util.Log.e("ReceiptScan", "Scanner launch failed", e)
                    Toast.makeText(context, "Scanner not available: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            android.util.Log.e("ReceiptScan", "Scanner init failed", e)
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

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { 
                    Text(
                        "Add Expense",
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
                            Text("Convert")
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
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Image section — either show thumbnail or capture buttons
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
                            contentDescription = "Expense receipt",
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
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Re-scan")
                    }
                    FilledTonalButton(
                        onClick = { documentPickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
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
                        Text("Capture Expense",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { launchDocumentScanner() }
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Scan Receipt")
                            }
                            Button(
                                onClick = { documentPickerLauncher.launch(arrayOf("image/*", "application/pdf")) }
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Files")
                            }
                        }
                    }
                }
            }

            // Extract button
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
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Extract Data")
                    }
                }
            }

            // Merchant
            OutlinedTextField(
                value = merchant,
                onValueChange = { viewModel.setMerchant(it) },
                label = { Text("Merchant *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Date
            DatePickerField(
                value = date,
                onValueChange = { viewModel.setDate(it) },
                label = "Date",
                modifier = Modifier.fillMaxWidth()
            )

            // Total
            OutlinedTextField(
                value = if (total == 0.0) "" else total.toString(),
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it == '.' }
                    if (filtered.count { it == '.' } <= 1) {
                        viewModel.setTotal(filtered.toDoubleOrNull() ?: 0.0)
                    }
                },
                label = { Text("Total") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // Tax amount
            OutlinedTextField(
                value = if (taxAmount == 0.0) "" else taxAmount.toString(),
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it == '.' }
                    if (filtered.count { it == '.' } <= 1) {
                        viewModel.setTaxAmount(filtered.toDoubleOrNull() ?: 0.0)
                    }
                },
                label = { Text("Tax Amount") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // Category dropdown
            Box {
                OutlinedTextField(
                    value = if (category.isEmpty()) "" else "${com.fmz.spenitaicore.data.db.entity.Receipt.getCategoryIcon(category)} $category",
                    onValueChange = {},
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showCategoryDropdown = true }) {
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                    }
                )
                // Transparent overlay to catch taps on the text field area
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
                    ExpensesViewModel.SPENDING_CATEGORIES.forEach { cat: String ->
                        DropdownMenuItem(
                            text = {
                                val ic = com.fmz.spenitaicore.data.db.entity.Receipt.getCategoryIcon(cat)
                                Text("$ic $cat")
                            },
                            onClick = {
                                viewModel.setCategory(cat)
                                showCategoryDropdown = false
                            }
                        )
                    }
                }
            }

            // Tax deductible toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tax Deductible", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isTaxDeductible,
                    onCheckedChange = { viewModel.setIsTaxDeductible(it) }
                )
            }

            if (isTaxDeductible) {
                Box {
                    OutlinedTextField(
                        value = taxCategory ?: "",
                        onValueChange = {},
                        label = { Text("Tax Category") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showTaxCategoryDropdown = true }) {
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                        }
                    )
                    // Transparent overlay to catch taps on the text field area
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(end = 48.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) { showTaxCategoryDropdown = true }
                    )
                    DropdownMenu(
                        expanded = showTaxCategoryDropdown,
                        onDismissRequest = { showTaxCategoryDropdown = false }
                    ) {
                        listOf(
                            "Business Expense", "Medical & Healthcare", "Parent Care",
                            "Lifestyle (General)", "Lifestyle (Sports)", "Education (Self)",
                            "Insurance & EPF", "Charitable Donation", "Housing Loan Interest",
                            "Zakat / Fitrah", "Other Reliefs"
                        ).forEach { tc ->
                            DropdownMenuItem(
                                text = { Text(tc) },
                                onClick = {
                                    viewModel.setTaxCategory(tc)
                                    showTaxCategoryDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Notes
            OutlinedTextField(
                value = notes ?: "",
                onValueChange = { viewModel.setNotes(it.ifBlank { null }) },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            // Tags
            OutlinedTextField(
                value = tagsInput,
                onValueChange = { viewModel.setTagsInput(it) },
                label = { Text("Tags (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("e.g. work, urgent") }
            )

            // Line items
            if (items.isNotEmpty()) {
                Text("Line Items", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                items.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.description, fontWeight = FontWeight.Medium)
                                Text("Qty: ${"%.0f".format(item.quantity)} x ${"%.2f".format(item.unitPrice)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${"%.2f".format(item.total)}")
                            IconButton(onClick = { viewModel.removeItem(item) }) {
                                Icon(Icons.Filled.Delete, "Remove",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.saveReceipt()
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy && merchant.isNotBlank()
            ) {
                Text("Save Expense")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
