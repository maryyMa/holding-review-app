package com.example.holdingreview.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 支持的股票市场及其腾讯行情 API 前缀。
 */
enum class Market(val displayName: String, val tencentPrefix: String) {
    SH("沪市", "sh"),
    SZ("深市", "sz"),
    UNKNOWN("待确认", "");

    /**
     * 将股票代码映射到行情提供方使用的市场。
     */
    companion object {
        /**
         * 根据常见沪深代码前缀推断市场。
         */
        fun fromSymbol(symbol: String): Market {
            val normalized = symbol.trim()
            return when {
                normalized.startsWith("60") || normalized.startsWith("68") ||
                    normalized.startsWith("51") || normalized.startsWith("56") ||
                    normalized.startsWith("58") -> SH
                normalized.startsWith("00") || normalized.startsWith("30") ||
                    normalized.startsWith("15") || normalized.startsWith("16") -> SZ
                else -> UNKNOWN
            }
        }
    }
}

/**
 * 包含行情派生指标的单个组合持仓领域模型。
 */
data class Holding(
    /** 用于编辑和删除该持仓的稳定本地标识。 */
    val id: String,
    /** 六位股票或基金代码。 */
    val symbol: String,
    /** 在组合页面展示的名称。 */
    val name: String,
    /** 用于展示和行情路由的市场。 */
    val market: Market,
    /** 持有的股票数量或基金份额。 */
    val quantity: Double,
    /** 每股或每份的平均成本价。 */
    val costPrice: Double,
    /** 最新已知价格，来自行情数据或手动输入。 */
    val currentPrice: Double,
    /** 最新行情中的日涨跌幅百分比。 */
    val dayChangePercent: Double,
    /** 用户输入的备注或交易计划。 */
    val note: String,
    /** 最近本地更新时间，单位为 epoch 毫秒。 */
    val updatedAtMillis: Long
) {
    /** 该持仓的当前市值。 */
    val marketValue: Double = quantity * currentPrice
    /** 该持仓的总成本。 */
    val costValue: Double = quantity * costPrice
    /** 买入以来的未实现盈亏。 */
    val totalProfit: Double = marketValue - costValue
    /** 相对总成本的未实现收益率。 */
    val totalProfitPercent: Double = if (costValue == 0.0) 0.0 else totalProfit / costValue * 100
    /** 根据行情涨跌幅估算的当日盈亏。 */
    val dayProfit: Double
        get() {
            if (dayChangePercent <= -100) return 0.0
            val previousPrice = currentPrice / (1 + dayChangePercent / 100)
            return (currentPrice - previousPrice) * quantity
        }
}

/**
 * 持久化前从持仓表单收集的可编辑输入。
 */
data class HoldingInput(
    /** 已有持仓 id；创建新持仓时为空。 */
    val id: String? = null,
    /** 六位股票或基金代码。 */
    val symbol: String,
    /** 用户可见的持仓名称。 */
    val name: String,
    /** 市场覆盖值，默认根据 [symbol] 推断。 */
    val market: Market = Market.fromSymbol(symbol),
    /** 用户输入的股票数量或份额。 */
    val quantity: Double,
    /** 用户输入的平均成本价。 */
    val costPrice: Double,
    /** 行情刷新数据可用前使用的手动最新价。 */
    val manualCurrentPrice: Double,
    /** 自由填写的备注或交易计划。 */
    val note: String
)

/**
 * 关注但不一定持有的股票领域模型。
 */
