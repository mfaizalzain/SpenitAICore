package com.fmz.spenitaicore.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmz.spenitaicore.R
import com.fmz.spenitaicore.data.db.entity.Receipt
import com.fmz.spenitaicore.ui.components.CompactTopAppBar
import com.fmz.spenitaicore.ui.components.ReceiptCard
import com.fmz.spenitaicore.ui.components.FullBottomSheet
import com.fmz.spenitaicore.ui.components.BottomSheetDialog
import com.fmz.spenitaicore.ui.components.DatePickerField
import com.fmz.spenitaicore.ui.components.AiCoreInstallDialog
import com.fmz.spenitaicore.ui.components.BannerAd
import com.fmz.spenitaicore.ui.components.SharedImportsBadgeIcon
import com.fmz.spenitaicore.ui.theme.SuccessGreen
import com.fmz.spenitaicore.ui.theme.WarningOrange
import com.fmz.spenitaicore.ui.theme.ErrorRed
import com.fmz.spenitaicore.viewmodel.DashboardViewModel
import com.fmz.spenitaicore.viewmodel.ExpensesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToScan: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSharedImports: () -> Unit,
    onNavigateToPaySlipScan: () -> Unit,
    sharedImportCount: Int = 0
) {
    val greeting by viewModel.greeting.collectAsStateWithLifecycle()
    val totalTodayText by viewModel.totalTodayText.collectAsStateWithLifecycle()
    val totalThisWeekText by viewModel.totalThisWeekText.collectAsStateWithLifecycle()
    val safeToSpendText by viewModel.safeToSpendText.collectAsStateWithLifecycle()
    val financialStatusText by viewModel.financialStatusText.collectAsStateWithLifecycle()
    val totalThisMonthText by viewModel.totalThisMonthText.collectAsStateWithLifecycle()
    val totalLastMonthText by viewModel.totalLastMonthText.collectAsStateWithLifecycle()
    val monthOverMonthText by viewModel.monthOverMonthText.collectAsStateWithLifecycle()
    val isSpendingUp by viewModel.isSpendingUp.collectAsStateWithLifecycle()
    val totalIncomeThisMonthText by viewModel.totalIncomeThisMonthText.collectAsStateWithLifecycle()
    val averageDailySpendText by viewModel.averageDailySpendText.collectAsStateWithLifecycle()
    val taxDeductibleTotalText by viewModel.taxDeductibleTotalText.collectAsStateWithLifecycle()
    val dashboardStoryText by viewModel.dashboardStoryText.collectAsStateWithLifecycle()
    val recentReceipts by viewModel.recentReceipts.collectAsStateWithLifecycle()
    val latestInsightSummary by viewModel.latestInsightSummary.collectAsStateWithLifecycle()
    val latestInsightFinding by viewModel.latestInsightFinding.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val selectedReceipt by viewModel.selectedReceipt.collectAsStateWithLifecycle()
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    val selectedTagsDisplay by viewModel.selectedTagsDisplay.collectAsStateWithLifecycle()
    val isDetailVisible by viewModel.isDetailVisible.collectAsStateWithLifecycle()
    val isEditVisible by viewModel.isEditVisible.collectAsStateWithLifecycle()
    val editingReceipt by viewModel.editingReceipt.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showConvertConfirm by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var pendingDeleteReceipt by remember { mutableStateOf<Receipt?>(null) }

    LaunchedEffect(Unit) {
        viewModel.quietLoad()
    }

    var showAiCoreDialog by remember { mutableStateOf(true) }
    if (showAiCoreDialog) {
        AiCoreInstallDialog(onDismiss = { showAiCoreDialog = false })
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("SpenIt AICore") },
                actions = {
                    IconButton(onClick = onNavigateToSharedImports) {
                        SharedImportsBadgeIcon(count = sharedImportCount)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            DashboardFabMenu(
                expanded = showFabMenu,
                onExpandedChange = { showFabMenu = it },
                onAddExpense = {
                    showFabMenu = false
                    onNavigateToScan()
                },
                onAddIncome = {
                    showFabMenu = false
                    onNavigateToPaySlipScan()
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Greeting
            item {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Financial status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = safeToSpendText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = financialStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = dashboardStoryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Quick Stats
            item {
                Text(
                    text = "Quick Stats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            label = "Today",
                            value = totalTodayText,
                            icon = Icons.Outlined.Today
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            label = "This Week",
                            value = totalThisWeekText,
                            icon = Icons.Outlined.CalendarViewWeek
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            label = "Daily Avg",
                            value = averageDailySpendText,
                            icon = Icons.Outlined.Timeline
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            label = "Tax Deduct.",
                            value = taxDeductibleTotalText,
                            icon = Icons.Outlined.Receipt,
                            valueTint = SuccessGreen
                        )
                    }
                }
            }

            // AI-Powered Insights at a Glance
            item {
                InsightsAtGlanceCard(
                    thisMonthText = totalThisMonthText,
                    monthOverChange = monthOverMonthText,
                    isUp = isSpendingUp,
                    incomeThisCycleText = totalIncomeThisMonthText,
                    dailyAvgText = averageDailySpendText,
                    taxDeductibleText = taxDeductibleTotalText,
                    aiSummary = latestInsightSummary,
                    aiFinding = latestInsightFinding
                )
            }

            // Recent receipts header
            item {
                Text(
                    text = "Recent Expenses",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Receipts list
            if (recentReceipts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "\uD83E\uDDFE",
                                style = MaterialTheme.typography.displaySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No expenses yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Scan your first expense to get started",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recentReceipts, key = { it.id }) { receipt ->
                    ReceiptCard(
                        receipt = receipt,
                        onClick = { viewModel.viewReceipt(receipt) },
                        onEdit = { viewModel.startEdit(receipt) },
                        onDelete = {
                            pendingDeleteReceipt = receipt
                            showDeleteConfirm = true
                        }
                    )
                }
            }

            // Banner ad
            item {
                val adUnitId = LocalContext.current.getString(R.string.admob_banner_ad_unit_id)
                BannerAd(
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    adUnitId = adUnitId
                )
            }

            // Bottom spacer for FAB
            item { Spacer(modifier = Modifier.height(72.dp)) }
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
                            text = { Text("${com.fmz.spenitaicore.data.db.entity.Receipt.getCategoryIcon(cat)} $cat") },
                            onClick = {
                                currentCategory = cat
                                catDropdown = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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
                    // Transparent overlay to catch taps on the text field
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(end = 48.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) { taxCatDropdown = true }
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
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            selectedReceipt?.let { receipt ->
                Text(
                    text = receipt.merchant,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Amount", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${receipt.currency} ${"%.2f".format(receipt.total)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Column {
                        Text("Category", style = MaterialTheme.typography.labelSmall)
                        Text(receipt.categoryIcon + " " + receipt.category, style = MaterialTheme.typography.bodyMedium)
                    }
                    Column {
                        Text("Date", style = MaterialTheme.typography.labelSmall)
                        Text(receipt.date, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (receipt.taxAmount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tax: ${receipt.currency} ${"%.2f".format(receipt.taxAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (receipt.isTaxDeductible) {
                    Text(
                        "Tax Year: ${receipt.taxYear} \u00B7 ${receipt.taxCategory ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (selectedTagsDisplay.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tags: $selectedTagsDisplay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                receipt.notes?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Notes: $it", style = MaterialTheme.typography.bodyMedium)
                }

                if (selectedItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    selectedItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${item.description} (x${"%.0f".format(item.quantity)})",
                                modifier = Modifier.weight(1f)
                            )
                            Text("${receipt.currency} ${"%.2f".format(item.total)}")
                        }
                    }
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
                        onClick = { selectedReceipt?.let { viewModel.startEdit(it) } }
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
                        onClick = { showConvertConfirm = true },
                        enabled = !isBusy
                    ) {
                        Icon(
                            Icons.Filled.SwapHoriz, contentDescription = "Convert to Income",
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
                            pendingDeleteReceipt = selectedReceipt
                            showDeleteConfirm = true
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

    // Delete confirmation dialog
    if (showDeleteConfirm && pendingDeleteReceipt != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false; pendingDeleteReceipt = null },
            title = { Text("Delete Expense") },
            text = { Text("Delete \"${pendingDeleteReceipt?.merchant}\" permanently? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteReceipt?.let {
                            viewModel.deleteReceipt(it)
                            viewModel.dismissDetail()
                        }
                        showDeleteConfirm = false
                        pendingDeleteReceipt = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false; pendingDeleteReceipt = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Convert confirmation dialog
    if (showConvertConfirm && selectedReceipt != null) {
        AlertDialog(
            onDismissRequest = { showConvertConfirm = false },
            title = { Text("Convert to Income") },
            text = { Text("Convert \"${selectedReceipt?.merchant}\" to an income entry? The expense will be deleted and a new income record created.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedReceipt?.let { viewModel.convertToIncome(it) }
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

@Composable
private fun DashboardFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (expanded) {
            SmallFloatingActionButton(
                onClick = onAddIncome,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Payments, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Income")
                }
            }
            SmallFloatingActionButton(
                onClick = onAddExpense,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Expense")
                }
            }
        }
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = if (expanded) "Close add menu" else "Open add menu"
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valueTint: Color? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = valueTint ?: Color.Unspecified
            )
        }
    }
}

@Composable
fun ActionChip(
    modifier: Modifier = Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── Insights at a Glance ───────────────────────────────────────────

@Composable
fun InsightsAtGlanceCard(
    thisMonthText: String,
    monthOverChange: String,
    isUp: Boolean,
    incomeThisCycleText: String,
    dailyAvgText: String,
    taxDeductibleText: String,
    aiSummary: String = "",
    aiFinding: String = "",
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\uD83D\uDCCA Insights at a Glance",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Top row: Monthly spend + trend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InsightMiniStat(
                    modifier = Modifier.weight(1f),
                    label = "Spent this cycle",
                    value = thisMonthText
                )
                InsightMiniStat(
                    modifier = Modifier.weight(1f),
                    label = "vs Last Cycle",
                    value = monthOverChange,
                    valueColor = if (isUp) ErrorRed else SuccessGreen
                )
                InsightMiniStat(
                    modifier = Modifier.weight(1f),
                    label = "Daily Avg",
                    value = dailyAvgText
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom row: Income + tax
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InsightMiniStat(
                    modifier = Modifier.weight(1f),
                    label = "Income this cycle",
                    value = incomeThisCycleText
                )
                InsightMiniStat(
                    modifier = Modifier.weight(1f),
                    label = "Tax Deductible",
                    value = taxDeductibleText
                )
                Box(modifier = Modifier.weight(1f))
            }

            // AI insight summary
            if (aiSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = aiSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (aiFinding.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text("\uD83D\uDCA1 ", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = aiFinding,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightMiniStat(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
