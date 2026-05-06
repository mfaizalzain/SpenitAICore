package com.fmz.spenitaicore.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmz.spenitaicore.data.db.entity.ReceiptItem
import com.fmz.spenitaicore.ui.components.CompactTopAppBar
import com.fmz.spenitaicore.ui.components.DatePickerField
import com.fmz.spenitaicore.viewmodel.ReceiptScanViewModel

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
    var showTaxCategoryDropdown by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? android.graphics.Bitmap
            if (bitmap != null) {
                try {
                    val file = java.io.File(context.cacheDir, "receipt_${System.currentTimeMillis()}.jpg")
                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
                    viewModel.setImage(file.absolutePath)
                } catch (e: Exception) {
                    android.util.Log.e("ReceiptScan", "Failed to save bitmap", e)
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        } else {
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_LONG).show()
        }
    }

    fun launchCamera() {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                cameraLauncher.launch(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: Exception) {
            android.util.Log.e("ReceiptScan", "Camera launch failed", e)
            Toast.makeText(context, "${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.setImageUri(it) }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("Scan Expense") },
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
                        enabled = !isBusy && merchant.isNotBlank()
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
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Image section — either show thumbnail or capture buttons
            if (hasImage && imagePath != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // In production, use Coil to load the image
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("\uD83E\uDDFE", style = MaterialTheme.typography.displayMedium)
                            Text("Expense image captured",
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
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Retake")
                    }
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
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
                        Text("Capture Expense",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { launchCamera() }
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Camera")
                            }
                            FilledTonalButton(
                                onClick = { imagePickerLauncher.launch("image/*") }
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Gallery")
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
                    value = category,
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
                DropdownMenu(
                    expanded = showCategoryDropdown,
                    onDismissRequest = { showCategoryDropdown = false }
                ) {
                    com.fmz.spenitaicore.viewmodel.ExpensesViewModel.SPENDING_CATEGORIES.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
