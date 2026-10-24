package com.goldquest.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

enum class Difficulty(val label: String, val reward: Int) {
    FACIL("Fácil", 10),
    MEDIA("Média", 30),
    DIFICIL("Difícil", 60),
}

enum class Frequency { WEEKLY, MONTHLY }

@Entity(tableName = "tag")
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

@Entity(
    tableName = "task",
    foreignKeys = [ForeignKey(Tag::class, ["id"], ["tagId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("tagId")],
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val difficulty: Difficulty = Difficulty.MEDIA,
    val dueDate: LocalDate? = null,
    val done: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val completedAt: LocalDateTime? = null,
    val rewardEarned: Int? = null,
    val penalize: Boolean = false,
    val penalizedAt: LocalDate? = null,  // quando a penalidade foi cobrada
    val tagId: Long? = null,
)

/**
 * Tarefa que se repete:
 *  - WEEKLY: em dias da semana escolhidos (ex.: academia seg/qua/sex);
 *  - MONTHLY: uma vez por mês, em qualquer dia (ex.: limpar janelas).
 */
@Entity(
    tableName = "recurring_task",
    foreignKeys = [ForeignKey(Tag::class, ["id"], ["tagId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("tagId")],
)
data class RecurringTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val difficulty: Difficulty = Difficulty.MEDIA,
    val frequency: Frequency = Frequency.WEEKLY,
    val weekdays: String = "",  // ex.: "0,2,4" (0 = segunda); vazio se mensal
    val penalize: Boolean = false,
    val tagId: Long? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

/** Cada vez que um hábito foi feito (uma linha por dia). */
@Entity(
    tableName = "recurring_completion",
    foreignKeys = [ForeignKey(RecurringTask::class, ["id"], ["recurringId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recurringId", "date", unique = true)],
)
data class RecurringCompletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recurringId: Long,
    val date: LocalDate,
    val reward: Int,
)

@Entity(tableName = "shop_item")
data class ShopItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Int,  // preço base
    val emoji: String = "🎁",
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

@Entity(
    tableName = "inventory_item",
    foreignKeys = [ForeignKey(ShopItem::class, ["id"], ["shopItemId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("shopItemId")],
)
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopItemId: Long?,
    val pricePaid: Int,
    val name: String,
    val emoji: String,
    val purchasedAt: LocalDateTime = LocalDateTime.now(),
    val usedAt: LocalDateTime? = null,
)

/** Histórico de tudo que entra (+) e sai (-) da carteira. O saldo é a soma desta tabela. */
@Entity(tableName = "wallet_transaction")
data class WalletTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Int,
    val description: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

/** Linha única (id = 1) com o último dia já revisado; null = nunca revisou. */
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    val lastReviewDate: LocalDate?,
)