data class WatchStock(
    /** 六位股票或基金代码。 */
    val symbol: String,
    /** 关注股票的展示名称。 */
    val name: String,
    /** 用于行情路由和展示的市场。 */
    val market: Market,
    /** 用户填写的关注该股票的原因。 */
    val reason: String,
    /** 用于分组关注想法的自由标签。 */
    val tags: String,
    /** 用户首次加入关注列表的时间，单位为 epoch 毫秒。 */
    val watchedAtMillis: Long,
    /** 加入关注时对应交易日的收盘价；尚未能确认收盘价时为空。 */
    val watchBaseClose: Double?,
    /** [watchBaseClose] 对应的 yyyy-MM-dd 交易日。 */
    val watchBaseCloseDate: String?,
    /** 最新已知行情价格；刷新前为空。 */
    val latestPrice: Double?,
    /** 最新日涨跌幅；刷新前为空。 */
    val dayChangePercent: Double?,
    /** 最近本地更新时间，单位为 epoch 毫秒。 */
    val updatedAtMillis: Long
) {
    /** 从关注基准收盘价到最新价格的累计涨跌幅。 */
    val watchChangePercent: Double?
        get() {
            val baseClose = watchBaseClose?.takeIf { it > 0 } ?: return null
            val latest = latestPrice ?: return null
            return (latest - baseClose) / baseClose * 100
        }
}

/**
 * 用于添加或更新单个关注股票的可编辑输入。
 */
data class WatchStockInput(
    /** 六位股票或基金代码。 */
    val symbol: String,
    /** 用户提供的展示名称。 */
    val name: String,
    /** 市场覆盖值，默认根据 [symbol] 推断。 */
    val market: Market = Market.fromSymbol(symbol),
    /** 关注列表理由。 */
    val reason: String,
    /** 用户自定义标签。 */
    val tags: String
)

enum class TradeOperationSide {
    BUY,
    SELL
}

/**
 * 用户记录的一笔买入或卖出操作。
 */
data class TradeOperation(
    val id: String,
    val symbol: String,
    val side: TradeOperationSide,
    val quantity: Double,
    val price: Double,
    val fee: Double,
    val occurredAtMillis: Long,
    val note: String,
    val realizedProfit: Double?,
    val createdAtMillis: Long
) {
    val amount: Double = quantity * price
}

/**
 * 保存交易操作前收集的表单输入。
 */
data class TradeOperationInput(
    val symbol: String,
    val side: TradeOperationSide,
    val quantity: Double,
    val price: Double,
    val occurredAtMillis: Long,
    val note: String
)

/**
 * 从远程行情提供方标准化后的行情快照。
 */
data class QuoteSnapshot(
    /** 六位股票或基金代码。 */
    val symbol: String,
    /** 行情提供方返回的展示名称。 */
    val name: String,
    /** 根据行情代码推断出的市场。 */
    val market: Market,
    /** 最新成交价。 */
    val latestPrice: Double,
    /** 提供方返回的前收盘价。 */
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
    /** 便于阅读的行情来源名称。 */
    val source: String,
    /** 行情存入本地的时间，单位为 epoch 毫秒。 */
    val updatedAtMillis: Long
)

/**
 * 用于归类生成的市场信号的类型。
 */
enum class SecurityType {
    STOCK,
    ETF;

    companion object {
        fun fromSymbol(symbol: String): SecurityType {
            val normalized = symbol.trim()
            return if (
                normalized.startsWith("15") ||
                normalized.startsWith("16") ||
                normalized.startsWith("51") ||
                normalized.startsWith("56") ||
                normalized.startsWith("58")
            ) {
                ETF
            } else {
                STOCK
            }
        }
    }
}

/**
 * 单条预警的展示级别。
 */
enum class MonitorAlertLevel {
    INFO,
    WARNING,
    CRITICAL
}

/**
 * 监控规则命中的具体类型。
 */
enum class MonitorAlertType(val displayName: String) {
    COST_PROFIT("成本收益达标"),
    COST_LOSS("成本亏损扩大"),
    CHANGE_RISE("日内上涨异动"),
    CHANGE_FALL("日内下跌异动"),
    VOLUME_SURGE("成交放量"),
    VOLUME_SHRINK("成交缩量"),
    MA_GOLDEN_CROSS("均线金叉"),
    MA_DEATH_CROSS("均线死叉"),
    RSI_OVERBOUGHT("RSI 超买"),
    RSI_OVERSOLD("RSI 超卖"),
    GAP_UP("跳空高开"),
    GAP_DOWN("跳空低开"),
    TRAILING_STOP_WARNING("动态止盈回撤"),
    TRAILING_STOP_CRITICAL("动态止盈严重回撤")
}

