package com.example.holdingreview.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.holdingreview.data.local.dao.DailyReviewDao
import com.example.holdingreview.data.local.dao.HoldingDao
import com.example.holdingreview.data.local.dao.QuoteSnapshotDao
import com.example.holdingreview.data.local.dao.WatchStockDao
import com.example.holdingreview.data.local.entities.DailyReviewEntity
import com.example.holdingreview.data.local.entities.HoldingEntity
import com.example.holdingreview.data.local.entities.QuoteSnapshotEntity
import com.example.holdingreview.data.local.entities.WatchStockEntity

@Database(
    entities = [
        HoldingEntity::class,
        WatchStockEntity::class,
        QuoteSnapshotEntity::class,
        DailyReviewEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class HoldingReviewDatabase : RoomDatabase() {
    abstract fun holdingDao(): HoldingDao
    abstract fun watchStockDao(): WatchStockDao
    abstract fun quoteSnapshotDao(): QuoteSnapshotDao
    abstract fun dailyReviewDao(): DailyReviewDao
}
