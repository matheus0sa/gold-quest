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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.goldquest.domain.Trend

private class PendingRemoval(val name: String, val id: Long)

@Composable
fun ShopScreen(offers: List<Offer>, gold: Int, vm: MainViewModel) {
    var removal by remember { mutableStateOf<PendingRemoval?>(null) }

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
            OfferCard(offer, gold, onBuy = { vm.buyItem(offer.item.id) }) {
                removal = PendingRemoval(offer.item.name, offer.item.id)
            }
        }

        if (offers.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Os preços sobem quando você compra muito o mesmo item e entram em promoção " +
                        "quando ele fica esquecido. Variar o lazer sai mais barato.",
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
}

@Composable
private fun OfferCard(offer: Offer, gold: Int, onBuy: () -> Unit, onRemove: () -> Unit) {
    val info = offer.info
    val missing = info.price - gold
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            if (info.trend != Trend.BASE) {
                Text(info.reason, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            if (missing > 0) {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("Faltam $missing", textAlign = TextAlign.Center)
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
                    label = { Text("Preço base em gold") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        vm.addShopItem(name, emoji, price.toIntOrNull())
                        if (name.isNotBlank() && (price.toIntOrNull() ?: 0) > 0) {
                            name = ""; emoji = ""; price = ""; open = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Adicionar à loja", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