/**
 * 东方财富日 K 线缓存和技术指标计算使用的数据点。
 */
data class KLinePoint(
    val symbol: String,
    val date: String,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Double,
    val amount: Double
)

/**
 * 监控规则使用的技术指标快照。
 */
data class TechnicalIndicators(
    val ma5: Double? = null,
    val ma10: Double? = null,
    val ma20: Double? = null,
    val previousMa5: Double? = null,
    val previousMa10: Double? = null,
    val rsi14: Double? = null,
    val volumeMa5: Double? = null,
    val gapPercent: Double? = null
)

/**
 * 每只股票的监控配置。阈值使用百分比时均按普通百分数保存，例如 15 表示 15%。
 */
data class MonitorConfig(
    val symbol: String,
    val market: Market = Market.fromSymbol(symbol),
    val securityType: SecurityType = SecurityType.fromSymbol(symbol),
    val enabled: Boolean = true,
    val enableCost: Boolean = true,
    val enableChange: Boolean = true,
    val enableVolume: Boolean = true,
    val enableMa: Boolean = true,
    val enableRsi: Boolean = true,
    val enableGap: Boolean = true,
    val enableTrailingStop: Boolean = true,
    val costProfitPercent: Double = 15.0,
    val costLossPercent: Double = -12.0,
    val changePercent: Double = if (securityType == SecurityType.ETF) 2.0 else 4.0,
    val volumeSurgeMultiplier: Double = 2.0,
    val volumeShrinkMultiplier: Double = 0.5,
    val rsiHigh: Double = 70.0,
    val rsiLow: Double = 30.0,
    val gapPercent: Double = 1.0,
    val trailingProfitStartPercent: Double = 10.0,
    val trailingWarningDrawdownPercent: Double = 5.0,
    val trailingCriticalDrawdownPercent: Double = 10.0,
    val highestPrice: Double? = null,
    val updatedAtMillis: Long = 0L
) {
    companion object {
        fun defaultFor(symbol: String, market: Market = Market.fromSymbol(symbol)): MonitorConfig {
            return MonitorConfig(
                symbol = symbol.trim(),
                market = market,
                securityType = SecurityType.fromSymbol(symbol)
            )
        }
    }
}

/**
 * 监控运行时的一只股票，来自持仓或关注列表。
 */
data class MonitorTarget(
    val symbol: String,
    val name: String,
    val market: Market,
    val securityType: SecurityType,
    val costPrice: Double?,
    val latestPrice: Double?,
    val dayChangePercent: Double?,
    val isHolding: Boolean,
    val isWatched: Boolean
)

/**
 * 已触发并持久化的股票预警。
 */
data class MonitorAlert(
    val id: String,
    val symbol: String,
    val name: String,
    val market: Market,
    val level: MonitorAlertLevel,
    val type: MonitorAlertType,
    val title: String,
    val message: String,
    val latestPrice: Double,
    val changePercent: Double,
    val triggeredAtMillis: Long,
    val isRead: Boolean
)

/**
 * 一次监控执行完成后的汇总结果。
 */
data class MonitorRunResult(
    val checkedCount: Int,
    val alertCount: Int,
    val alerts: List<MonitorAlert>,
    val failedSymbols: List<String>
)

enum class SignalType {
    /** 大幅价格波动信号。 */
    PRICE_MOVE,
    /** 异常成交量信号。 */
    VOLUME,
    /** 大幅日内振幅信号。 */
    AMPLITUDE,
    /** 异常换手信号。 */
    TURNOVER,
    /** 组合盈亏贡献信号。 */
    CONTRIBUTION,
    /** 组合集中度或其他风险信号。 */
    RISK
}

