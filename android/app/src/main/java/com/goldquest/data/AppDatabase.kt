package com.goldquest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Tag::class,
        Task::class,
        RecurringTask::class,
        RecurringCompletion::class,
        ShopItem::class,
        InventoryItem::class,
        WalletTransaction::class,
        Settings::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): GoldDao

    companion object {
        /**
         * v3 (joias): recomeço do zero, sem migração. Qualquer banco de versão anterior
         * é apagado e recriado ao abrir o app.
         */
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gold_quest.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
