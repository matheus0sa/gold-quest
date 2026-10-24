package com.goldquest.ui

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
        if (ui.available.isEmpty()) {
            item { EmptyText("Nada guardado ainda. Compre uma recompensa na loja.") }
        }
        items(ui.available, key = { "inv-${it.id}" }) { inv ->
            TaskCard(
                title = inv.name,
                leading = { Text(inv.emoji, fontSize = 24.sp) },
                actions = { Button(onClick = { vm.useItem(inv.id) }) { Text("Usar") } },
            ) { MetaText("Comprado em ${formatDateTime(inv.purchasedAt)}") }
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

@Composable
fun HistoryScreen(ui: HistoryUi) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("Histórico") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Total("Ganho no total", "+${ui.earned}", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                Total("Gasto no total", "−${ui.spent}", MaterialTheme.colorScheme.error, Modifier.weight(1f))
            }
        }
        if (ui.transactions.isEmpty()) {
            item { EmptyText("Nenhuma movimentação ainda. Conclua uma tarefa para ganhar seu primeiro gold.") }
        }
        items(ui.transactions, key = { "tx-${it.id}" }) { t ->
            val positive = t.amount > 0
            TaskCard(
                title = t.description,
                trailingText = (if (positive) "+" else "−") + abs(t.amount),
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
