package com.example.holdingreview.domain.usecase

import com.example.holdingreview.data.repository.PortfolioRepository
import com.example.holdingreview.data.repository.StockMonitorRepository
import com.example.holdingreview.domain.model.DailyReview
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.KLinePoint
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.MarketSignal
import com.example.holdingreview.domain.model.MonitorAlert
import com.example.holdingreview.domain.model.MonitorAlertLevel
import com.example.holdingreview.domain.model.MonitorAlertType
import com.example.holdingreview.domain.model.MonitorConfig
import com.example.holdingreview.domain.model.MonitorRunResult
import com.example.holdingreview.domain.model.MonitorTarget
import com.example.holdingreview.domain.model.OcrHoldingDraft
import com.example.holdingreview.domain.model.PortfolioSnapshot
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.ReviewDraft
import com.example.holdingreview.domain.model.TechnicalIndicators
import com.example.holdingreview.domain.model.SignalSeverity
import com.example.holdingreview.domain.model.SignalType
import com.example.holdingreview.domain.model.WatchStock
import com.example.holdingreview.domain.model.todayText
import com.example.holdingreview.domain.util.compactNumber
import com.example.holdingreview.domain.util.money
import com.example.holdingreview.domain.util.percent
import com.example.holdingreview.domain.util.signedMoney
import com.example.holdingreview.domain.util.signedPercent
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import java.util.UUID

/**
 * 根据当前持仓列表构建组合聚合指标。
 */
class CalculatePortfolioUseCase @Inject constructor() {
    /**
     * 计算总额、盈亏贡献和集中度指标。
     */
    operator fun invoke(holdings: List<Holding>): PortfolioSnapshot {
        return PortfolioSnapshot(
            holdingCount = holdings.size,
            marketValue = holdings.sumOf { it.marketValue },
            costValue = holdings.sumOf { it.costValue },
            dayProfit = holdings.sumOf { it.dayProfit },
            totalProfit = holdings.sumOf { it.totalProfit },
            topContributor = holdings.maxByOrNull { it.dayProfit },
            topDrag = holdings.minByOrNull { it.dayProfit },
            largestHolding = holdings.maxByOrNull { it.marketValue }
        )
    }
}

/**
 * 通过仓库边界刷新行情数据。
 */
class RefreshQuotesUseCase @Inject constructor(
    /** 负责本地持久化和远程行情刷新的仓库。 */
    private val repository: PortfolioRepository
) {
    /**
     * 执行行情刷新并返回更新的行情数量。
     */
    suspend operator fun invoke(): Result<Int> = repository.refreshQuotes()
}

/**
 * 查询单只股票行情，用于新增/编辑持仓时自动补全名称和现价。
 */
class LookupQuoteUseCase @Inject constructor(
    /** 负责执行远程行情查询并缓存结果的仓库。 */
    private val repository: PortfolioRepository
) {
    /**
     * 根据股票代码查询最新行情。
     */
    suspend operator fun invoke(symbol: String): Result<QuoteSnapshot> = repository.lookupQuote(symbol)
}

/**
 * 根据用户输入、股票代码和股票名称推断关注股票所属行业。
 */
class InferIndustryUseCase @Inject constructor() {
    /**
     * 用户填写行业时优先保留；否则按常见代码和名称关键词推断。
     */
    operator fun invoke(symbol: String, name: String, manualIndustry: String): String {
        val trimmedIndustry = manualIndustry.trim()
        if (trimmedIndustry.isNotBlank()) return trimmedIndustry

        val normalizedSymbol = symbol.trim()
        val normalizedName = name.trim()
        codeIndustries[normalizedSymbol]?.let { return it }

        keywordIndustries.firstOrNull { (keywords, _) ->
            keywords.any { normalizedName.contains(it) }
        }?.let { return it.second }

        return "其他"
    }

