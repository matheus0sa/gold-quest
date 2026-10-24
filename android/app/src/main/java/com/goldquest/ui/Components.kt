package com.goldquest.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldquest.data.Difficulty
import com.goldquest.data.Tag
import com.goldquest.ui.theme.Gold
import com.goldquest.ui.theme.GoldDeep

@Composable
fun Coin(size: Dp = 20.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Gold)
            .border(size / 8, GoldDeep.copy(alpha = 0.45f), CircleShape)
    )
}

@Composable
fun difficultyColor(difficulty: Difficulty): Color = when (difficulty) {
    Difficulty.FACIL -> MaterialTheme.colorScheme.secondary
    Difficulty.MEDIA -> MaterialTheme.colorScheme.tertiary
    Difficulty.DIFICIL -> MaterialTheme.colorScheme.error
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f), modifier = modifier) {
        Text(
            text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun DifficultyChip(difficulty: Difficulty) = Pill(difficulty.label, difficultyColor(difficulty))

@Composable
fun TagChip(tag: Tag) = Pill("#${tag.name}", MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun PenaltyBadge(amount: Int) = Text(
    "⚠ −$amount",
    color = MaterialTheme.colorScheme.error,
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
)

@Composable
fun ScreenTitle(text: String) = Text(
    text,
    style = MaterialTheme.typography.headlineMedium,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.padding(bottom = 4.dp),
)

@Composable
fun SectionTitle(text: String, count: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count != null) {
            Text(count, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
fun EmptyText(text: String) = Text(
    text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(vertical = 8.dp),
)

@Composable
fun CheckCircle(checked: Boolean, onClick: (() -> Unit)?, description: String) {
    val color = if (checked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    var modifier = Modifier
        .size(28.dp)
        .clip(CircleShape)
        .background(if (checked) color else Color.Transparent)
        .border(2.dp, color, CircleShape)
    if (onClick != null && !checked) {
        modifier = modifier.clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (checked) Text("✓", color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold)
    }
}

/** Linha de tarefa: marcador, título com metadados, gold e ações opcionais. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    title: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailingText: String? = null,
    muted: Boolean = false,
    actions: @Composable (RowScope.() -> Unit)? = null,
    meta: @Composable () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) { meta() }
            }
            if (trailingText != null) {
                Text(
                    trailingText,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            actions?.invoke(this)
        }
    }
}

@Composable
fun MetaText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, color = color, fontSize = 13.sp)

/** Barra de filtro por tag, compartilhada entre Tarefas e Semana. */
@Composable
fun TagFilterBar(tags: List<Tag>, filter: TagFilter, onSelect: (TagFilter) -> Unit) {
    if (tags.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        item {
            FilterChip(filter == TagFilter.All, { onSelect(TagFilter.All) }, { Text("Todas") })
        }
        items(tags, key = { it.id }) { tag ->
            FilterChip(
                selected = filter == TagFilter.ById(tag.id),
                onClick = { onSelect(TagFilter.ById(tag.id)) },
                label = { Text("#${tag.name}") },
            )
        }
        item {
            FilterChip(filter == TagFilter.None, { onSelect(TagFilter.None) }, { Text("Sem tag") })
        }
    }
}

@Composable
fun ConfirmDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
