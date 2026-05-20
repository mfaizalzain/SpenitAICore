package com.fmz.spenitaicore.ui.taxrelief

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmz.spenitaicore.R
import com.fmz.spenitaicore.data.db.entity.Receipt
import com.fmz.spenitaicore.ui.components.CompactTopAppBar
import com.fmz.spenitaicore.ui.components.NativeAdCard
import com.fmz.spenitaicore.ui.components.SharedImportsBadgeIcon
import com.fmz.spenitaicore.ui.theme.spenItGradientBackground
import com.fmz.spenitaicore.util.CurrencyFormatter
import com.fmz.spenitaicore.util.DateUtils
import com.fmz.spenitaicore.viewmodel.TaxCategorySummary
import com.fmz.spenitaicore.viewmodel.TaxReliefViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxReliefScreen(
    viewModel: TaxReliefViewModel,
    onNavigateToSharedImports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToEditReceipt: (Int) -> Unit,
    sharedImportCount: Int = 0
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showYearDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(state.exportResult) {
        state.exportResult?.let { result ->
            val zipFile = File(result.zipPath)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Tax Relief Export ${state.selectedYear}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Tax Relief Export"))
            viewModel.clearExportResult()
        }
    }

    Scaffold(
        modifier = Modifier.spenItGradientBackground(),
        containerColor = Color.Transparent,
        topBar = {
            CompactTopAppBar(
                title = { Text("Tax Relief") },
                actions = {
                    IconButton(onClick = onNavigateToSharedImports) {
                        SharedImportsBadgeIcon(
                            count = sharedImportCount,
                            contentDescription = "Imports"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
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
            TaxReliefHeader(
                selectedYear = state.selectedYear,
                availableYears = state.availableYears,
                totalRelief = state.totalRelief,
                totalExpense = state.totalExpense,
                receiptCount = state.receipts.size,
                categoryCount = state.categories.size,
                currency = state.currency,
                isExporting = state.isExporting,
                canExport = state.receipts.isNotEmpty() && !state.isLoading,
                showYearDropdown = showYearDropdown,
                onShowYearDropdownChange = { showYearDropdown = it },
                onYearSelected = viewModel::selectYear,
                onExport = viewModel::exportSelectedYear
            )

            state.exportError?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = viewModel::clearExportError) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.receipts.isEmpty() -> {
                    EmptyTaxReliefState(modifier = Modifier.fillMaxSize())
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            val adUnitId = LocalContext.current.getString(R.string.admob_native_ad_unit_id)
                            NativeAdCard(
                                modifier = Modifier.fillMaxWidth(),
                                adUnitId = adUnitId
                            )
                        }

                        if (state.categories.isNotEmpty()) {
                            item {
                                CategorySummaryRow(
                                    categories = state.categories,
                                    currency = state.currency
                                )
                            }
                        }

                        item {
                            Text(
                                "Deductible expenses",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        items(state.receipts, key = { it.id }) { receipt ->
                            TaxReceiptCard(
                                receipt = receipt,
                                onClick = { onNavigateToEditReceipt(receipt.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxReliefHeader(
    selectedYear: String,
    availableYears: List<String>,
    totalRelief: Double,
    totalExpense: Double,
    receiptCount: Int,
    categoryCount: Int,
    currency: String,
    isExporting: Boolean,
    canExport: Boolean,
    showYearDropdown: Boolean,
    onShowYearDropdownChange: (Boolean) -> Unit,
    onYearSelected: (String) -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    FilledTonalButton(
                        onClick = { onShowYearDropdownChange(true) },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(selectedYear.ifBlank { "Tax year" })
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showYearDropdown,
                        onDismissRequest = { onShowYearDropdownChange(false) }
                    ) {
                        availableYears.forEach { year ->
                            DropdownMenuItem(
                                text = { Text(year) },
                                onClick = {
                                    onYearSelected(year)
                                    onShowYearDropdownChange(false)
                                }
                            )
                        }
                    }
                }
                FilledTonalButton(
                    onClick = onExport,
                    enabled = canExport && !isExporting,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Outlined.FolderZip, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (isExporting) "Exporting" else "Export ZIP")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Total expenses", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                CurrencyFormatter.format(totalExpense, currency),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (totalRelief > 0.0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tax relief: ${CurrencyFormatter.format(totalRelief, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "$receiptCount expense${if (receiptCount == 1) "" else "s"} across $categoryCount categor${if (categoryCount == 1) "y" else "ies"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun CategorySummaryRow(
    categories: List<TaxCategorySummary>,
    currency: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.take(2).forEach { category ->
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        category.category,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        CurrencyFormatter.formatCompact(category.totalExpense, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${category.receipts.size} item${if (category.receipts.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TaxReceiptCard(
    receipt: Receipt,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(receipt.categoryIcon, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    receipt.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${receipt.taxCategory ?: receipt.category} · ${DateUtils.format(receipt.date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    CurrencyFormatter.format(receipt.total, receipt.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (receipt.taxAmount > 0.0) {
                    Text(
                        "Tax relief: ${CurrencyFormatter.format(receipt.taxAmount, receipt.currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTaxReliefState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Receipt,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No tax-deductible expenses",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Mark expenses as tax deductible to see them here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
