package com.fmz.spenit.ui.sharedimports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmz.spenit.data.db.entity.SharedImportItem
import com.fmz.spenit.data.db.entity.SharedImportKind
import com.fmz.spenit.data.db.entity.SharedImportStatus
import com.fmz.spenit.viewmodel.SharedImportsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedImportsScreen(
    viewModel: SharedImportsViewModel,
    onNavigateBack: () -> Unit
) {
    val imports by viewModel.imports.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val paths = uris.map { it.toString() }
            val names = uris.map { it.lastPathSegment ?: "unknown" }
            viewModel.addFiles(paths, names)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shared Imports") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (imports.any { it.status == SharedImportStatus.Completed }) {
                        TextButton(onClick = { viewModel.clearCompleted() }) {
                            Text("Clear Done")
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
        ) {
            // Info banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$pendingCount file(s) pending",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    FilledTonalButton(
                        onClick = { filePickerLauncher.launch("image/*") }
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Files")
                    }
                }
            }

            // Import list
            if (imports.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83D\uDCE5", style = MaterialTheme.typography.displayMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No files to import",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Add files to scan and process them",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(imports, key = { it.id }) { item ->
                        ImportItemCard(
                            item = item,
                            onProcess = { viewModel.processImport(item) },
                            onRetry = { viewModel.retryImport(item) },
                            onRemove = { viewModel.removeImport(item) },
                            onMarkExpense = { viewModel.setImportKind(item, SharedImportKind.ExpenseReceipt) },
                            onMarkIncome = { viewModel.setImportKind(item, SharedImportKind.Income) },
                            onMarkBank = { viewModel.setImportKind(item, SharedImportKind.BankStatement) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun ImportItemCard(
    item: SharedImportItem,
    onProcess: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onMarkExpense: () -> Unit,
    onMarkIncome: () -> Unit,
    onMarkBank: () -> Unit
) {
    var showKindMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Kind badge
                        Box {
                            AssistChip(
                                onClick = {
                                    if (item.kind == SharedImportKind.Unknown) showKindMenu = true
                                },
                                label = {
                                    Text(
                                        when (item.kind) {
                                            SharedImportKind.ExpenseReceipt -> "Expense"
                                            SharedImportKind.Income -> "Income"
                                            SharedImportKind.BankStatement -> "Bank"
                                            SharedImportKind.Unknown -> "Choose Type"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.height(28.dp)
                            )
                            DropdownMenu(
                                expanded = showKindMenu,
                                onDismissRequest = { showKindMenu = false }
                            ) {
                                DropdownMenuItem(text = { Text("Expense Receipt") },
                                    onClick = { onMarkExpense(); showKindMenu = false })
                                DropdownMenuItem(text = { Text("Income") },
                                    onClick = { onMarkIncome(); showKindMenu = false })
                                DropdownMenuItem(text = { Text("Bank Statement") },
                                    onClick = { onMarkBank(); showKindMenu = false })
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when (item.status) {
                                SharedImportStatus.NeedsReview -> "Needs Review"
                                SharedImportStatus.Processing -> "Processing"
                                SharedImportStatus.Completed -> "Done"
                                SharedImportStatus.Failed -> "Failed"
                                SharedImportStatus.Skipped -> "Skipped"
                                SharedImportStatus.InQueue -> "Queued"
                                SharedImportStatus.Duplicate -> "Duplicate"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (item.status) {
                                SharedImportStatus.Completed -> MaterialTheme.colorScheme.primary
                                SharedImportStatus.Failed -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Action buttons
                Row {
                    if (item.canProcess) {
                        IconButton(onClick = onProcess, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.PlayArrow, "Process")
                        }
                    }
                    if (item.canRetry) {
                        IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Refresh, "Retry")
                        }
                    }
                    if (item.isCompleted) {
                        Icon(Icons.Filled.CheckCircle, "Done",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Delete, "Remove",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item.statusMessage?.let {
                if (it.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
