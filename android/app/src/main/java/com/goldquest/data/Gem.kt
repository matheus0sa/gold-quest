package com.goldquest.data

/**
 * Joias: moedas que só vêm de atividades específicas.
 * Uma recompensa pode pedir gold + joias (ex.: GTA = 50 gold + 1 Esmeralda),
 * então gold de outras tarefas não basta: é preciso fazer a atividade que dá a joia.
 */
enum class Gem(val label: String, val plural: String) {
    DIAMANTE("Diamante", "Diamantes"),
    ESMERALDA("Esmeralda", "Esmeraldas"),
    RUBI("Rubi", "Rubis"),
    SAFIRA("Safira", "Safiras"),
    AMETISTA("Ametista", "Ametistas"),
    TOPAZIO("Topázio", "Topázios");

    fun amountLabel(amount: Int): String = "$amount ${if (amount == 1) label else plural}"
}

/** Quantidade de cada joia (custo de um item, saldo da carteira...). Só valores > 0. */
typealias GemBag = Map<Gem, Int>

/** {ESMERALDA=1, SAFIRA=2} -> "ESMERALDA:1,SAFIRA:2", na ordem do enum. */
fun encodeGems(bag: GemBag): String =
    Gem.entries.mapNotNull { gem -> bag[gem]?.takeIf { it > 0 }?.let { "${gem.name}:$it" } }.joinToString(",")

/** Inverso de [encodeGems]; ignora pedaços inválidos. */
fun decodeGems(raw: String): GemBag =
    raw.split(",").mapNotNull { part ->
        val (name, amount) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
        val gem = Gem.entries.firstOrNull { it.name == name.trim() } ?: return@mapNotNull null
        val n = amount.trim().toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        gem to n
    }.toMap()
