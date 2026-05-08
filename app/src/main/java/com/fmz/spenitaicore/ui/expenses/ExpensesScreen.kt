package com.fmz.spenitaicore.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmz.spenitaicore.data.db.entity.Receipt
import com.fmz.spenitaicore.ui.components.CompactTopAppBar
import com.fmz.spenitaicore.ui.components.ReceiptCard
import com.fmz.spenitaicore.ui.components.BottomSheetDialog
import com.fmz.spenitaicore.ui.components.FullBottomSheet
import com.fmz.spenitaicore.ui.components.DatePickerField
import com.fmz.spenitaicore.ui.components.SharedImportsBadgeIcon
import com.fmz.spenitaicore.util.DateUtils
import com.fmz.spenitaicore.viewmodel.ExpensesViewModel

import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    viewModel: ExpensesViewModel,
    onNavigateToScan: () -> Unit,
    onNavigateToSharedImports: () -> Unit,
    sharedImportCount: Int = 0
) {
    val filteredReceipts by viewModel.filteredReceipts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val showTaxOnly by viewModel.showTaxOnly.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val totalText by viewModel.totalText.collectAsStateWithLifecycle()
    val isEditVisible by viewModel.isEditVisible.collectAsStateWithLifecycle()
    val isDetailVisible by viewModel.isDetailVisible.collectAsStateWithLifecycle()
    val editingReceipt by viewModel.editingReceipt.collectAsStateWithLifecycle()
    val selectedReceipt by viewModel.selectedReceipt.collectAsStateWithLifecycle()
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    val selectedTagsDisplay by viewModel.selectedTagsDisplay.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val availableTaxYears by viewModel.availableTaxYears.collectAsStateWithLifecycle()
    val selectedTaxYear by viewModel.selectedTaxYear.collectAsStateWithLifecycle()

    var editMerchant by remember { mutableStateOf("") }
    var editAmount by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("General") }
    var editNotes by remember { mutableStateOf("") }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showPeriodDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.quietLoad()
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("Expenses") },
                actions = {
                    IconButton(onClick = onNavigateToSharedImports) {
                        SharedImportsBadgeIcon(
                            count = sharedImportCount,
                            contentDescription = "Imports"
                        )
                    }
                    IconButton(onClick = onNavigateToScan) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToScan,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Scan Expense")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                placeholder = { Text("Search expenses... (tax 2024, >500, #groceries)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.onSearchQueryChanged("")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    } else {
                        IconButton(onClick = { viewModel.applySearchFilters() }) {
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Search")
                        }
                    }
                },
                singleLine = true
            )

            // Filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Period filter
                FilterChip(
                    selected = true,
                    onClick = { showPeriodDropdown = true },
                    label = {
                        val periods = listOf("Last30" to "Last 30 Days", "Last90" to "Last 90 Days", "ThisYear" to "This Year", "All" to "All Time")
                        Text(periods.firstOrNull { it.first == selectedPeriod }?.second ?: selectedPeriod)
                    },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) }
                )

                // Category filter
                FilterChip(
                    selected = selectedCategory != "All",
                    onClick = { showCategoryDropdown = true },
                    label = { Text(selectedCategory) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) }
                )

                // Tax toggle
                FilterChip(
                    selected = showTaxOnly,
                    onClick = { viewModel.setShowTaxOnly(!showTaxOnly) },
                    label = { Text("Tax") }
                )
            }

            // Total header
            Text(
                text = "Total: $totalText",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // List
            if (filteredReceipts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No expenses found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredReceipts, key = { it.id }) { receipt ->
                        ReceiptCard(
                            receipt = receipt,
                            onClick = { viewModel.viewReceipt(receipt) },
                            onEdit = { viewModel.startEdit(receipt) },
                            onDelete = { viewModel.deleteReceipt(receipt) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    // Period dropdown
    DropdownMenu(
        expanded = showPeriodDropdown,
        onDismissRequest = { showPeriodDropdown = false }
    ) {
        listOf("Last30" to "Last 30 Days", "Last90" to "Last 90 Days", "ThisYear" to "This Year", "All" to "All Time")
            .forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        viewModel.setPeriod(value)
                        showPeriodDropdown = false
                    }
                )
            }
    }

    // Category dropdown
    DropdownMenu(
        expanded = showCategoryDropdown,
        onDismissRequest = { showCategoryDropdown = false }
    ) {
        DropdownMenuItem(
            text = { Text("All") },
            onClick = {
                viewModel.setCategory("All")
                showCategoryDropdown = false
            }
        )
        ExpensesViewModel.SPENDING_CATEGORIES.forEach { cat ->
            DropdownMenuItem(
                text = { Text(cat) },
                onClick = {
                    viewModel.setCategory(cat)
                    showCategoryDropdown = false
                }
            )
        }
    }

    // Edit bottom sheet
    if (isEditVisible && editingReceipt != null) {
        var currentMerchant by remember { mutableStateOf(editingReceipt!!.merchant) }
        var currentAmount by remember { mutableStateOf(editingReceipt!!.total.toString()) }
        var currentDate by remember { mutableStateOf(editingReceipt!!.date) }
        var currentCategory by remember { mutableStateOf(editingReceipt!!.category) }
        var currentNotes by remember { mutableStateOf(editingReceipt!!.notes ?: "") }
        var isTaxDeductible by remember { mutableStateOf(editingReceipt!!.isTaxDeductible) }
        var taxCategory by remember { mutableStateOf(editingReceipt!!.taxCategory ?: "") }
        var catDropdown by remember { mutableStateOf(false) }
        var taxCatDropdown by remember { mutableStateOf(false) }

        BottomSheetDialog(
            visible = true,
            onDismiss = { viewModel.dismissEdit() },
            title = "Edit Expense",
            confirmText = "Save",
            onConfirm = {
                viewModel.saveEdit(
                    merchant = currentMerchant,
                    amountText = currentAmount,
                    category = currentCategory,
                    notes = currentNotes,
                    date = currentDate,
                    isTaxDeductible = isTaxDeductible,
                    taxCategory = taxCategory
                )
            }
        ) {
            OutlinedTextField(
                value = currentMerchant,
                onValueChange = { currentMerchant = it },
                label = { Text("Merchant") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            DatePickerField(
                value = currentDate,
                onValueChange = { currentDate = it },
                label = "Date"
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = currentAmount,
                onValueChange = { currentAmount = it },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedTextField(
                    value = currentCategory,
                    onValueChange = {},
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { catDropdown = true }) {
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                    }
                )
                DropdownMenu(expanded = catDropdown, onDismissRequest = { catDropdown = false }) {
                    ExpensesViewModel.SPENDING_CATEGORIES.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                currentCategory = cat
                                catDropdown = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Tax Deductible Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tax Deductible", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isTaxDeductible,
                    onCheckedChange = { isTaxDeductible = it }
                )
            }

            if (isTaxDeductible) {
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedTextField(
                        value = taxCategory,
                        onValueChange = {},
                        label = { Text("Tax Category") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { taxCatDropdown = true }) {
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = taxCatDropdown,
                        onDismissRequest = { taxCatDropdown = false }
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
                                    taxCategory = tc
                                    taxCatDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = currentNotes,
                onValueChange = { currentNotes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }
    }

    // Detail bottom sheet
    FullBottomSheet(
        visible = isDetailVisible,
        onDismiss = { viewModel.dismissDetail() },
        title = "Expense Details"
    ) {
        Column {
            // Show original document image if available
            val imageFile = selectedReceipt?.imagePath
                ?.takeIf { it.isNotEmpty() }
                ?.let { java.io.File(it) }
            if (imageFile != null && imageFile.exists()) {
                if (selectedReceipt?.isPdf == true) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("PDF Document", fontWeight = FontWeight.SemiBold)
                                Text(imageFile.name, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        coil.compose.AsyncImage(
                            model = imageFile,
                            contentDescription = "Expense document",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            selectedReceipt?.let { receipt ->
                Text(receipt.merchant, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Amount", style = MaterialTheme.typography.labelSmall)
                        Text("${receipt.currency} ${"%.2f".format(receipt.total)}",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text("Category", style = MaterialTheme.typography.labelSmall)
                        Text(receipt.category, style = MaterialTheme.typography.bodyMedium)
                    }
                Column {
                    Text("Date", style = MaterialTheme.typography.labelSmall)
                    Text(receipt.date, style = MaterialTheme.typography.bodyMedium)
                }
                Column {
                    Text("Created", style = MaterialTheme.typography.labelSmall)
                    Text(
                        java.time.Instant.ofEpochMilli(receipt.createdAt)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                }
                receipt.notes?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Notes: $it", style = MaterialTheme.typography.bodyMedium)
                }
                if (selectedTagsDisplay.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Tags: $selectedTagsDisplay", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selectedItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    selectedItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${item.description} (x${"%.0f".format(item.quantity)})", modifier = Modifier.weight(1f))
                            Text("${receipt.currency} ${"%.2f".format(item.total)}")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        selectedReceipt?.let { viewModel.startEdit(it) }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit")
                }

                Button(
                    onClick = {
                        selectedReceipt?.let { 
                            viewModel.deleteReceipt(it)
                            viewModel.dismissDetail()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
