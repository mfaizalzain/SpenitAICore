package com.fmz.spenitaicore.ui.sharedimports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import com.fmz.spenitaicore.data.db.entity.SharedImportItem
import com.fmz.spenitaicore.data.db.entity.SharedImportKind
import com.fmz.spenitaicore.data.db.entity.SharedImportStatus
import com.fmz.spenitaicore.ui.components.CompactTopAppBar
import com.fmz.spenitaicore.viewmodel.SharedImportsViewModel
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedImportsScreen(
    viewModel: SharedImportsViewModel,
    pendingImportSignal: Int = 0,
    onNavigateBack: () -> Unit,
    onNavigateToEditReceipt: (Int) -> Unit = {},
    onNavigateToEditIncome: (Int) -> Unit = {},
    onImportCountChanged: (Int) -> Unit = {}
) {
    val imports by viewModel.imports.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val navigateToEdit by viewModel.navigateToEdit.collectAsStateWithLifecycle()
    val unclassifiedCount = imports.count { it.kind == SharedImportKind.Unknown }
    val processingCount = imports.count { it.status == SharedImportStatus.Processing || it.status == SharedImportStatus.InQueue }

    LaunchedEffect(imports.size) {
        onImportCountChanged(imports.size)
    }

    // Handle navigation to edit
    var showBankStatementDialog by remember { mutableStateOf<SharedImportsViewModel.EditNavigation.BankStatement?>(null) }

    LaunchedEffect(navigateToEdit) {
        when (val nav = navigateToEdit) {
            is SharedImportsViewModel.EditNavigation.Receipt -> {
                onNavigateToEditReceipt(nav.receiptId)
                viewModel.onEditNavigationHandled()
            }
            is SharedImportsViewModel.EditNavigation.Income -> {
                onNavigateToEditIncome(nav.incomeEntryId)
                viewModel.onEditNavigationHandled()
            }
            is SharedImportsViewModel.EditNavigation.BankStatement -> {
                showBankStatementDialog = nav
                viewModel.onEditNavigationHandled()
            }
            null -> { /* Do nothing */ }
        }
    }

    // Bank Statement dialog for choosing which entry to edit
    showBankStatementDialog?.let { bankNav ->
        AlertDialog(
            onDismissRequest = { showBankStatementDialog = null },
            title = { Text("View Imported Transactions") },
            text = {
                Column {
                    Text("This bank statement was imported as multiple entries. Select one to view/edit:")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (bankNav.incomeEntryIds.isNotEmpty()) {
                        Text("Income Entries:", fontWeight = FontWeight.Medium)
                        bankNav.incomeEntryIds.forEach { id ->
                            TextButton(
                                onClick = {
                                    showBankStatementDialog = null
                                    onNavigateToEditIncome(id)
                                }
                            ) {
                                Text("Income #$id")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    if (bankNav.receiptIds.isNotEmpty()) {
                        Text("Expense Entries:", fontWeight = FontWeight.Medium)
                        bankNav.receiptIds.forEach { id ->
                            TextButton(
                                onClick = {
                                    showBankStatementDialog = null
                                    onNavigateToEditReceipt(id)
                                }
                            ) {
                                Text("Expense #$id")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBankStatementDialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    LaunchedEffect(pendingImportSignal) {
        if (pendingImportSignal > 0) {
            viewModel.drainPendingFiles()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addFileUris(uris)
        }
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { 
                    Text(
                        "Shared Imports",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$pendingCount file(s) pending",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    FilledTonalButton(
                        onClick = { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) }
                    ) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Files")
                    }
                }
            }

            // Warning banner for unclassified files
            if (unclassifiedCount > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$unclassifiedCount file(s) need a type selected — tap \"Choose Type\" on each file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Processing banner
            if (processingCount > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Processing $processingCount file(s) — keep this page open",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Navigating away may interrupt the import",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
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
                        Text("📥", style = MaterialTheme.typography.displayMedium)
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
                            onMarkBank = { viewModel.setImportKind(item, SharedImportKind.BankStatement) },
                            onViewCompleted = { viewModel.onCompletedItemClick(item) },
                            onShare = { viewModel.shareImportResult(item) }
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
    onMarkBank: () -> Unit,
    onViewCompleted: () -> Unit,
    onShare: () -> Unit
) {
    var showKindMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (item.isCompleted) {
                    Modifier.clickable(onClick = onViewCompleted)
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                item.isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
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
                                SharedImportStatus.Completed -> "Tap to view/edit"
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

                // Action buttons — all consistently sized
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        IconButton(onClick = onViewCompleted, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, "View",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Share, "Share",
                                tint = MaterialTheme.colorScheme.primary)
                        }
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
