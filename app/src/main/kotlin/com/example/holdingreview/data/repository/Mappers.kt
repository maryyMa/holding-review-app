package com.example.holdingreview.data.repository

import com.example.holdingreview.data.local.entities.DailyReviewEntity
import com.example.holdingreview.data.local.entities.HoldingEntity
import com.example.holdingreview.data.local.entities.QuoteSnapshotEntity
import com.example.holdingreview.data.local.entities.WatchStockEntity
import com.example.holdingreview.data.remote.RemoteQuote
import com.example.holdingreview.domain.model.DailyReview
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.WatchStock

fun HoldingEntity.toDomain(quote: QuoteSnapshotEntity?): Holding {
    return Holding(
        id = id,
        symbol = symbol,
        name = quote?.name?.ifBlank { name } ?: name,
        market = Market.fromSymbol(symbol).takeUnless { it == Market.UNKNOWN } ?: market.toMarket(),
        quantity = quantity,
        costPrice = costPrice,
        currentPrice = quote?.latestPrice ?: manualCurrentPrice,
        dayChangePercent = quote?.changePercent ?: 0.0,
        note = note,
        updatedAtMillis = updatedAtMillis
    )
}

fun WatchStockEntity.toDomain(quote: QuoteSnapshotEntity?): WatchStock {
    return WatchStock(
        symbol = symbol,
        name = quote?.name?.ifBlank { name } ?: name,
        market = Market.fromSymbol(symbol).takeUnless { it == Market.UNKNOWN } ?: market.toMarket(),
        reason = reason,
        tags = tags,
        latestPrice = quote?.latestPrice,
        dayChangePercent = quote?.changePercent,
        updatedAtMillis = updatedAtMillis
    )
}

fun QuoteSnapshotEntity.toDomain(): QuoteSnapshot {
    return QuoteSnapshot(
        symbol = symbol,
        name = name,
        market = market.toMarket(),
        latestPrice = latestPrice,
        previousClose = previousClose,
        changePercent = changePercent,
        volume = volume,
        turnoverAmount = turnoverAmount,
        turnoverRate = turnoverRate,
        amplitude = amplitude,
        source = source,
        updatedAtMillis = updatedAtMillis
    )
}

fun RemoteQuote.toEntity(now: Long): QuoteSnapshotEntity {
    return QuoteSnapshotEntity(
        symbol = symbol,
        name = name,
        market = market.name,
        latestPrice = latestPrice,
        previousClose = previousClose,
        changePercent = changePercent,
        volume = volume,
        turnoverAmount = turnoverAmount,
        turnoverRate = turnoverRate,
        amplitude = amplitude,
        source = "Tencent",
        updatedAtMillis = now
    )
}

fun DailyReviewEntity.toDomain(): DailyReview {
    return DailyReview(
        tradeDate = tradeDate,
        summary = summary,
        aiPrompt = aiPrompt,
        createdAtMillis = createdAtMillis
    )
}

fun DailyReview.toEntity(): DailyReviewEntity {
    return DailyReviewEntity(
        tradeDate = tradeDate,
        summary = summary,
        aiPrompt = aiPrompt,
        createdAtMillis = createdAtMillis
    )
}

private fun String.toMarket(): Market = runCatching { Market.valueOf(this) }.getOrDefault(Market.UNKNOWN)
