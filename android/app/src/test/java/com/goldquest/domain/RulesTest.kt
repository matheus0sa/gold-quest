package com.goldquest.domain

import com.goldquest.data.Difficulty
import com.goldquest.data.Frequency
import com.goldquest.data.RecurringCompletion
import com.goldquest.data.RecurringTask
import com.goldquest.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RulesTest {
    private val hoje = LocalDate.of(2026, 10, 24)  // sábado

    @Test
    fun recompensaDependeDoPrazo() {
        fun reward(d: Difficulty, due: LocalDate?) = Task(title = "t", difficulty = d, dueDate = due).calculateReward(hoje)
        assertEquals(30, reward(Difficulty.MEDIA, null))
        assertEquals(36, reward(Difficulty.MEDIA, hoje))                // no prazo: +20%
        assertEquals(72, reward(Difficulty.DIFICIL, hoje.plusDays(3)))
        assertEquals(15, reward(Difficulty.MEDIA, hoje.minusDays(1)))   // atrasada: metade
        assertEquals(5, reward(Difficulty.FACIL, hoje.minusDays(1)))
    }

    @Test
    fun penalidadeEDezPorCentoDoValor() {
        assertEquals(1, penaltyFor(Difficulty.FACIL))
        assertEquals(3, penaltyFor(Difficulty.MEDIA))
        assertEquals(6, penaltyFor(Difficulty.DIFICIL))
    }

    @Test
    fun statusDoPrazo() {
        fun status(due: LocalDate?) = Task(title = "t", dueDate = due).dueStatus(hoje)
        assertNull(status(null))
        assertEquals(DueStatus.LATE, status(hoje.minusDays(1)))
        assertEquals(DueStatus.TODAY, status(hoje))
        assertEquals(DueStatus.FUTURE, status(hoje.plusDays(1)))
    }

    @Test
    fun rotuloDosDiasDaSemana() {
        fun label(days: String) = RecurringTask(title = "r", weekdays = days).daysLabel()
        assertEquals("Todo dia", label("0,1,2,3,4,5,6"))
        assertEquals("Dias úteis", label("0,1,2,3,4"))
        assertEquals("Fim de semana", label("5,6"))
        assertEquals("Seg, Qua, Sex", label("0,2,4"))
        assertEquals("1x por mês", RecurringTask(title = "r", frequency = Frequency.MONTHLY).daysLabel())
    }

    @Test
    fun recorrenteSoValeNosDiasEscolhidos() {
        val academia = RecurringTask(title = "academia", weekdays = "0,2,4")
        assertTrue(academia.isScheduled(LocalDate.of(2026, 10, 26)))   // segunda
        assertFalse(academia.isScheduled(hoje))                        // sábado
        assertFalse(RecurringTask(title = "m", frequency = Frequency.MONTHLY).isScheduled(hoje))
    }

    @Test
    fun formularioExigeDiasOuMensalParaRepetir() {
        val base = TaskDraft(title = "x")
        assertNull(base.validate(DraftKind.NEW))
        assertEquals("Escreva um título para a tarefa.", base.copy(title = " ").validate(DraftKind.NEW))
        assertEquals(
            "Escolha os dias da semana ou a opção mensal.",
            base.copy(repeat = Repeat.WEEKLY).validate(DraftKind.NEW),
        )
        assertNull(base.copy(repeat = Repeat.WEEKLY, weekdays = setOf(1)).validate(DraftKind.NEW))
        assertNull(base.copy(repeat = Repeat.MONTHLY, penalize = true).validate(DraftKind.NEW))
        assertEquals(
            "Para ter penalidade, a tarefa precisa de um prazo ou de repetição.",
            base.copy(penalize = true).validate(DraftKind.NEW),
        )
        assertNull(base.copy(penalize = true, dueDate = hoje).validate(DraftKind.TASK))
    }

    // ---------- Revisão ----------

    private fun rec(id: Long, days: String, penalize: Boolean = false, criadoEm: LocalDate = hoje.minusDays(30)) =
        RecurringTask(
            id = id, title = "rec$id", weekdays = days, penalize = penalize,
            createdAt = LocalDateTime.of(criadoEm, java.time.LocalTime.NOON),
        )

    @Test
    fun janelaDaRevisao() {
        // nunca revisou: só ontem
        assertEquals(hoje.minusDays(1) to hoje.minusDays(1), reviewWindow(null, hoje))
        // revisou ontem: nada a revisar
        assertFalse(needsReview(hoje.minusDays(1), hoje))
        // revisou há 3 dias: dos 2 dias seguintes até ontem
        assertEquals(hoje.minusDays(2) to hoje.minusDays(1), reviewWindow(hoje.minusDays(3), hoje))
        // revisou há 20 dias: limitado a 7 dias
        assertEquals(hoje.minusDays(7) to hoje.minusDays(1), reviewWindow(hoje.minusDays(20), hoje))
    }

    @Test
    fun revisaoListaRecorrentesNaoMarcadas() {
        val sexta = LocalDate.of(2026, 10, 23)
        val diario = rec(1, "0,1,2,3,4,5,6", penalize = true)
        val feito = rec(2, "0,1,2,3,4,5,6")
        val items = buildReviewItems(
            start = sexta, end = sexta,
            recurring = listOf(diario, feito),
            completions = listOf(RecurringCompletion(recurringId = 2, date = sexta, reward = 30)),
            overdue = emptyList(),
        )
        assertEquals(1, items.size)
        assertEquals("rec1", items[0].title)
        assertEquals(30, items[0].reward)
        assertEquals(3, items[0].penalty)
    }

    @Test
    fun recorrenteCriadaDepoisDoDiaNaoEntra() {
        val sexta = LocalDate.of(2026, 10, 23)
        val nova = rec(1, "0,1,2,3,4,5,6", criadoEm = hoje)
        assertTrue(buildReviewItems(sexta, sexta, listOf(nova), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun mensalFechaNoUltimoDiaDoMes() {
        val fimDeSetembro = LocalDate.of(2026, 9, 30)
        val janelas = RecurringTask(
            id = 7, title = "janelas", frequency = Frequency.MONTHLY, penalize = true,
            createdAt = LocalDateTime.of(2026, 8, 10, 12, 0),
        )
        val naoFeita = buildReviewItems(fimDeSetembro, fimDeSetembro, listOf(janelas), emptyList(), emptyList())
        assertEquals(1, naoFeita.size)
        assertEquals("📅 Mensal de setembro", naoFeita[0].detail)
        assertEquals(3, naoFeita[0].penalty)

        val feita = listOf(RecurringCompletion(recurringId = 7, date = LocalDate.of(2026, 9, 12), reward = 30))
        assertTrue(buildReviewItems(fimDeSetembro, fimDeSetembro, listOf(janelas), feita, emptyList()).isEmpty())

        // criada no próprio mês: pergunta, mas sem penalidade
        val nova = janelas.copy(createdAt = LocalDateTime.of(2026, 9, 20, 12, 0))
        val semPenalidade = buildReviewItems(fimDeSetembro, fimDeSetembro, listOf(nova), emptyList(), emptyList())
        assertEquals(0, semPenalidade.single().penalty)
    }

    @Test
    fun tarefaVencidaPagaRecompensaNoPrazo() {
        val ontem = hoje.minusDays(1)
        val task = Task(id = 5, title = "relatório", difficulty = Difficulty.DIFICIL, dueDate = ontem, penalize = true)
        val item = buildReviewItems(ontem, ontem, emptyList(), emptyList(), listOf(task)).single()
        assertEquals(72, item.reward)
        assertEquals(6, item.penalty)
        assertEquals("task-5", item.key)
    }
}
