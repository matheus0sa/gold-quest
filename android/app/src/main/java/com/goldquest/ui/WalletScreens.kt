package com.goldquest.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.goldquest.domain.WEEKDAY_LABELS
import com.goldquest.domain.daysLabel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldquest.domain.formatDateTime
import kotlin.math.abs

@Composable
fun InventoryScreen(ui: InventoryUi, vm: MainViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("Inventário") }
        if (ui.isEmpty) {
            item { EmptyText("Nada guardado ainda. Compre uma recompensa na loja.") }
        } else {
            item { WeekTable(ui) }
        }

        if (ui.forToday.isNotEmpty()) {
            item { SectionTitle("Para hoje", WEEKDAY_LABELS[ui.todayIndex]) }
            items(ui.forToday, key = { "inv-${it.id}" }) { inv ->
                TaskCard(
                    title = inv.name,
                    leading = { Text(inv.emoji, fontSize = 24.sp) },
                    actions = { Button(onClick = { vm.useItem(inv.id) }) { Text("Usar") } },
                ) {
                    MetaText("📅 ${inv.daysLabel()}")
                    MetaText("Comprado em ${formatDateTime(inv.purchasedAt)}")
                }
            }
        } else if (!ui.isEmpty) {
            item { EmptyText("Nada separado para hoje. Hoje é dia de descansar a carteira.") }
        }

        if (ui.otherDays.isNotEmpty()) {
            item { SectionTitle("Guardados para outros dias", ui.otherDays.size.toString()) }
            items(ui.otherDays, key = { "later-${it.id}" }) { inv ->
                TaskCard(
                    title = inv.name,
                    muted = true,
                    leading = { Text(inv.emoji, fontSize = 24.sp) },
                ) { MetaText("📅 Só ${inv.daysLabel()}") }
            }
        }

        if (ui.used.isNotEmpty()) {
            item { SectionTitle("Usados recentemente") }
            items(ui.used, key = { "used-${it.id}" }) { inv ->
                TaskCard(
                    title = inv.name,
                    muted = true,
                    leading = { Text(inv.emoji, fontSize = 24.sp) },
                ) { inv.usedAt?.let { MetaText("Usado em ${formatDateTime(it)}") } }
            }
        }
    }
}

/** Tabela Seg..Dom com o que está guardado para cada dia; hoje fica destacado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekTable(ui: InventoryUi) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            ui.week.forEach { day ->
                WeekRow(day.label, day.stacks, highlight = day.index == ui.todayIndex)
            }
            if (ui.anyDay.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
                WeekRow("Livre", ui.anyDay, highlight = false)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekRow(label: String, stacks: List<InventoryStack>, highlight: Boolean) {
    val accent = MaterialTheme.colorScheme.tertiary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlight) accent.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
            color = if (highlight) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.width(44.dp),
        )
        if (stacks.isEmpty()) {
            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                stacks.forEach { st ->
                    val text = "${st.emoji} ${st.name}" + if (st.count > 1) " ×${st.count}" else ""
                    Pill(text, MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(ui: HistoryUi) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("Histórico") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Total("Gold ganho", "+${ui.earned}", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                Total("Gold gasto", "−${ui.spent}", MaterialTheme.colorScheme.error, Modifier.weight(1f))
            }
        }
        if (ui.transactions.isEmpty()) {
            item { EmptyText("Nenhuma movimentação ainda. Conclua uma tarefa para ganhar seu primeiro gold.") }
        }
        items(ui.transactions, key = { "tx-${it.id}" }) { t ->
            val amount = (if (t.amount > 0) "+" else "−") + abs(t.amount)
            val gem = t.gem
            TaskCard(
                title = t.description,
                trailingText = if (gem == null) amount else null,
                actions = gem?.let {
                    {
                        GemAmount(
                            it, amount, size = 18.dp, fontSize = 16.sp,
                            color = if (t.amount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        )
                    }
                },
            ) { MetaText(formatDateTime(t.createdAt)) }
        }
    }
}

@Composable
private fun Total(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            MetaText(label)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }
    }
}
