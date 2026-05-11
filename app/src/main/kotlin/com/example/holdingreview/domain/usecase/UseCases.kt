package com.example.holdingreview.domain.usecase

import com.example.holdingreview.data.repository.PortfolioRepository
import com.example.holdingreview.domain.model.DailyReview
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.MarketSignal
import com.example.holdingreview.domain.model.OcrHoldingDraft
import com.example.holdingreview.domain.model.PortfolioSnapshot
import com.example.holdingreview.domain.model.ReviewDraft
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

class CalculatePortfolioUseCase @Inject constructor() {
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

class RefreshQuotesUseCase @Inject constructor(
    private val repository: PortfolioRepository
) {
    suspend operator fun invoke(): Result<Int> = repository.refreshQuotes()
}

class AnalyzeMarketSignalsUseCase @Inject constructor(
    private val calculatePortfolio: CalculatePortfolioUseCase
) {
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

class ParseOcrHoldingUseCase @Inject constructor() {
    private val codeRegex = Regex("""(?<!\d)([0361568]\d{5})(?!\d)""")
    private val numberRegex = Regex("""-?\d+(?:\.\d+)?""")
    private val chineseRegex = Regex("""[\u4e00-\u9fa5A-Za-z]{2,12}""")

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

    private fun inferName(text: String, code: String): String {
        val before = text.substringBefore(code)
        val after = text.substringAfter(code, "")
        return (chineseRegex.find(before)?.value ?: chineseRegex.find(after)?.value).orEmpty()
            .replace("持仓", "")
            .replace("股票", "")
            .trim()
    }
}

class GenerateDailyReviewUseCase @Inject constructor(
    private val calculatePortfolio: CalculatePortfolioUseCase
) {
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

    fun toDailyReview(draft: ReviewDraft): DailyReview {
        return DailyReview(draft.tradeDate, draft.summary, draft.aiPrompt, System.currentTimeMillis())
    }
}

fun Double?.formatSignalValue(): String = compactNumber(this)
