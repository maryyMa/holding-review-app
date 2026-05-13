package com.example.holdingreview.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.holdingreview.data.local.dao.DailyReviewDao
import com.example.holdingreview.data.local.dao.HoldingDao
import com.example.holdingreview.data.local.dao.KLineCacheDao
import com.example.holdingreview.data.local.dao.MonitorAlertDao
import com.example.holdingreview.data.local.dao.MonitorConfigDao
import com.example.holdingreview.data.local.dao.QuoteSnapshotDao
import com.example.holdingreview.data.local.dao.TradeOperationDao
import com.example.holdingreview.data.local.dao.WatchStockDao
import com.example.holdingreview.data.local.entities.DailyReviewEntity
import com.example.holdingreview.data.local.entities.HoldingEntity
import com.example.holdingreview.data.local.entities.KLineCacheEntity
import com.example.holdingreview.data.local.entities.MonitorAlertEntity
import com.example.holdingreview.data.local.entities.MonitorConfigEntity
import com.example.holdingreview.data.local.entities.QuoteSnapshotEntity
import com.example.holdingreview.data.local.entities.TradeOperationEntity
import com.example.holdingreview.data.local.entities.WatchStockEntity

/**
 * 管理本地组合、行情、关注列表和复盘表的 Room 数据库。
 */
@Database(
    entities = [
        HoldingEntity::class,
        WatchStockEntity::class,
        QuoteSnapshotEntity::class,
        DailyReviewEntity::class,
        TradeOperationEntity::class,
        MonitorConfigEntity::class,
        MonitorAlertEntity::class,
        KLineCacheEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class HoldingReviewDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_stocks ADD COLUMN watchedAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_stocks ADD COLUMN watchBaseClose REAL")
                db.execSQL("ALTER TABLE watch_stocks ADD COLUMN watchBaseCloseDate TEXT")
                db.execSQL("UPDATE watch_stocks SET watchedAtMillis = updatedAtMillis WHERE watchedAtMillis = 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trade_operations (
                        id TEXT NOT NULL PRIMARY KEY,
                        symbol TEXT NOT NULL,
                        side TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        price REAL NOT NULL,
                        fee REAL NOT NULL,
                        occurredAtMillis INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        realizedProfit REAL,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trade_operations_symbol ON trade_operations(symbol)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trade_operations_occurredAtMillis ON trade_operations(occurredAtMillis)")
            }
        }
    }

    /**
     * 提供已保存组合持仓的访问入口。
     */
    abstract fun holdingDao(): HoldingDao
    /**
     * 提供关注列表股票的访问入口。
     */
    abstract fun watchStockDao(): WatchStockDao
    /**
     * 提供缓存行情快照的访问入口。
     */
    abstract fun quoteSnapshotDao(): QuoteSnapshotDao
    /**
     * 提供已保存每日复盘的访问入口。
     */
    abstract fun dailyReviewDao(): DailyReviewDao
    /**
     * 提供交易操作记录的访问入口。
     */
    abstract fun tradeOperationDao(): TradeOperationDao
    /**
     * 提供股票监控配置的访问入口。
     */
    abstract fun monitorConfigDao(): MonitorConfigDao
    /**
     * 提供股票监控预警记录的访问入口。
     */
    abstract fun monitorAlertDao(): MonitorAlertDao
    /**
     * 提供日 K 线缓存的访问入口。
     */
    abstract fun kLineCacheDao(): KLineCacheDao
}
