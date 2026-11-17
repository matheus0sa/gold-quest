package com.goldquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goldquest.data.Gem
import com.goldquest.data.GemBag
import com.goldquest.data.ShopItem
import com.goldquest.domain.MAX_GEMS_PER_ITEM
import com.goldquest.domain.Shortfall
import com.goldquest.domain.shortfall
import com.goldquest.domain.Trend
import com.goldquest.domain.daysLabel
import com.goldquest.domain.weekdayList

private class PendingRemoval(val name: String, val id: Long)

@Composable
fun ShopScreen(offers: List<Offer>, gold: Int, gems: GemBag, vm: MainViewModel) {
    var removal by remember { mutableStateOf<PendingRemoval?>(null) }
    var editingDays by remember { mutableStateOf<ShopItem?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ScreenTitle("Loja") }

        if (offers.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyText("A loja está vazia. Crie sua primeira recompensa abaixo.")
            }
        }
        items(offers, key = { it.item.id }) { offer ->
            OfferCard(
                offer, gold, gems,
                onBuy = { vm.buyItem(offer.item.id) },
                onEditDays = { editingDays = offer.item },
            ) { removal = PendingRemoval(offer.item.name, offer.item.id) }
        }

        if (offers.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "O gold sobe quando você compra muito o mesmo item e entra em promoção " +
                        "quando ele fica esquecido. As joias têm preço fixo e só vêm das atividades que as dão.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { NewRewardForm(startOpen = offers.isEmpty(), vm = vm) }
    }

    removal?.let { r ->
        ConfirmDialog("Remover ${r.name} da loja?", onConfirm = { vm.deleteShopItem(r.id) }, onDismiss = { removal = null })
    }
    editingDays?.let { item ->
        WeekdaysDialog(item, onSave = { vm.setShopItemWeekdays(item.id, it) }, onDismiss = { editingDays = null })
    }
}

@Composable
private fun WeekdaysDialog(item: ShopItem, onSave: (Set<Int>) -> Unit, onDismiss: () -> Unit) {
    var days by remember(item.id) { mutableStateOf(item.weekdayList().toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.emoji} ${item.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Em quais dias pode ser usado? Sem nenhum marcado, vale qualquer dia.", fontSize = 14.sp)
                WeekdayPicker(days) { days = it }
                Text(
                    "Só dá para comprar e usar nesses dias. Os itens que você já tem guardados também passam a segui-los.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(days); onDismiss() }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun OfferCard(
    offer: Offer,
    gold: Int,
    gems: GemBag,
    onBuy: () -> Unit,
    onEditDays: () -> Unit,
    onRemove: () -> Unit,
) {
    val info = offer.info
    val missing = shortfall(info.price, offer.item.gems, gold, gems)
    val trendColor = when (info.trend) {
        Trend.UP -> MaterialTheme.colorScheme.error
        Trend.DOWN -> MaterialTheme.colorScheme.secondary
        Trend.BASE -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(offer.item.emoji, fontSize = 36.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) { Text("✕") }
            }
            Text(offer.item.name, fontWeight = FontWeight.SemiBold, minLines = 2, maxLines = 2)
            TextButton(
                onClick = onEditDays,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            ) { Text("📅 ${offer.item.daysLabel()}", fontSize = 13.sp, maxLines = 1) }
            if (info.price > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Coin(16.dp)
                Text(info.price.toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = trendColor)
                when (info.trend) {
                    Trend.UP -> Text("▲", color = trendColor, fontSize = 12.sp)
                    Trend.DOWN -> Text("▼", color = trendColor, fontSize = 12.sp)
                    Trend.BASE -> {}
                }
                if (info.trend != Trend.BASE) {
                    Text(
                        info.base.toString(),
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
            if (offer.item.gems.isNotEmpty()) {
                GemCostRow(offer.item.gems, size = 18.dp, fontSize = 17.sp, missing = missing.gems)
            }
            if (info.trend != Trend.BASE) {
                Text(info.reason, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            if (!offer.availableToday) {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("Só ${offer.item.daysLabel()}", textAlign = TextAlign.Center, maxLines = 1)
                }
            } else if (!missing.isEmpty) {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    MissingLabel(missing)
                }
            } else {
                Button(onClick = onBuy, modifier = Modifier.fillMaxWidth()) { Text("Comprar", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun NewRewardForm(startOpen: Boolean, vm: MainViewModel) {
    var open by remember { mutableStateOf(startOpen) }
    var emoji by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(emptySet<Int>()) }
    var gemCost by remember { mutableStateOf(emptyMap<Gem, Int>()) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
                Text(if (open) "Fechar" else "Criar recompensa")
            }
            if (open) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(8) },
                        label = { Text("Ícone") },
                        placeholder = { Text("🎁") },
                        singleLine = true,
                        modifier = Modifier.weight(0.4f),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = { Text("Nome") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it.filter(Char::isDigit).take(7) },
                    label = { Text("Preço base em gold (0 = só joias)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Joias exigidas (opcional)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Ex.: videogame = 50 gold + 1 Esmeralda. Sem a joia não dá para comprar, mesmo com gold sobrando.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Column {
                    for (gem in Gem.entries) {
                        GemStepperRow(gem, gemCost[gem] ?: 0, MAX_GEMS_PER_ITEM) { n ->
                            gemCost = if (n <= 0) gemCost - gem else gemCost + (gem to n)
                        }
                    }
                }
                Text(
                    "Dias para usar (opcional)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WeekdayPicker(days) { days = it }
                Text(
                    if (days.isEmpty()) "Sem dia marcado: pode usar em qualquer dia."
                    else "Só dá para comprar e usar nesses dias.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Button(
                    onClick = {
                        vm.addShopItem(name, emoji, price.toIntOrNull(), days, gemCost)
                        if (name.isNotBlank() && ((price.toIntOrNull() ?: 0) > 0 || gemCost.isNotEmpty())) {
                            name = ""; emoji = ""; price = ""; days = emptySet(); gemCost = emptyMap(); open = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Adicionar à loja", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** "Faltam 20 [moeda] 1 [esmeralda]" no botão desativado. */
@Composable
private fun MissingLabel(missing: Shortfall) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Faltam")
        if (missing.gold > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Coin(14.dp)
                Text(missing.gold.toString())
            }
        }
        for (gem in Gem.entries) {
            val n = missing.gems[gem] ?: continue
            GemAmount(gem, n.toString(), size = 14.dp, bold = false, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
