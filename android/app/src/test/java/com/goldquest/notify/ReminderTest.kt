package com.goldquest.notify

import com.goldquest.domain.ReminderSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class ReminderTest {
    private val agora = LocalDateTime.of(2026, 10, 24, 15, 30)

    @Test
    fun agendaParaHojeSeOHorarioAindaNaoPassou() {
        assertEquals(Duration.ofMinutes(270), ReminderScheduler.delayUntil(20, 0, agora))
    }

    @Test
    fun agendaParaAmanhaSeOHorarioJaPassou() {
        assertEquals(Duration.ofHours(23).plusMinutes(30), ReminderScheduler.delayUntil(15, 0, agora))
        // exatamente agora também vai para amanhã
        assertEquals(Duration.ofHours(24), ReminderScheduler.delayUntil(15, 30, agora))
    }

    @Test
    fun textoDaNotificacaoListaSoOQueFalta() {
        assertEquals(
            "Falta: 1 hábito de hoje. Hora de juntar gold!",
            Notifier.describe(ReminderSummary(habits = 1, monthly = 0, tasksDue = 0)),
        )
        assertEquals(
            "Falta: 3 hábitos de hoje e 2 tarefas com prazo hoje ou atrasadas. Hora de juntar gold!",
            Notifier.describe(ReminderSummary(habits = 3, monthly = 0, tasksDue = 2)),
        )
        assertEquals(
            "Falta: 2 hábitos de hoje, 1 tarefa mensal e 1 tarefa com prazo hoje ou atrasada. Hora de juntar gold!",
            Notifier.describe(ReminderSummary(habits = 2, monthly = 1, tasksDue = 1)),
        )
    }
}
