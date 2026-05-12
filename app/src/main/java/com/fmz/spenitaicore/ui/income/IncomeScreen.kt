package com.fmz.spenitaicore.ui.income

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
import com.fmz.spenitaicore.data.db.entity.IncomeSources
import com.fmz.spenitaicore.ui.components.CompactTopAppBar
import com.fmz.spenitaicore.ui.components.DatePickerField
import com.fmz.spenitaicore.ui.components.IncomeCard
import com.fmz.spenitaicore.ui.components.BottomSheetDialog
import com.fmz.spenitaicore.ui.components.FullBottomSheet
import com.fmz.spenitaicore.ui.components.SharedImportsBadgeIcon
import com.fmz.spenitaicore.viewmodel.IncomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeScreen(
    viewModel: IncomeViewModel,
    onNavigateToScan: () -> Unit,
    onNavigateToSharedImports: () -> Unit,
    sharedImportCount: Int = 0
) {
    val incomeEntries by viewModel.incomeEntries.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val totalText by viewModel.totalText.collectAsStateWithLifecycle()
    val totalThisMonthText by viewModel.totalThisMonthText.collectAsStateWithLifecycle()
    val netText by viewModel.netText.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val isEditVisible by viewModel.isEditVisible.collectAsStateWithLifecycle()
    val isDetailVisible by viewModel.isDetailVisible.collectAsStateWithLifecycle()
    val editingEntry by viewModel.editingEntry.collectAsStateWithLifecycle()
    val selectedEntry by viewModel.selectedEntry.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showPeriodDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.quietLoad()
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("Income") },
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
                Icon(Icons.Filled.Add, contentDescription = "Scan Income")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Stats header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Income", style = MaterialTheme.typography.labelSmall)
                        Text(totalThisMonthText, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Net", style = MaterialTheme.typography.labelSmall)
                        Text(netText, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                placeholder = { Text("Search income... (#salary, >500, jan-mar)") },
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Period filter
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { showPeriodDropdown = true },
                        label = {
                            val periods = listOf("Last30" to "Last 30 Days", "Last90" to "Last 90 Days",
                                "ThisYear" to "This Year", "All" to "All Time")
                            Text(periods.firstOrNull { it.first == selectedPeriod }?.second ?: selectedPeriod)
                        },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) }
                    )
                    DropdownMenu(
                        expanded = showPeriodDropdown,
                        onDismissRequest = { showPeriodDropdown = false }
                    ) {
                        listOf("Last30" to "Last 30 Days", "Last90" to "Last 90 Days",
                            "ThisYear" to "This Year", "All" to "All Time")
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
                }

                // Category filter
                Box {
                    FilterChip(
                        selected = selectedCategory != "All",
                        onClick = { showCategoryDropdown = true },
                        label = { Text(selectedCategory) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) }
                    )
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
                        IncomeViewModel.INCOME_CATEGORIES.forEach { cat ->
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
            }

            // Total header
            Text(
                text = "Total: $totalText",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // List
            if (incomeEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No income entries found", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(incomeEntries, key = { it.id }) { entry ->
                        IncomeCard(
                            entry = entry,
                            onClick = { viewModel.viewIncome(entry) },
                            onEdit = { viewModel.startEdit(entry) },
                            onDelete = { viewModel.deleteIncome(entry) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    // Edit bottom sheet
    if (isEditVisible && editingEntry != null) {
        var editSource by remember { mutableStateOf(editingEntry!!.source) }
        var editCategory by remember { mutableStateOf(editingEntry!!.category) }
        var editAmount by remember { mutableStateOf(editingEntry!!.amount.toString()) }
        var editDate by remember { mutableStateOf(editingEntry!!.date) }
        var editNotes by remember { mutableStateOf(editingEntry!!.notes ?: "") }
        var categoryDropdown by remember { mutableStateOf(false) }

        BottomSheetDialog(
            visible = true,
            onDismiss = { viewModel.dismissEdit() },
            title = "Edit Income",
            confirmText = "Save",
            onConfirm = { viewModel.saveEdit(editSource, editCategory, editAmount, editDate, editNotes) }
        ) {
            OutlinedTextField(
                value = editSource,
                onValueChange = { editSource = it },
                label = { Text("Source") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedTextField(
                    value = editCategory,
                    onValueChange = {},
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { categoryDropdown = true }) {
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                    }
                )
                DropdownMenu(expanded = categoryDropdown, onDismissRequest = { categoryDropdown = false }) {
                    IncomeSources.All.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = { editCategory = category; categoryDropdown = false }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = editAmount,
                onValueChange = { editAmount = it },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            DatePickerField(
                value = editDate,
                onValueChange = { editDate = it },
                label = "Date",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = editNotes,
                onValueChange = { editNotes = it },
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
        title = "Income Details"
    ) {
        Column {
            // Show original document image if available
            val imageFile = selectedEntry?.imagePath
                ?.takeIf { it.isNotEmpty() }
                ?.let { java.io.File(it) }
            if (imageFile != null && imageFile.exists()) {
                if (selectedEntry?.isPdf == true) {
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
                            contentDescription = "Income document",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            selectedEntry?.let { entry ->
                Text(entry.categoryEmoji + " " + entry.source,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Amount", style = MaterialTheme.typography.labelSmall)
                        Text("${entry.currency} ${"%.2f".format(entry.amount)}",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        Text("Category", style = MaterialTheme.typography.labelSmall)
                        Text(entry.category, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column {
                        Text("Date", style = MaterialTheme.typography.labelSmall)
                        Text(entry.date, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (entry.isRecurring) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Recurring: ${entry.recurrenceInterval ?: "Monthly"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                entry.notes?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Notes: $it", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { selectedEntry?.let { viewModel.startEdit(it) } }
                    ) {
                        Icon(
                            Icons.Filled.Edit, contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Edit", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { selectedEntry?.let { viewModel.convertToExpense(it) } },
                        enabled = !isBusy
                    ) {
                        Icon(
                            Icons.Filled.SwapHoriz, contentDescription = "Convert to Expense",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Convert", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            selectedEntry?.let {
                                viewModel.deleteIncome(it)
                                viewModel.dismissDetail()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.Delete, contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Delete", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
