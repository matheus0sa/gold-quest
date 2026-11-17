package com.goldquest.domain

import com.goldquest.data.Difficulty
import com.goldquest.data.Gem
import com.goldquest.data.GemBalance
import com.goldquest.data.RecurringTask
import com.goldquest.data.ShopItem
import com.goldquest.data.Task
import com.goldquest.data.decodeGems
import com.goldquest.data.encodeGems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class GemsTest {
    private val hoje = LocalDate.of(2026, 11, 17)

    @Test
    fun custoDeJoiasIdaEVoltaNoBanco() {
        val bag = mapOf(Gem.SAFIRA to 2, Gem.ESMERALDA to 1)
        val raw = encodeGems(bag)
        assertEquals("ESMERALDA:1,SAFIRA:2", raw)  // ordem do enum
        assertEquals(bag, decodeGems(raw))
        assertEquals(emptyMap<Gem, Int>(), decodeGems(""))
        assertEquals(mapOf(Gem.RUBI to 3), decodeGems("RUBI:3,XYZ:1,SAFIRA:0,lixo"))
        assertEquals("", encodeGems(mapOf(Gem.RUBI to 0)))
    }

    @Test
    fun goldSobrandoNaoCompraSemAJoia() {
        // Jogatina de GTA = 50 gold + 1 Esmeralda
        val gta = mapOf(Gem.ESMERALDA to 1)
        val semTreino = shortfall(50, gta, gold = 500, balance = emptyMap())
        assertFalse(semTreino.isEmpty)
        assertEquals(0, semTreino.gold)
        assertEquals(mapOf(Gem.ESMERALDA to 1), semTreino.gems)
        assertEquals("1 Esmeralda", semTreino.describe())

        assertTrue(shortfall(50, gta, gold = 50, balance = mapOf(Gem.ESMERALDA to 1)).isEmpty)
    }

    @Test
    fun faltaGoldEJoiasJuntos() {
        val custo = mapOf(Gem.ESMERALDA to 1, Gem.SAFIRA to 3)
        val falta = shortfall(80, custo, gold = 60, balance = mapOf(Gem.SAFIRA to 1, Gem.RUBI to 9))
        assertEquals(20, falta.gold)
        assertEquals(mapOf(Gem.ESMERALDA to 1, Gem.SAFIRA to 2), falta.gems)
        assertEquals("20 gold, 1 Esmeralda e 2 Safiras", falta.describe())
    }

    @Test
    fun itemSoDeJoiasNaoTemPrecoDinamico() {
        val item = ShopItem(name = "Folga", price = 0, gems = mapOf(Gem.DIAMANTE to 1), createdAt = LocalDateTime.of(2026, 1, 1, 0, 0))
        val info = currentPrice(item, emptyList(), hoje)
        assertEquals(0, info.price)
        assertEquals(Trend.BASE, info.trend)
    }

    @Test
    fun saldosIgnoramJoiasZeradas() {
        val bag = listOf(GemBalance(Gem.RUBI, 2), GemBalance(Gem.SAFIRA, 0)).toBag()
        assertEquals(mapOf(Gem.RUBI to 2), bag)
        assertEquals("2 Rubis", bag.describe())
    }

    @Test
    fun tarefaDaJoiaSoQuandoTemQuantidade() {
        assertNull(Task(title = "t").gemReward())
        assertNull(Task(title = "t", gem = Gem.RUBI, gemAmount = 0).gemReward())
        assertEquals(GemReward(Gem.RUBI, 2), Task(title = "t", gem = Gem.RUBI, gemAmount = 2).gemReward())
        assertEquals("1 Topázio", GemReward(Gem.TOPAZIO, 1).toString())
    }

    @Test
    fun revisaoPagaAJoiaDoTreino() {
        val ontem = hoje.minusDays(1)
        val treino = RecurringTask(
            id = 1, title = "Treino", difficulty = Difficulty.MEDIA, weekdays = "0,1,2,3,4,5,6",
            gem = Gem.ESMERALDA, gemAmount = 1, createdAt = LocalDateTime.of(2026, 11, 1, 8, 0),
        )
        val items = buildReviewItems(ontem, ontem, listOf(treino), emptyList(), emptyList())
        assertEquals(1, items.size)
        assertEquals(GemReward(Gem.ESMERALDA, 1), items[0].gem)
    }

    @Test
    fun rascunhoValidaQuantidadeDeJoias() {
        val base = TaskDraft(title = "Treino", gem = Gem.ESMERALDA)
        assertNull(base.validate(DraftKind.TASK))
        assertEquals(1, base.savedGemAmount)
        assertEquals(0, base.copy(gem = null, gemAmount = 3).savedGemAmount)
        assertTrue(base.copy(gemAmount = 0).validate(DraftKind.TASK) != null)
        assertTrue(base.copy(gemAmount = MAX_GEMS_PER_TASK + 1).validate(DraftKind.TASK) != null)
    }
}
