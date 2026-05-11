package com.example.holdingreview.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class Market(val displayName: String, val tencentPrefix: String) {
    SH("沪市", "sh"),
    SZ("深市", "sz"),
    UNKNOWN("待确认", "");

    companion object {
        fun fromSymbol(symbol: String): Market {
            val normalized = symbol.trim()
            return when {
                normalized.startsWith("60") || normalized.startsWith("68") || normalized.startsWith("51") -> SH
                normalized.startsWith("00") || normalized.startsWith("30") || normalized.startsWith("15") -> SZ
                else -> UNKNOWN
            }
        }
    }
}

data class Holding(
    val id: String,
    val symbol: String,
    val name: String,
    val market: Market,
    val quantity: Double,
    val costPrice: Double,
    val currentPrice: Double,
    val dayChangePercent: Double,
    val note: String,
    val updatedAtMillis: Long
) {
    val marketValue: Double = quantity * currentPrice
    val costValue: Double = quantity * costPrice
    val totalProfit: Double = marketValue - costValue
    val totalProfitPercent: Double = if (costValue == 0.0) 0.0 else totalProfit / costValue * 100
    val dayProfit: Double
        get() {
            if (dayChangePercent <= -100) return 0.0
            val previousPrice = currentPrice / (1 + dayChangePercent / 100)
            return (currentPrice - previousPrice) * quantity
        }
}

data class HoldingInput(
    val id: String? = null,
    val symbol: String,
    val name: String,
    val market: Market = Market.fromSymbol(symbol),
    val quantity: Double,
    val costPrice: Double,
    val manualCurrentPrice: Double,
    val note: String
)

data class WatchStock(
    val symbol: String,
    val name: String,
    val market: Market,
    val reason: String,
    val tags: String,
    val latestPrice: Double?,
    val dayChangePercent: Double?,
    val updatedAtMillis: Long
)

data class WatchStockInput(
    val symbol: String,
    val name: String,
    val market: Market = Market.fromSymbol(symbol),
    val reason: String,
    val tags: String
)

data class QuoteSnapshot(
    val symbol: String,
    val name: String,
    val market: Market,
    val latestPrice: Double,
    val previousClose: Double?,
    val changePercent: Double,
    val volume: Double?,
    val turnoverAmount: Double?,
    val turnoverRate: Double?,
    val amplitude: Double?,
    val source: String,
    val updatedAtMillis: Long
)

enum class SignalType {
    PRICE_MOVE,
    VOLUME,
    AMPLITUDE,
    TURNOVER,
    CONTRIBUTION,
    RISK
}

enum class SignalSeverity {
    INFO,
    WARNING,
    STRONG
}

data class MarketSignal(
    val symbol: String?,
    val title: String,
    val description: String,
    val type: SignalType,
    val severity: SignalSeverity
)

data class PortfolioSnapshot(
    val holdingCount: Int = 0,
    val marketValue: Double = 0.0,
    val costValue: Double = 0.0,
    val dayProfit: Double = 0.0,
    val totalProfit: Double = 0.0,
    val topContributor: Holding? = null,
    val topDrag: Holding? = null,
    val largestHolding: Holding? = null
) {
    val dayProfitPercent: Double = if (marketValue - dayProfit == 0.0) 0.0 else dayProfit / (marketValue - dayProfit) * 100
    val totalProfitPercent: Double = if (costValue == 0.0) 0.0 else totalProfit / costValue * 100
    val largestHoldingWeight: Double = if (largestHolding == null || marketValue == 0.0) {
        0.0
    } else {
        largestHolding.marketValue / marketValue * 100
    }
}

data class DailyReview(
    val tradeDate: String,
    val summary: String,
    val aiPrompt: String,
    val createdAtMillis: Long
)

data class ReviewDraft(
    val tradeDate: String,
    val summary: String,
    val aiPrompt: String
)

data class OcrHoldingDraft(
    val id: String = UUID.randomUUID().toString(),
    val symbol: String,
    val name: String,
    val market: Market,
    val quantity: Double,
    val costPrice: Double,
    val currentPrice: Double,
    val note: String,
    val confidence: Float
)

fun todayText(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