/**
 * 生成市场信号的相对重要程度。
 */
enum class SignalSeverity {
    /** 信息提示级信号。 */
    INFO,
    /** 需要复盘关注的警示信号。 */
    WARNING,
    /** 需要在界面中突出显示的强信号。 */
    STRONG
}

/**
 * 根据持仓和关注股票生成的可读观察结论。
 */
data class MarketSignal(
    /** 该信号关联的可选股票代码。 */
    val symbol: String?,
    /** 显示在列表和复盘文本中的短标题。 */
    val title: String,
    /** 说明为什么生成该信号。 */
    val description: String,
    /** 信号分类。 */
    val type: SignalType,
    /** 信号重要程度。 */
    val severity: SignalSeverity
)

/**
 * 由当前持仓推导出的组合聚合指标。
 */
data class PortfolioSnapshot(
    /** 当前持仓数量。 */
    val holdingCount: Int = 0,
    /** 当前市值合计。 */
    val marketValue: Double = 0.0,
    /** 总成本合计。 */
    val costValue: Double = 0.0,
    /** 估算当日盈亏合计。 */
    val dayProfit: Double = 0.0,
    /** 未实现盈亏合计。 */
    val totalProfit: Double = 0.0,
    /** 当日正向贡献最大的持仓。 */
    val topContributor: Holding? = null,
    /** 当日负向拖累最大的持仓。 */
    val topDrag: Holding? = null,
    /** 市值最大的持仓。 */
    val largestHolding: Holding? = null
) {
    /** 相对估算前一日组合市值的当日收益率。 */
    val dayProfitPercent: Double = if (marketValue - dayProfit == 0.0) 0.0 else dayProfit / (marketValue - dayProfit) * 100
    /** 相对总成本的累计收益率。 */
    val totalProfitPercent: Double = if (costValue == 0.0) 0.0 else totalProfit / costValue * 100
    /** 最大持仓占组合市值的百分比。 */
    val largestHoldingWeight: Double = if (largestHolding == null || marketValue == 0.0) {
        0.0
    } else {
        largestHolding.marketValue / marketValue * 100
    }
}

/**
 * 已保存的每日组合复盘及用于生成更丰富文本的 Prompt。
 */
data class DailyReview(
    /** yyyy-MM-dd 格式的复盘日期。 */
    val tradeDate: String,
    /** 生成的组合复盘摘要。 */
    val summary: String,
    /** 可复制到 AI 写作助手中的 Prompt。 */
    val aiPrompt: String,
    /** 创建时间，单位为 epoch 毫秒。 */
    val createdAtMillis: Long
)

/**
 * 根据当前组合状态生成但尚未保存的每日复盘草稿。
 */
data class ReviewDraft(
    /** yyyy-MM-dd 格式的草稿日期。 */
    val tradeDate: String,
    /** 生成的摘要文本。 */
    val summary: String,
    /** 生成的 AI Prompt 文本。 */
    val aiPrompt: String
)

/**
 * 用户确认前从 OCR 文本解析出的候选持仓。
 */
data class OcrHoldingDraft(
    /** 用于保持 UI 可编辑行稳定的草稿 id。 */
    val id: String = UUID.randomUUID().toString(),
    /** 解析出的六位股票或基金代码。 */
    val symbol: String,
    /** 解析或推断出的持仓名称。 */
    val name: String,
    /** 根据代码推断出的市场。 */
    val market: Market,
    /** 解析出的股票数量或份额。 */
    val quantity: Double,
    /** 解析出的成本价。 */
    val costPrice: Double,
    /** 解析出的最新价或截图价格。 */
    val currentPrice: Double,
    /** 带入持仓记录的草稿备注。 */
    val note: String,
    /** 根据已识别必填字段计算的简单置信度。 */
    val confidence: Float
)

/**
 * 使用复盘草稿所需的中国区域设置返回今天的日期文本。
 */
fun todayText(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