    private companion object {
        val codeIndustries = mapOf(
            "600519" to "白酒",
            "688981" to "半导体",
            "000001" to "银行"
        )

        val keywordIndustries = listOf(
            listOf("银行", "证券", "保险") to "金融",
            listOf("半导体", "芯片", "电子") to "半导体",
            listOf("医药", "医疗", "生物") to "医药",
            listOf("白酒", "食品", "饮料") to "消费",
            listOf("新能源", "电池", "光伏") to "新能源",
            listOf("汽车") to "汽车",
            listOf("地产", "物业") to "房地产",
            listOf("煤", "油", "气") to "能源",
            listOf("通信", "电信") to "通信",
            listOf("软件", "信息", "数据", "互联网") to "科技",
            listOf("钢铁", "有色", "矿") to "周期",
            listOf("电力", "水电", "核电") to "电力公用",
            listOf("军工", "航天", "航空", "船舶") to "国防军工"
        )
    }
}

/**
 * 根据价格波动、盈亏贡献和集中度生成复盘信号。
 */
class AnalyzeMarketSignalsUseCase @Inject constructor(
    /** 用于推导贡献和权重上下文的组合计算器。 */
    private val calculatePortfolio: CalculatePortfolioUseCase
) {
    /**
     * 为持仓和关注股票生成面向用户的信号。
     */
    operator fun invoke(holdings: List<Holding>, watchStocks: List<WatchStock>): List<MarketSignal> {
        val signals = mutableListOf<MarketSignal>()
        val snapshot = calculatePortfolio(holdings)

        holdings.forEach { holding ->
            if (abs(holding.dayChangePercent) >= 5) {
                signals += MarketSignal(
                    symbol = holding.symbol,
                    title = "${holding.name} 价格异动",
                    description = "今日涨跌幅 ${signedPercent(holding.dayChangePercent)}，对组合贡献 ${signedMoney(holding.dayProfit)}。",
                    type = SignalType.PRICE_MOVE,
                    severity = if (abs(holding.dayChangePercent) >= 8) SignalSeverity.STRONG else SignalSeverity.WARNING
                )
            }
        }

        watchStocks.forEach { stock ->
            val changePercent = stock.dayChangePercent ?: return@forEach
            if (abs(changePercent) >= 5) {
                signals += MarketSignal(
                    symbol = stock.symbol,
                    title = "${stock.name} 关注股异动",
                    description = "关注列表股票今日涨跌幅 ${signedPercent(changePercent)}，原因：${stock.reason.ifBlank { "未填写" }}。",
                    type = SignalType.PRICE_MOVE,
                    severity = SignalSeverity.WARNING
                )
            }
        }

        snapshot.topContributor?.let {
            if (it.dayProfit > 0) {
                signals += MarketSignal(
                    symbol = it.symbol,
                    title = "主要贡献",
                    description = "${it.name} 今日贡献 ${signedMoney(it.dayProfit)}，收益来自 ${signedPercent(it.dayChangePercent)} 的价格变化。",
                    type = SignalType.CONTRIBUTION,
                    severity = SignalSeverity.INFO
                )
            }
        }

        snapshot.topDrag?.let {
            if (it.dayProfit < 0) {
                signals += MarketSignal(
                    symbol = it.symbol,
                    title = "主要拖累",
                    description = "${it.name} 今日拖累 ${signedMoney(it.dayProfit)}，需要复盘是否仍符合持仓逻辑。",
                    type = SignalType.CONTRIBUTION,
                    severity = SignalSeverity.WARNING
                )
            }
        }

        snapshot.largestHolding?.let {
            if (snapshot.largestHoldingWeight >= 45) {
                signals += MarketSignal(
                    symbol = it.symbol,
                    title = "仓位集中",
                    description = "${it.name} 仓位占比约 ${percent(snapshot.largestHoldingWeight)}，需要关注单一持仓波动。",
                    type = SignalType.RISK,
                    severity = SignalSeverity.WARNING
                )
            }
        }
        return signals
    }
}

