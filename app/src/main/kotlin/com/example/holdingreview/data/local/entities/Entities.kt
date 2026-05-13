package com.example.holdingreview.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 保存在设备上的组合持仓 Room 行。
 */
@Entity(
    tableName = "holdings",
    indices = [Index(value = ["symbol"], unique = true)]
)
data class HoldingEntity(
    /** 本地生成的稳定主键。 */
    @PrimaryKey val id: String,
    /** 六位股票或基金代码。 */
    val symbol: String,
    /** 面向用户的持仓名称。 */
    val name: String,
    /** 序列化后的 [com.example.holdingreview.domain.model.Market] 名称。 */
    val market: String,
    /** 持有的股票数量或基金份额。 */
    val quantity: Double,
    /** 每股或每份的平均成本价。 */
    val costPrice: Double,
    /** 没有新鲜行情时使用的手动价格。 */
    val manualCurrentPrice: Double,
    /** 用户备注或交易计划。 */
    val note: String,
    /** 最近本地更新时间，单位为 epoch 毫秒。 */
    val updatedAtMillis: Long
)

/**
 * 关注列表中跟踪股票的 Room 行。
 */
@Entity(tableName = "watch_stocks")
data class WatchStockEntity(
    /** 关注列表行使用的股票代码主键。 */
    @PrimaryKey val symbol: String,
    /** 面向用户的股票名称。 */
    val name: String,
    /** 序列化后的 [com.example.holdingreview.domain.model.Market] 名称。 */
    val market: String,
    /** 用户输入的关注该股票的原因。 */
    val reason: String,
    /** 用于给关注列表条目分组的自由标签。 */
    val tags: String,
    /** 首次加入关注列表的时间，单位为 epoch 毫秒。 */
    val watchedAtMillis: Long,
    /** 加入关注时记录的当前价。 */
    val watchBaseClose: Double?,
    /** [watchBaseClose] 对应的 yyyy-MM-dd 记录日期。 */
    val watchBaseCloseDate: String?,
    /** 最近本地更新时间，单位为 epoch 毫秒。 */
    val updatedAtMillis: Long
)

/**
 * 按代码保存的最新远程行情值 Room 行。
 */
@Entity(tableName = "quote_snapshots")
data class QuoteSnapshotEntity(
    /** 缓存行情使用的股票代码主键。 */
    @PrimaryKey val symbol: String,
    /** 行情提供方返回的展示名称。 */
    val name: String,
    /** 序列化后的 [com.example.holdingreview.domain.model.Market] 名称。 */
    val market: String,
    /** 最新成交价。 */
    val latestPrice: Double,
    /** 可用时的前收盘价。 */
    val previousClose: Double?,
    /** 日涨跌幅百分比。 */
    val changePercent: Double,
    /** 可用时的成交量。 */
    val volume: Double?,
    /** 可用时的成交额。 */
    val turnoverAmount: Double?,
    /** 可用时的换手率百分比。 */
    val turnoverRate: Double?,
    /** 可用时的日内振幅百分比。 */
    val amplitude: Double?,
    /** 该行情快照的来源系统。 */
    val source: String,
    /** 行情持久化时间，单位为 epoch 毫秒。 */
    val updatedAtMillis: Long
)

/**
 * 已保存每日复盘的 Room 行。
 */
@Entity(tableName = "daily_reviews")
data class DailyReviewEntity(
    /** yyyy-MM-dd 格式的复盘日期主键。 */
    @PrimaryKey val tradeDate: String,
    /** 已保存的复盘摘要文本。 */
    val summary: String,
    /** 已保存的 AI Prompt 文本。 */
    val aiPrompt: String,
    /** 复盘保存时间，单位为 epoch 毫秒。 */
    val createdAtMillis: Long
)

/**
 * 用户手动记录的买入/卖出操作。
 */
@Entity(
    tableName = "trade_operations",
    indices = [Index(value = ["symbol"]), Index(value = ["occurredAtMillis"])]
)
data class TradeOperationEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val side: String,
    val quantity: Double,
    val price: Double,
    val fee: Double,
    val occurredAtMillis: Long,
    val note: String,
    val realizedProfit: Double?,
    val createdAtMillis: Long
)

/**
 * 每只股票的本地监控规则配置。
 */
@Entity(tableName = "monitor_configs")
data class MonitorConfigEntity(
    @PrimaryKey val symbol: String,
    val market: String,
    val securityType: String,
    val enabled: Boolean,
    val enableCost: Boolean,
    val enableChange: Boolean,
    val enableVolume: Boolean,
    val enableMa: Boolean,
    val enableRsi: Boolean,
    val enableGap: Boolean,
    val enableTrailingStop: Boolean,
    val costProfitPercent: Double,
    val costLossPercent: Double,
    val changePercent: Double,
    val volumeSurgeMultiplier: Double,
    val volumeShrinkMultiplier: Double,
    val rsiHigh: Double,
    val rsiLow: Double,
    val gapPercent: Double,
    val trailingProfitStartPercent: Double,
    val trailingWarningDrawdownPercent: Double,
    val trailingCriticalDrawdownPercent: Double,
    val highestPrice: Double?,
    val updatedAtMillis: Long
)

/**
 * 已触发的股票监控预警记录。
 */
@Entity(
    tableName = "monitor_alerts",
    indices = [
        Index(value = ["symbol"]),
        Index(value = ["triggeredAtMillis"])
    ]
)
data class MonitorAlertEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val name: String,
    val market: String,
    val level: String,
    val type: String,
    val title: String,
    val message: String,
    val latestPrice: Double,
    val changePercent: Double,
    val triggeredAtMillis: Long,
    val isRead: Boolean
)

/**
 * 东方财富日 K 线本地缓存。
 */
@Entity(
    tableName = "kline_cache",
    primaryKeys = ["symbol", "date"],
    indices = [Index(value = ["symbol"])]
)
data class KLineCacheEntity(
    val symbol: String,
    val date: String,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
    val amount: Double,
    val updatedAtMillis: Long
)
