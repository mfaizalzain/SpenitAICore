package com.fmz.spenit.ui.income

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmz.spenit.ui.components.IncomeCard
import com.fmz.spenit.ui.components.BottomSheetDialog
import com.fmz.spenit.ui.components.FullBottomSheet
import com.fmz.spenit.viewmodel.IncomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeScreen(
    viewModel: IncomeViewModel,
    onNavigateToScan: () -> Unit,
    onNavigateToSharedImports: () -> Unit
) {
    val incomeEntries by viewModel.incomeEntries.collectAsStateWithLifecycle()
    val totalThisMonthText by viewModel.totalThisMonthText.collectAsStateWithLifecycle()
    val netText by viewModel.netText.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val isEditVisible by viewModel.isEditVisible.collectAsStateWithLifecycle()
    val isDetailVisible by viewModel.isDetailVisible.collectAsStateWithLifecycle()
    val editingEntry by viewModel.editingEntry.collectAsStateWithLifecycle()
    val selectedEntry by viewModel.selectedEntry.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.quietLoad()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Income") },
                actions = {
                    IconButton(onClick = onNavigateToSharedImports) {
                        Icon(Icons.Outlined.Inbox, contentDescription = "Imports")
                    }
                    IconButton(onClick = onNavigateToScan) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToScan) {
                Icon(Icons.Filled.Add, contentDescription = "Scan Pay Slip")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Stats header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
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

            // Period filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var showPeriod by remember { mutableStateOf(false) }
                FilterChip(
                    selected = true,
                    onClick = { showPeriod = true },
                    label = { Text(selectedPeriod) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) }
                )
                DropdownMenu(expanded = showPeriod, onDismissRequest = { showPeriod = false }) {
                    listOf("Last30" to "Last 30 Days", "Last90" to "Last 90 Days",
                        "ThisYear" to "This Year", "All" to "All Time")
                        .forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { viewModel.setPeriod(value); showPeriod = false }
                            )
                        }
                }
            }

            // List
            if (incomeEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No income entries", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
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
        var editAmount by remember { mutableStateOf(editingEntry!!.amount.toString()) }
        var editDate by remember { mutableStateOf(editingEntry!!.date) }
        var editNotes by remember { mutableStateOf(editingEntry!!.notes ?: "") }
        var sourceDropdown by remember { mutableStateOf(false) }

        BottomSheetDialog(
            visible = true,
            onDismiss = { viewModel.dismissEdit() },
            title = "Edit Income",
            confirmText = "Save",
            onConfirm = { viewModel.saveEdit(editSource, editAmount, editDate, editNotes) }
        ) {
            Box {
                OutlinedTextField(
                    value = editSource,
                    onValueChange = {},
                    label = { Text("Source") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { sourceDropdown = true }) {
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                    }
                )
                DropdownMenu(expanded = sourceDropdown, onDismissRequest = { sourceDropdown = false }) {
                    com.fmz.spenit.data.db.entity.IncomeSources.All.forEach { src ->
                        DropdownMenuItem(
                            text = { Text(src) },
                            onClick = { editSource = src; sourceDropdown = false }
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
            OutlinedTextField(
                value = editDate,
                onValueChange = { editDate = it },
                label = { Text("Date (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
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
        }
    }
}