/**
 * 将 OCR 文本解析为用户保存前可确认的候选持仓。
 */
class ParseOcrHoldingUseCase @Inject constructor() {
    /** 匹配 OCR 文本中的六位 A 股风格代码。 */
    private val codeRegex = Regex("""(?<!\d)([0361568]\d{5})(?!\d)""")
    /** 匹配用于数量和价格的带符号整数及小数。 */
    private val numberRegex = Regex("""-?\d+(?:\.\d+)?""")
    /** 匹配检测到的代码附近可能的股票名称。 */
    private val chineseRegex = Regex("""[\u4e00-\u9fa5A-Za-z]{2,12}""")

    /**
     * 为每个不同的检测代码提取一条候选持仓。
     */
    operator fun invoke(rawText: String): List<OcrHoldingDraft> {
        val lines = rawText.replace("｜", " ").replace("|", " ").lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val drafts = mutableListOf<OcrHoldingDraft>()

        lines.forEachIndexed { index, line ->
            val code = codeRegex.find(line)?.value ?: return@forEachIndexed
            val window = listOfNotNull(line, lines.getOrNull(index + 1)).joinToString(" ")
            val name = inferName(window, code)
            val numbers = numberRegex.findAll(window)
                .map { it.value }
                .filterNot { it == code }
                .mapNotNull { it.toDoubleOrNull() }
                .toList()

            val quantity = numbers.firstOrNull { it >= 10 && it % 1.0 == 0.0 } ?: 0.0
            val prices = numbers.filter { it > 0 && it < 10000 && it != quantity }
            val costPrice = prices.getOrNull(0) ?: 0.0
            val currentPrice = prices.getOrNull(1) ?: costPrice
            val confidence = listOf(code.isNotBlank(), name.isNotBlank(), quantity > 0, costPrice > 0, currentPrice > 0)
                .count { it } / 5f

            drafts += OcrHoldingDraft(
                symbol = code,
                name = name.ifBlank { "待确认" },
                market = Market.fromSymbol(code),
                quantity = quantity,
                costPrice = costPrice,
                currentPrice = currentPrice,
                note = "OCR 导入，保存前已确认。",
                confidence = confidence
            )
        }
        return drafts.distinctBy { it.symbol }
    }

    /**
     * 根据检测代码周围的文本推断股票名称。
     */
    private fun inferName(text: String, code: String): String {
        val before = text.substringBefore(code)
        val after = text.substringAfter(code, "")
        return (chineseRegex.find(before)?.value ?: chineseRegex.find(after)?.value).orEmpty()
            .replace("持仓", "")
            .replace("股票", "")
            .trim()
    }
}

/**
 * 根据组合状态创建每日复盘草稿和可复用 AI Prompt。
 */
