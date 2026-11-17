package com.goldquest.domain

import com.goldquest.data.Gem
import com.goldquest.data.GemBag
import com.goldquest.data.GemBalance

const val MAX_GEMS_PER_TASK = 5   // quantas joias uma tarefa pode dar
const val MAX_GEMS_PER_ITEM = 20  // quantas de cada joia um item pode pedir

/** Saldos da consulta -> mapa só com joias positivas. */
fun List<GemBalance>.toBag(): GemBag = filter { it.amount > 0 }.associate { it.gem to it.amount }

/** "1 Esmeralda + 2 Safiras", na ordem do enum; vazio se não há joias. */
fun GemBag.describe(): String =
    Gem.entries.mapNotNull { gem -> this[gem]?.takeIf { it > 0 }?.let(gem::amountLabel) }.joinToString(" + ")

/** Joia que uma tarefa dá (null = só gold). */
data class GemReward(val gem: Gem, val amount: Int) {
    override fun toString() = gem.amountLabel(amount)
}

fun gemRewardOf(gem: Gem?, amount: Int): GemReward? =
    if (gem != null && amount > 0) GemReward(gem, amount) else null

/** O que falta para pagar um custo em gold + joias. */
data class Shortfall(val gold: Int, val gems: GemBag) {
    val isEmpty: Boolean get() = gold <= 0 && gems.isEmpty()

    /** "20 gold e 1 Esmeralda" */
    fun describe(): String {
        val parts = buildList {
            if (gold > 0) add("$gold gold")
            Gem.entries.forEach { gem -> gems[gem]?.let { add(gem.amountLabel(it)) } }
        }
        return when (parts.size) {
            0 -> ""
            1 -> parts[0]
            else -> parts.dropLast(1).joinToString(", ") + " e " + parts.last()
        }
    }
}

fun shortfall(price: Int, gems: GemBag, gold: Int, balance: GemBag): Shortfall = Shortfall(
    gold = maxOf(0, price - gold),
    gems = gems.mapNotNull { (gem, need) ->
        val missing = need - (balance[gem] ?: 0)
        if (missing > 0) gem to missing else null
    }.toMap(),
)
