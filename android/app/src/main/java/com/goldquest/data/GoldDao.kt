package com.goldquest.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface GoldDao {
    // ---------- Tags ----------
    @Query("SELECT * FROM tag ORDER BY name COLLATE NOCASE")
    fun tags(): Flow<List<Tag>>

    @Query("SELECT * FROM tag")
    suspend fun tagsOnce(): List<Tag>

    @Insert
    suspend fun insert(tag: Tag): Long

    @Query("DELETE FROM tag WHERE id = :id")
    suspend fun deleteTag(id: Long)

    // ---------- Tarefas ----------
    @Query("SELECT * FROM task WHERE done = 0 ORDER BY dueDate IS NULL, dueDate, createdAt")
    fun pendingTasks(): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE done = 1 ORDER BY completedAt DESC LIMIT 100")
    fun doneTasks(): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE done = 0 AND dueDate <= :day")
    suspend fun dueTasksUntil(day: LocalDate): List<Task>

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun task(id: Long): Task?

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun deleteTask(id: Long)

    /** Pendentes cujo prazo caiu no intervalo e que ainda não foram penalizadas. */
    @Query(
        "SELECT * FROM task WHERE done = 0 AND penalizedAt IS NULL " +
            "AND dueDate BETWEEN :start AND :end"
    )
    suspend fun overdueTasks(start: LocalDate, end: LocalDate): List<Task>

    // ---------- Recorrentes ----------
    @Query("SELECT * FROM recurring_task ORDER BY createdAt")
    fun recurring(): Flow<List<RecurringTask>>

    @Query("SELECT * FROM recurring_task")
    suspend fun recurringOnce(): List<RecurringTask>

    @Query("SELECT * FROM recurring_task WHERE id = :id")
    suspend fun recurring(id: Long): RecurringTask?

    @Insert
    suspend fun insert(task: RecurringTask): Long

    @Update
    suspend fun update(task: RecurringTask)

    @Query("DELETE FROM recurring_task WHERE id = :id")
    suspend fun deleteRecurring(id: Long)

    @Query("SELECT * FROM recurring_completion WHERE date >= :since")
    fun completionsSince(since: LocalDate): Flow<List<RecurringCompletion>>

    @Query("SELECT * FROM recurring_completion WHERE date BETWEEN :start AND :end")
    suspend fun completionsBetween(start: LocalDate, end: LocalDate): List<RecurringCompletion>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(completion: RecurringCompletion): Long

    // ---------- Loja e inventário ----------
    @Query("SELECT * FROM shop_item ORDER BY price")
    fun shopItems(): Flow<List<ShopItem>>

    @Query("SELECT COUNT(*) FROM shop_item")
    suspend fun shopItemCount(): Int

    @Query("SELECT * FROM shop_item WHERE id = :id")
    suspend fun shopItem(id: Long): ShopItem?

    @Insert
    suspend fun insert(item: ShopItem): Long

    @Query("DELETE FROM shop_item WHERE id = :id")
    suspend fun deleteShopItem(id: Long)

    @Query("SELECT * FROM inventory_item")
    fun inventory(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_item WHERE shopItemId = :shopItemId")
    suspend fun purchasesOf(shopItemId: Long): List<InventoryItem>

    @Query("SELECT * FROM inventory_item WHERE id = :id")
    suspend fun inventoryItem(id: Long): InventoryItem?

    @Insert
    suspend fun insert(item: InventoryItem): Long

    @Update
    suspend fun update(item: InventoryItem)

    // ---------- Carteira ----------
    @Insert
    suspend fun insert(transaction: WalletTransaction)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction")
    fun gold(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction")
    suspend fun goldOnce(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction WHERE amount > 0")
    fun totalEarned(): Flow<Int>

    @Query("SELECT COALESCE(-SUM(amount), 0) FROM wallet_transaction WHERE amount < 0")
    fun totalSpent(): Flow<Int>

    @Query("SELECT * FROM wallet_transaction ORDER BY createdAt DESC, id DESC LIMIT 100")
    fun recentTransactions(): Flow<List<WalletTransaction>>

    // ---------- Configurações ----------
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settings(): Settings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: Settings)
}
