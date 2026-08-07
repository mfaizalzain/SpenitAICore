package com.fmz.spenitaicore.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmz.spenitaicore.data.db.entity.CategoryBudget
import com.fmz.spenitaicore.ui.components.CompactTopAppBar
import com.fmz.spenitaicore.ui.theme.spenItGradientBackground
import com.fmz.spenitaicore.util.CurrencyFormatter
import com.fmz.spenitaicore.viewmodel.BudgetsViewModel
import com.fmz.spenitaicore.viewmodel.ExpensesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    viewModel: BudgetsViewModel,
    onNavigateBack: () -> Unit
) {
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val spendingByCategory by viewModel.spendingByCategory.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf<CategoryBudget?>(null) }

    Scaffold(
        modifier = Modifier.spenItGradientBackground(),
        containerColor = Color.Transparent,
        topBar = {
            CompactTopAppBar(
                title = {
                    Text(
                        "Budgets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add budget")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Monthly limits per category. You'll see how much of each budget is already spent this month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (budgets.isEmpty()) {
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
                            Text("\uD83C\uDFAF", style = MaterialTheme.typography.displaySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No budgets yet",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Tap + to set a monthly limit for a category",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(budgets, key = { it.category }) { budget ->
                val spent = spendingByCategory[budget.category] ?: 0.0
                BudgetRow(
                    budget = budget,
                    spent = spent,
                    currency = currency,
                    onEdit = { editingBudget = budget },
                    onDelete = { viewModel.deleteBudget(budget.category) }
                )
            }
        }
    }

    if (showAddDialog || editingBudget != null) {
        AddBudgetDialog(
            existing = editingBudget,
            currency = currency,
            onDismiss = {
                showAddDialog = false
                editingBudget = null
            },
            onSave = { category, limit ->
                viewModel.setBudget(category, limit, currency)
                showAddDialog = false
                editingBudget = null
            }
        )
    }
}

@Composable
private fun BudgetRow(
    budget: CategoryBudget,
    spent: Double,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val ratio = if (budget.monthlyLimit > 0) spent / budget.monthlyLimit else 0.0
    val overBudget = ratio >= 1.0
    val nearLimit = ratio >= 0.8 && !overBudget
    val barColor = when {
        overBudget -> MaterialTheme.colorScheme.error
        nearLimit -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (overBudget) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${com.fmz.spenitaicore.data.db.entity.Receipt.getCategoryIcon(budget.category)} ${budget.category}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete budget",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${CurrencyFormatter.format(spent, currency)} of ${CurrencyFormatter.format(budget.monthlyLimit, budget.currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${"%.0f".format(ratio * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = barColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { ratio.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (overBudget) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Over budget by ${CurrencyFormatter.format(spent - budget.monthlyLimit, budget.currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (nearLimit) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Close to your limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            TextButton(onClick = onEdit) {
                Text("Edit limit")
            }
        }
    }
}

@Composable
private fun AddBudgetDialog(
    existing: CategoryBudget?,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {
    var selectedCategory by remember(existing?.category) { mutableStateOf(existing?.category ?: "") }
    var amountText by remember(existing?.category) {
        mutableStateOf(existing?.monthlyLimit?.let { "%.2f".format(it) } ?: "")
    }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) "Edit Budget" else "Add Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showCategoryDropdown = true }) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose category")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { showCategoryDropdown = false }
                    ) {
                        ExpensesViewModel.SPENDING_CATEGORIES.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("${com.fmz.spenitaicore.data.db.entity.Receipt.getCategoryIcon(cat)} $cat") },
                                onClick = {
                                    selectedCategory = cat
                                    showCategoryDropdown = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monthly limit ($currency)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val limit = amountText.toDoubleOrNull()
                    if (selectedCategory.isNotBlank() && limit != null && limit > 0) {
                        onSave(selectedCategory, limit)
                    }
                },
                enabled = selectedCategory.isNotBlank() &&
                    (amountText.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
