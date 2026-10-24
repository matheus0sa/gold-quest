package com.goldquest.domain

import com.goldquest.data.InventoryItem
import com.goldquest.data.ShopItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/*
 * Preços dinâmicos da loja.
 *
 * O preço é calculado na hora, a partir do histórico de compras do item.
 * Nada fica salvo: mudar uma constante aqui vale imediatamente para tudo.
 */

// --- Ajuste fino: mude aqui depois de usar por uma ou duas semanas ---
const val IMPACTO_COMPRA = 0.10     // cada compra encarece 10%...
const val DIAS_PARA_ESQUECER = 10   // ...e esse efeito some aos poucos em 10 dias
const val PROMO_APOS_DIAS = 5       // sem comprar por 5 dias, entra em promoção
const val PROMO_POR_DIA = 0.05      // desconto cresce 5% por dia...
const val PROMO_MAXIMA = 0.30       // ...até no máximo 30%
const val TETO = 1.8                // preço nunca passa de 1,8x o base
const val PISO = 0.7                // nem fica abaixo de 0,7x
const val ARREDONDAR_PARA = 5       // preços em múltiplos de 5
private const val JANELA_DIAS = 60  // compras mais antigas que isso só contam se não houver outras

enum class Trend { UP, DOWN, BASE }

data class PriceInfo(
    val base: Int,
    val price: Int,
    val trend: Trend,
    val reason: String,  // explicação curta para mostrar na loja
)

/**
 * base: preço base do item
 * compras: datas das compras desse item (qualquer ordem)
 * criadoEm: quando o item entrou na loja (usado se nunca foi comprado)
 * hoje: data de referência
 */
fun calcularPreco(base: Int, compras: List<LocalDate>, criadoEm: LocalDate?, hoje: LocalDate): PriceInfo {
    val diasCompras = compras.map { ChronoUnit.DAYS.between(it, hoje).toInt() }.filter { it >= 0 }

    // 1) Cada compra recente soma um efeito que enfraquece com o tempo
    var alta = 0.0
    var recentes = 0
    for (dias in diasCompras) {
        val peso = 1 - dias.toDouble() / DIAS_PARA_ESQUECER
        if (peso > 0) {
            alta += IMPACTO_COMPRA * peso
            recentes++
        }
    }

    // 2) Promoção para item esquecido
    val ultima = diasCompras.minOrNull()
    val referencia = ultima ?: ChronoUnit.DAYS.between(criadoEm ?: hoje, hoje).toInt()
    var desconto = 0.0
    if (referencia >= PROMO_APOS_DIAS) {
        desconto = min(PROMO_MAXIMA, PROMO_POR_DIA * (referencia - PROMO_APOS_DIAS + 1))
    }

    // 3) Limites e arredondamento
    val mult = max(PISO, min(TETO, 1 + alta - desconto))
    val preco = max(
        ARREDONDAR_PARA,
        roundHalfEven(base * mult / ARREDONDAR_PARA) * ARREDONDAR_PARA,
    )

    return when {
        preco > base -> {
            val vezes = if (recentes == 1) "1 vez" else "$recentes vezes"
            PriceInfo(base, preco, Trend.UP, "Comprado $vezes nos últimos $DIAS_PARA_ESQUECER dias")
        }
        preco < base -> {
            val motivo = if (ultima == null) "Promoção: nunca comprado" else "Promoção: $referencia dias sem usar"
            PriceInfo(base, preco, Trend.DOWN, motivo)
        }
        else -> PriceInfo(base, preco, Trend.BASE, "Preço normal")
    }
}

/** Preço de um item da loja hoje, a partir das compras dele no inventário. */
fun currentPrice(item: ShopItem, purchases: List<InventoryItem>, today: LocalDate): PriceInfo {
    val dates: List<LocalDateTime> = purchases.map { it.purchasedAt }
    val since = today.minusDays(JANELA_DIAS.toLong())
    var compras = dates.filter { it.toLocalDate() >= since }
    if (compras.isEmpty()) {
        // Sem compras recentes: usa a última compra de todas, se existir
        compras = listOfNotNull(dates.maxOrNull())
    }
    return calcularPreco(item.price, compras.map { it.toLocalDate() }, item.createdAt.toLocalDate(), today)
}