class GenerateDailyReviewUseCase @Inject constructor(
    /** 用于填充复盘指标的组合计算器。 */
    private val calculatePortfolio: CalculatePortfolioUseCase
) {
    /**
     * 构建当天的复盘摘要和 Prompt。
     */
    operator fun invoke(holdings: List<Holding>, signals: List<MarketSignal>): ReviewDraft {
        val snapshot = calculatePortfolio(holdings)
        val date = todayText()
        val summary = buildString {
            append(date).append(" 持仓复盘\n\n")
            append("今日持仓 ").append(snapshot.holdingCount).append(" 只，")
            append("总市值 ").append(money(snapshot.marketValue)).append("。")
            append("当日盈亏 ").append(signedMoney(snapshot.dayProfit))
                .append("（").append(signedPercent(snapshot.dayProfitPercent)).append("），")
            append("累计盈亏 ").append(signedMoney(snapshot.totalProfit))
                .append("（").append(signedPercent(snapshot.totalProfitPercent)).append("）。\n\n")

            snapshot.topContributor?.let {
                append("主要贡献：").append(it.name).append("（").append(it.symbol).append("），")
                    .append("当日贡献 ").append(signedMoney(it.dayProfit)).append("。\n")
            }
            snapshot.topDrag?.let {
                append("主要拖累：").append(it.name).append("（").append(it.symbol).append("），")
                    .append("当日影响 ").append(signedMoney(it.dayProfit)).append("。\n")
            }
            if (signals.isNotEmpty()) {
                append("异动观察：")
                append(signals.take(3).joinToString("；") { it.title + "，" + it.description })
                append("\n")
            }
            append("明日计划：")
            append(
                if (snapshot.dayProfit >= 0) {
                    "保留盈利仓位的观察节奏，不因单日上涨随意追高。"
                } else {
                    "优先检查拖累持仓是否跌破原有逻辑，避免情绪化补仓。"
                }
            )
        }

        val aiPrompt = buildString {
            append("你是一名理性、克制的投资复盘助手。请基于下面数据，写一段约 300 字中文复盘，风格轻松但不夸张，不构成投资建议。\n")
            append("组合：总市值 ").append(money(snapshot.marketValue))
                .append("，当日盈亏 ").append(signedMoney(snapshot.dayProfit))
                .append("，累计盈亏 ").append(signedMoney(snapshot.totalProfit)).append("。\n")
            append("持仓：\n")
            holdings.forEach {
                append("- ").append(it.name).append("（").append(it.symbol).append("）：")
                    .append("现价 ").append(money(it.currentPrice))
                    .append("，涨跌 ").append(signedPercent(it.dayChangePercent))
                    .append("，市值 ").append(money(it.marketValue))
                    .append("，当日贡献 ").append(signedMoney(it.dayProfit))
                    .append("，备注：").append(it.note).append("\n")
            }
            append("异动：\n")
            signals.forEach { append("- ").append(it.title).append("：").append(it.description).append("\n") }
        }
        return ReviewDraft(date, summary, aiPrompt)
    }

    /**
     * 将可编辑草稿转换为带时间戳的复盘实体。
     */
    fun toDailyReview(draft: ReviewDraft): DailyReview {
        return DailyReview(draft.tradeDate, draft.summary, draft.aiPrompt, System.currentTimeMillis())
    }
}

/**
 * 格式化可选信号值，用于紧凑指标行展示。
 */
/**
 * 计算股票监控需要的均线、RSI、均量和跳空指标。
 */
class CalculateTechnicalIndicatorsUseCase @Inject constructor() {
    operator fun invoke(kLines: List<KLinePoint>): TechnicalIndicators {
        val sorted = kLines.sortedBy { it.date }
        val closes = sorted.map { it.close }
        val ma5 = closes.movingAverage(5)
        val ma10 = closes.movingAverage(10)
        val ma20 = closes.movingAverage(20)
        val previousMa5 = closes.dropLast(1).movingAverage(5)
        val previousMa10 = closes.dropLast(1).movingAverage(10)
        val volumeWindow = if (sorted.size >= 6) sorted.dropLast(1).takeLast(5) else sorted.takeLast(5)
        val volumeMa5 = volumeWindow.takeIf { it.size >= 5 }?.map { it.volume }?.average()
        val gapPercent = calculateGapPercent(sorted)
        return TechnicalIndicators(
            ma5 = ma5,
            ma10 = ma10,
            ma20 = ma20,
            previousMa5 = previousMa5,
            previousMa10 = previousMa10,
            rsi14 = calculateRsi(closes),
            volumeMa5 = volumeMa5,
            gapPercent = gapPercent
        )
    }

    private fun List<Double>.movingAverage(size: Int): Double? {
        if (this.size < size) return null
        return takeLast(size).average()
    }

