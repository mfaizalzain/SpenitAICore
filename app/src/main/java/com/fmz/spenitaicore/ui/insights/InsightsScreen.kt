package com.fmz.spenitaicore.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fmz.spenitaicore.R
import com.fmz.spenitaicore.ai.CategoryBreakdown
import com.fmz.spenitaicore.ai.SavingTip
import com.fmz.spenitaicore.ai.SpendingTrend
import com.fmz.spenitaicore.ui.components.CompactTopAppBar
import com.fmz.spenitaicore.ui.components.BannerAd
import com.fmz.spenitaicore.ui.theme.*
import com.fmz.spenitaicore.viewmodel.InsightsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(viewModel: InsightsViewModel) {
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    val totalThisMonthText by viewModel.totalThisMonthText.collectAsStateWithLifecycle()
    val averageDailySpendText by viewModel.averageDailySpendText.collectAsStateWithLifecycle()
    val taxDeductibleTotalText by viewModel.taxDeductibleTotalText.collectAsStateWithLifecycle()
    val monthOverMonthText by viewModel.monthOverMonthText.collectAsStateWithLifecycle()
    val isIncrease by viewModel.isIncrease.collectAsStateWithLifecycle()
    val periodSpendLabel by viewModel.periodSpendLabel.collectAsStateWithLifecycle()
    val topCategories by viewModel.topCategories.collectAsStateWithLifecycle()
    val savingTips by viewModel.savingTips.collectAsStateWithLifecycle()
    val weeklyTrend by viewModel.weeklyTrend.collectAsStateWithLifecycle()
    val aiSummary by viewModel.aiSummary.collectAsStateWithLifecycle()
    val keyFindings by viewModel.keyFindings.collectAsStateWithLifecycle()
    val anomalyAlert by viewModel.anomalyAlert.collectAsStateWithLifecycle()
    val aiStatusText by viewModel.aiStatusText.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.quietLoad()
    }

    Scaffold(
        topBar = {
            CompactTopAppBar(
                title = { Text("Insights") },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshInsights() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshInsights() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Range selector
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Last30" to "30D", "QuarterToDate" to "QTD", "YearToDate" to "YTD").forEach { (range, label) ->
                            FilterChip(
                                selected = selectedRange == range,
                                onClick = { viewModel.selectRange(range) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                // Stats cards
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InsightStatCard(
                            modifier = Modifier.weight(1f),
                            label = periodSpendLabel,
                            value = totalThisMonthText,
                            change = monthOverMonthText,
                            isPositive = !isIncrease
                        )
                        InsightStatCard(
                            modifier = Modifier.weight(1f),
                            label = "Daily Average",
                            value = averageDailySpendText,
                            change = null,
                            isPositive = null
                        )
                    }
                }

                // Tax deductible
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Tax Deductible", style = MaterialTheme.typography.labelSmall)
                                Text(taxDeductibleTotalText, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                            }
                            Icon(Icons.Filled.Receipt, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // AI Summary
                if (aiSummary != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("AI Analysis", style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(aiStatusText, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(aiSummary!!, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else if (aiStatusText.isNotBlank()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                aiStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Key findings
                if (keyFindings.isNotEmpty()) {
                    item {
                        Text("Key Findings", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                    }
                    items(keyFindings) { finding ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("\u2022 ", color = MaterialTheme.colorScheme.primary)
                            Text(finding, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Anomaly alert
                anomalyAlert?.let {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.2f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp)) {
                                Text("\u26A0\uFE0F ", style = MaterialTheme.typography.bodyMedium)
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // Category breakdown
                if (topCategories.isNotEmpty()) {
                    item {
                        Text("Top Categories", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                    }
                    items(topCategories) { cat ->
                        CategoryBar(cat)
                    }
                }

                // Weekly trend
                if (weeklyTrend.isNotEmpty()) {
                    item {
                        Text("Weekly Trend", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            weeklyTrend.forEach { trend ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "${"%.0f".format(trend.amount)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(((trend.normalizedHeight * 80)).coerceIn(4.0, 80.0).dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(trend.label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // Saving tips
                if (savingTips.isNotEmpty()) {
                    item {
                        Text("Saving Tips", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                    }
                    items(savingTips) { tip ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp)) {
                                Text(tip.icon, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tip.title, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(tip.description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
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

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun InsightStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    change: String?,
    isPositive: Boolean?
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            change?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = if (isPositive == true) SuccessGreen else ErrorRed)
            }
        }
    }
}

@Composable
fun CategoryBar(cat: CategoryBreakdown) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(cat.category, style = MaterialTheme.typography.bodySmall)
            Text("${"%.0f".format(cat.percentage)}%",
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (cat.percentage / 100.0).toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}
