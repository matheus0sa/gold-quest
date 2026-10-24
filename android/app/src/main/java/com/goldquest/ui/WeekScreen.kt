package com.goldquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldquest.data.Difficulty
import com.goldquest.domain.reward
import com.goldquest.domain.weekdayList

@Composable
fun WeekScreen(ui: WeekUi, vm: MainViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("Semana") }
        item { TagFilterBar(ui.tags, ui.filter, vm::setFilter) }

        if (ui.weekly.isNotEmpty()) {
            item { SectionTitle("Carga por dia") }
            item { LoadCard(ui) }
            item { SectionTitle("Tarefas por dia") }
            item { WeekTable(ui) }
        } else if (ui.monthly.isEmpty()) {
            item {
                EmptyText(
                    if (ui.filter == TagFilter.All) {
                        "Nenhuma tarefa recorrente ainda. Crie uma em Tarefas marcando os dias em que ela se repete."
                    } else {
                        "Nenhuma tarefa recorrente com este filtro."
                    }
                )
            }
        }

        if (ui.monthly.isNotEmpty()) {
            item { SectionTitle("Mensais", ui.monthly.size.toString()) }
            items(ui.monthly.size) { i ->
                val rec = ui.monthly[i]
                TaskCard(
                    title = rec.title,
                    leading = { Text("📅") },
                    trailingText = "+${rec.reward()}",
                ) {
                    DifficultyChip(rec.difficulty)
                    MetaText("1x por mês, qualquer dia")
                }
            }
        }
    }
}

@Composable
private fun SurfaceCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
private fun LoadCard(ui: WeekUi) {
    SurfaceCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (d in ui.days) {
                val isMax = ui.maxCount > 0 && d.count == ui.maxCount
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        d.label,
                        modifier = Modifier.width(36.dp),
                        fontWeight = if (d.index == ui.todayIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (d.index == ui.todayIndex) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val fraction = if (ui.maxCount > 0) d.count.toFloat() / ui.maxCount else 0f
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction)
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isMax) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.primary
                                    )
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            MetaText(
                                if (d.count == 0) "Dia livre"
                                else "${d.count} ${if (d.count == 1) "tarefa" else "tarefas"}, ${d.gold} gold possíveis"
                            )
                            if (isMax) Pill("Mais cheio", MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekTable(ui: WeekUi) {
    val cell = 32.dp
    SurfaceCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f))
                for (d in ui.days) {
                    Text(
                        d.label,
                        modifier = Modifier.width(cell),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = if (d.index == ui.todayIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (d.index == ui.todayIndex) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            for (rec in ui.weekly) {
                val days = rec.weekdayList()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rec.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2)
                        MetaText("+${rec.reward()} gold")
                    }
                    for (d in ui.days) {
                        Box(Modifier.width(cell), contentAlignment = Alignment.Center) {
                            if (d.index in days) Dot(rec.difficulty)
                        }
                    }
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                for (d in ui.days) {
                    Text(
                        d.count.toString(),
                        modifier = Modifier.width(cell),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
                for (d in Difficulty.entries) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dot(d)
                        MetaText(d.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(difficulty: Difficulty) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(difficultyColor(difficulty)))
}
