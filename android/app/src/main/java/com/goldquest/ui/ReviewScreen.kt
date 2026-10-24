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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goldquest.domain.ReviewItem
import com.goldquest.domain.WEEKDAY_LABELS
import com.goldquest.domain.formatDay
import com.goldquest.domain.weekdayIndex
import java.time.LocalDate

/** Primeira visita do dia: confirmar o que ficou sem marcar nos dias anteriores. */
@Composable
fun ReviewScreen(items: List<ReviewItem>, yesterday: LocalDate, onSubmit: (Map<String, Boolean>) -> Unit) {
    val answers = remember { mutableStateMapOf<String, Boolean>() }
    val byDay = items.groupBy { it.day }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenTitle("Revisão") }
        item {
            Text(
                "Antes de começar o dia: estas tarefas ficaram sem marcar. Se você fez e esqueceu, " +
                    "ainda dá para ganhar o gold. As que têm ⚠ custam gold se não foram feitas.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for ((day, dayItems) in byDay) {
            item(key = "day-$day") {
                SectionTitle(
                    if (day == yesterday) "Ontem" else WEEKDAY_LABELS[day.weekdayIndex],
                    formatDay(day),
                )
            }
            items(dayItems, key = { it.key }) { item ->
                ReviewCard(item, answers[item.key]) { answers[item.key] = it }
            }
        }
        item {
            Button(
                onClick = { onSubmit(answers.toMap()) },
                enabled = items.all { it.key in answers },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) { Text("Concluir revisão", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun ReviewCard(item: ReviewItem, answer: Boolean?, onAnswer: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DifficultyChip(item.difficulty)
                MetaText(item.detail)
                if (item.penalty > 0) PenaltyBadge(item.penalty)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = answer == true,
                    onClick = { onAnswer(true) },
                    label = { Text("Fiz · +${item.reward} gold") },
                )
                FilterChip(
                    selected = answer == false,
                    onClick = { onAnswer(false) },
                    label = {
                        Text(if (item.penalty > 0) "Não fiz · −${item.penalty} gold" else "Não fiz · sem penalidade")
                    },
                )
            }
        }
    }
}
