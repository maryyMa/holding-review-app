package com.example.holdingreview.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.holdingreview.data.local.dao.DailyReviewDao
import com.example.holdingreview.data.local.dao.HoldingDao
import com.example.holdingreview.data.local.dao.KLineCacheDao
import com.example.holdingreview.data.local.dao.MonitorAlertDao
import com.example.holdingreview.data.local.dao.MonitorConfigDao
import com.example.holdingreview.data.local.dao.QuoteSnapshotDao
import com.example.holdingreview.data.local.dao.WatchStockDao
import com.example.holdingreview.data.local.entities.DailyReviewEntity
import com.example.holdingreview.data.local.entities.HoldingEntity
import com.example.holdingreview.data.local.entities.KLineCacheEntity
import com.example.holdingreview.data.local.entities.MonitorAlertEntity
import com.example.holdingreview.data.local.entities.MonitorConfigEntity
import com.example.holdingreview.data.local.entities.QuoteSnapshotEntity
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
        MonitorConfigEntity::class,
        MonitorAlertEntity::class,
        KLineCacheEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class HoldingReviewDatabase : RoomDatabase() {
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
