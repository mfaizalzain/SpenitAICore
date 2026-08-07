package com.fmz.spenitaicore.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fmz.spenitaicore.MainActivity
import com.fmz.spenitaicore.data.db.AppDatabase
import com.fmz.spenitaicore.data.preferences.AppPreferences
import com.fmz.spenitaicore.util.CurrencyFormatter
import com.fmz.spenitaicore.util.DateUtils
import com.fmz.spenitaicore.util.SalaryCycle
import java.time.LocalDate

/**
 * Home-screen widget showing the current salary-cycle spending, today's
 * spend and the safe-to-spend amount at a glance.
 */
class SpendWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent {
            SpendWidgetContent(data)
        }
    }
}

class SpendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpendWidget()
}

private data class SpendWidgetData(
    val cycleLabel: String,
    val cycleSpend: String,
    val todaySpend: String,
    val safeToSpend: String
)

private suspend fun loadWidgetData(context: Context): SpendWidgetData {
    val preferences = AppPreferences(context)
    val currency = preferences.getDefaultCurrency()
    val payDay = preferences.getSalaryPayDay()
    val now = LocalDate.now()
    val cycle = SalaryCycle.getCurrentPeriod(payDay, now)

    val db = AppDatabase.getInstance(context)
    val cutoff = DateUtils.fromLocalDate(now.minusMonths(6))
    val receipts = db.receiptDao().getReceiptsFromSync(cutoff)
    val incomes = db.incomeEntryDao().getIncomeEntriesFromSync(cutoff)

    var cycleSpend = 0.0
    var todaySpend = 0.0
    var cycleIncome = 0.0
    val todayStr = DateUtils.today()

    for (r in receipts) {
        val date = try {
            DateUtils.toLocalDate(r.date)
        } catch (_: Exception) {
            continue
        }
        if (SalaryCycle.isInPeriod(date, cycle)) cycleSpend += r.total
        if (r.date == todayStr) todaySpend += r.total
    }
    for (e in incomes) {
        val date = try {
            DateUtils.toLocalDate(e.date)
        } catch (_: Exception) {
            continue
        }
        if (SalaryCycle.isInPeriod(date, cycle)) cycleIncome += e.amount
    }

    return SpendWidgetData(
        cycleLabel = cycle.label,
        cycleSpend = CurrencyFormatter.format(cycleSpend, currency),
        todaySpend = CurrencyFormatter.format(todaySpend, currency),
        safeToSpend = CurrencyFormatter.format(cycleIncome - cycleSpend, currency)
    )
}

@Composable
private fun SpendWidgetContent(data: SpendWidgetData) {
    val textPrimary = ColorProvider(Color(0xFFF8FAFC))
    val textSecondary = ColorProvider(Color(0xFF9FB6AF))
    val accent = ColorProvider(Color(0xFF2DD4BF))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF0D2A25)))
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.Top
    ) {
        Text(
            text = "SpenIt",
            style = TextStyle(fontSize = 11.sp, color = accent)
        )
        Text(
            text = data.cycleLabel,
            style = TextStyle(fontSize = 11.sp, color = textSecondary)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = data.cycleSpend,
            style = TextStyle(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text("Today", style = TextStyle(fontSize = 10.sp, color = textSecondary))
                Text(
                    data.todaySpend,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                )
            }
            Spacer(modifier = GlanceModifier.width(16.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text("Safe to spend", style = TextStyle(fontSize = 10.sp, color = textSecondary))
                Text(
                    data.safeToSpend,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                )
            }
        }
    }
}