    private fun calculateRsi(closes: List<Double>, period: Int = 14): Double? {
        if (closes.size <= period) return null
        val window = closes.takeLast(period + 1)
        var gains = 0.0
        var losses = 0.0
        for (index in 1 until window.size) {
            val change = window[index] - window[index - 1]
            if (change >= 0) gains += change else losses += abs(change)
        }
        if (losses == 0.0) return 100.0
        val rs = (gains / period) / (losses / period)
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun calculateGapPercent(kLines: List<KLinePoint>): Double? {
        if (kLines.size < 2) return null
        val previous = kLines[kLines.lastIndex - 1]
        val latest = kLines.last()
        return when {
            previous.high > 0 && latest.open > previous.high -> (latest.open - previous.high) / previous.high * 100
            previous.low > 0 && latest.open < previous.low -> (latest.open - previous.low) / previous.low * 100
            else -> 0.0
        }
    }
}

/**
 * 根据行情、K 线和监控配置判断某只股票是否触发预警。
 */
class EvaluateStockAlertsUseCase @Inject constructor(
    private val calculateTechnicalIndicators: CalculateTechnicalIndicatorsUseCase
) {
    operator fun invoke(
        target: MonitorTarget,
        quote: QuoteSnapshot,
        config: MonitorConfig,
        kLines: List<KLinePoint>,
        highestPrice: Double,
        now: Long = System.currentTimeMillis()
    ): List<MonitorRuleHit> {
        val indicators = calculateTechnicalIndicators(kLines)
        val hits = mutableListOf<MonitorRuleHit>()
        val costPrice = target.costPrice
        if (config.enableCost && costPrice != null && costPrice > 0) {
            val profitPercent = (quote.latestPrice - costPrice) / costPrice * 100
            if (profitPercent >= config.costProfitPercent) {
                hits += MonitorRuleHit(
                    type = MonitorAlertType.COST_PROFIT,
                    weight = 2,
                    forcedLevel = MonitorAlertLevel.WARNING,
                    message = "相对成本收益 ${signedPercent(profitPercent)}，已达到 ${percent(config.costProfitPercent)} 观察线"
                )
            }
            if (profitPercent <= config.costLossPercent) {
                hits += MonitorRuleHit(
                    type = MonitorAlertType.COST_LOSS,
                    weight = 3,
                    forcedLevel = MonitorAlertLevel.WARNING,
                    message = "相对成本收益 ${signedPercent(profitPercent)}，已跌破 ${percent(config.costLossPercent)} 风险线"
                )
            }
        }

        if (config.enableChange) {
            val threshold = config.changePercent
            if (quote.changePercent >= threshold) {
                hits += MonitorRuleHit(
                    type = MonitorAlertType.CHANGE_RISE,
                    weight = 2,
                    message = "日内上涨 ${signedPercent(quote.changePercent)}，超过 ${percent(threshold)}"
                )
            } else if (quote.changePercent <= -threshold) {
                hits += MonitorRuleHit(
                    type = MonitorAlertType.CHANGE_FALL,
                    weight = 2,
                    message = "日内下跌 ${signedPercent(quote.changePercent)}，超过 ${percent(threshold)}"
                )
            }
        }

        val latestVolume = quote.volume
        val volumeMa5 = indicators.volumeMa5
        if (config.enableVolume && latestVolume != null && volumeMa5 != null && volumeMa5 > 0) {
            val volumeRatio = latestVolume / volumeMa5
            if (volumeRatio >= config.volumeSurgeMultiplier) {
                hits += MonitorRuleHit(
                    type = MonitorAlertType.VOLUME_SURGE,
                    weight = 2,
                    message = "成交量约为 5 日均量 ${numberText(volumeRatio)} 倍，出现放量"
                )
            } else if (volumeRatio <= config.volumeShrinkMultiplier) {
                hits += MonitorRuleHit(
                    type = MonitorAlertType.VOLUME_SHRINK,
                    weight = 1,
                    message = "成交量约为 5 日均量 ${numberText(volumeRatio)} 倍，出现缩量"
                )
            }
        }

        if (config.enableMa) {
            val ma5 = indicators.ma5
            val ma10 = indicators.ma10
            val previousMa5 = indicators.previousMa5
            val previousMa10 = indicators.previousMa10
            if (ma5 != null && ma10 != null && previousMa5 != null && previousMa10 != null) {
                if (previousMa5 <= previousMa10 && ma5 > ma10) {
                    hits += MonitorRuleHit(
                        type = MonitorAlertType.MA_GOLDEN_CROSS,
                        weight = 2,
                        message = "MA5 上穿 MA10，形成短线金叉"
                    )
                } else if (previousMa5 >= previousMa10 && ma5 < ma10) {
                    hits += MonitorRuleHit(
                        type = MonitorAlertType.MA_DEATH_CROSS,
                        weight = 2,
                        message = "MA5 下穿 MA10，形成短线死叉"
                    )
                }
            }
        }

        val rsi14 = indicators.rsi14
        if (config.enableRsi && rsi14 != null) {
            if (rsi14 >= config.rsiHigh) {
                hits += MonitorRuleHit(
                    type = MonitorAlertType.RSI_OVERBOUGHT,
                    weight = 2,
                    message = "RSI14 为 ${numberText(rsi14)}，进入超买区"
                )
            } else if (rsi14 <= config.rsiLow) {
                hits += MonitorRuleHit(
                    type = MonitorAlertType.RSI_OVERSOLD,
                    weight = 2,
                    message = "RSI14 为 ${numberText(rsi14)}，进入超卖区"
                )
            }
        }

        val gapPercent = indicators.gapPercent
        if (config.enableGap && gapPercent != null) {
            if (gapPercent >= config.gapPercent) {
                hits += MonitorRuleHit(
                    type = MonitorAlertType.GAP_UP,
                    weight = 2,
                    message = "开盘相对前一日高点跳空 ${signedPercent(gapPercent)}"
                )
            } else if (gapPercent <= -config.gapPercent) {
                hits += MonitorRuleHit(
                    type = MonitorAlertType.GAP_DOWN,
                    weight = 2,
                    message = "开盘相对前一日低点跳空 ${signedPercent(gapPercent)}"
                )
            }
        }

        if (config.enableTrailingStop && costPrice != null && costPrice > 0 && highestPrice > 0) {
            val profitPercent = (highestPrice - costPrice) / costPrice * 100
            val drawdownPercent = (highestPrice - quote.latestPrice) / highestPrice * 100
            if (profitPercent >= config.trailingProfitStartPercent) {
                if (drawdownPercent >= config.trailingCriticalDrawdownPercent) {
                    hits += MonitorRuleHit(
                        type = MonitorAlertType.TRAILING_STOP_CRITICAL,
                        weight = 3,
                        forcedLevel = MonitorAlertLevel.CRITICAL,
                        message = "盈利启动后从高点回撤 ${percent(drawdownPercent)}，达到严重回撤线"
                    )
                } else if (drawdownPercent >= config.trailingWarningDrawdownPercent) {
                    hits += MonitorRuleHit(
                        type = MonitorAlertType.TRAILING_STOP_WARNING,
                        weight = 2,
                        forcedLevel = MonitorAlertLevel.WARNING,
                        message = "盈利启动后从高点回撤 ${percent(drawdownPercent)}，达到动态止盈观察线"
                    )
                }
            }
        }

        return hits.map { it.copy(triggeredAtMillis = now) }
    }

    fun levelFor(hits: List<MonitorRuleHit>): MonitorAlertLevel {
        val forced = hits.mapNotNull { it.forcedLevel }.maxByOrNull { it.ordinal }
        val totalWeight = hits.sumOf { it.weight }
        val computed = when {
            totalWeight >= 5 || hits.size >= 3 -> MonitorAlertLevel.CRITICAL
            totalWeight >= 3 || hits.size >= 2 -> MonitorAlertLevel.WARNING
            else -> MonitorAlertLevel.INFO
        }
        return listOfNotNull(forced, computed).maxBy { it.ordinal }
    }

    private fun numberText(value: Double): String {
        return "%.2f".format(java.util.Locale.CHINA, value)
    }
}

/**
 * 单条规则命中的中间结果，运行用例会将同一只股票的命中合并成一条预警。
 */
data class MonitorRuleHit(
    val type: MonitorAlertType,
    val weight: Int,
    val message: String,
    val forcedLevel: MonitorAlertLevel? = null,
    val triggeredAtMillis: Long = 0L
)

/**
 * 拉取行情和 K 线，执行监控规则，并保存新的预警记录。
 */
class RunStockMonitorUseCase @Inject constructor(
    private val repository: StockMonitorRepository,
    private val evaluateStockAlerts: EvaluateStockAlertsUseCase
) {
    suspend operator fun invoke(): Result<MonitorRunResult> = runCatching {
        val targets = repository.getTargets()
        if (targets.isEmpty()) {
            return@runCatching MonitorRunResult(checkedCount = 0, alertCount = 0, alerts = emptyList(), failedSymbols = emptyList())
        }
        val quotes = repository.fetchQuotes(targets.map { it.symbol }).getOrThrow().associateBy { it.symbol }
        val now = System.currentTimeMillis()
        val recentCutoff = now - 30 * 60 * 1000L
        val alerts = mutableListOf<MonitorAlert>()
        val failedSymbols = mutableListOf<String>()

        targets.forEach { target ->
            val quote = quotes[target.symbol]
            if (quote == null) {
                failedSymbols += target.symbol
                return@forEach
            }
            val config = repository.getConfig(target.symbol, target.market)
                .let { if (it.securityType != target.securityType || it.market == Market.UNKNOWN) it.copy(securityType = target.securityType, market = target.market) else it }
            if (!config.enabled) return@forEach

            val highestPrice = max(config.highestPrice ?: quote.latestPrice, quote.latestPrice)
            if (highestPrice != (config.highestPrice ?: 0.0)) {
                repository.updateHighestPrice(target.symbol, highestPrice)
            }

            val kLines = repository.fetchKLines(target.symbol, target.market, 30)
                .getOrElse {
                    failedSymbols += target.symbol
                    emptyList()
                }
            val hits = evaluateStockAlerts(
                target = target,
                quote = quote,
                config = config.copy(highestPrice = highestPrice),
                kLines = kLines,
                highestPrice = highestPrice,
                now = now
            )
            val newHits = hits.filter { hit ->
                !repository.hasRecentAlert(target.symbol, hit.type, recentCutoff)
            }
            if (newHits.isNotEmpty()) {
                val level = evaluateStockAlerts.levelFor(newHits)
                alerts += MonitorAlert(
                    id = UUID.randomUUID().toString(),
                    symbol = target.symbol,
                    name = quote.name.ifBlank { target.name },
                    market = quote.market.takeUnless { it == Market.UNKNOWN } ?: target.market,
                    level = level,
                    type = newHits.maxBy { it.weight }.type,
                    title = "${quote.name.ifBlank { target.name }}触发${level.displayText()}预警",
                    message = newHits.joinToString("\n") { "• ${it.type.displayName}：${it.message}" },
                    latestPrice = quote.latestPrice,
                    changePercent = quote.changePercent,
                    triggeredAtMillis = now,
                    isRead = false
                )
            }
        }

        repository.insertAlerts(alerts)
        MonitorRunResult(
            checkedCount = targets.size,
            alertCount = alerts.size,
            alerts = alerts,
            failedSymbols = failedSymbols.distinct()
        )
    }

    private fun MonitorAlertLevel.displayText(): String {
        return when (this) {
            MonitorAlertLevel.INFO -> "提示"
            MonitorAlertLevel.WARNING -> "警告"
            MonitorAlertLevel.CRITICAL -> "严重"
        }
    }
}

fun Double?.formatSignalValue(): String = compactNumber(this)
