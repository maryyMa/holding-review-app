package com.example.holdingreview.data.repository

import com.example.holdingreview.data.local.entities.DailyReviewEntity
import com.example.holdingreview.data.local.entities.HoldingEntity
import com.example.holdingreview.data.local.entities.KLineCacheEntity
import com.example.holdingreview.data.local.entities.MonitorAlertEntity
import com.example.holdingreview.data.local.entities.MonitorConfigEntity
import com.example.holdingreview.data.local.entities.QuoteSnapshotEntity
import com.example.holdingreview.data.local.entities.TradeOperationEntity
import com.example.holdingreview.data.local.entities.WatchStockEntity
import com.example.holdingreview.data.remote.RemoteQuote
import com.example.holdingreview.domain.model.DailyReview
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.KLinePoint
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.MonitorAlert
import com.example.holdingreview.domain.model.MonitorAlertLevel
import com.example.holdingreview.domain.model.MonitorAlertType
import com.example.holdingreview.domain.model.MonitorConfig
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.SecurityType
import com.example.holdingreview.domain.model.TradeOperation
import com.example.holdingreview.domain.model.TradeOperationSide
import com.example.holdingreview.domain.model.WatchStock

/**
 * 将持久化持仓和可选行情缓存转换为领域模型。
 */
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

/**
 * 将关注列表行和可选行情缓存转换为领域模型。
 */
fun WatchStockEntity.toDomain(quote: QuoteSnapshotEntity?): WatchStock {
    return WatchStock(
        symbol = symbol,
        name = quote?.name?.ifBlank { name } ?: name,
        market = Market.fromSymbol(symbol).takeUnless { it == Market.UNKNOWN } ?: market.toMarket(),
        reason = reason,
        tags = tags,
        watchedAtMillis = watchedAtMillis,
        watchBaseClose = watchBaseClose,
        watchBaseCloseDate = watchBaseCloseDate,
        latestPrice = quote?.latestPrice,
        dayChangePercent = quote?.changePercent,
        updatedAtMillis = updatedAtMillis
    )
}

/**
 * 将缓存行情行转换为领域行情模型。
 */
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

/**
 * 将远程行情转换为带统一时间戳的持久化缓存行。
 */
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

/**
 * 将已保存复盘行转换为领域复盘模型。
 */
fun DailyReviewEntity.toDomain(): DailyReview {
    return DailyReview(
        tradeDate = tradeDate,
        summary = summary,
        aiPrompt = aiPrompt,
        createdAtMillis = createdAtMillis
    )
}

/**
 * 将领域复盘转换为用于持久化的 Room 行。
 */
fun DailyReview.toEntity(): DailyReviewEntity {
    return DailyReviewEntity(
        tradeDate = tradeDate,
        summary = summary,
        aiPrompt = aiPrompt,
        createdAtMillis = createdAtMillis
    )
}

fun TradeOperationEntity.toDomain(): TradeOperation {
    return TradeOperation(
        id = id,
        symbol = symbol,
        side = side.toTradeOperationSide(),
        quantity = quantity,
        price = price,
        fee = fee,
        occurredAtMillis = occurredAtMillis,
        note = note,
        realizedProfit = realizedProfit,
        createdAtMillis = createdAtMillis
    )
}

fun TradeOperation.toEntity(): TradeOperationEntity {
    return TradeOperationEntity(
        id = id,
        symbol = symbol,
        side = side.name,
        quantity = quantity,
        price = price,
        fee = fee,
        occurredAtMillis = occurredAtMillis,
        note = note,
        realizedProfit = realizedProfit,
        createdAtMillis = createdAtMillis
    )
}

/**
 * 安全解析序列化后的市场枚举值。
 */
/**
 * 将监控配置实体转换为领域模型。
 */
fun MonitorConfigEntity.toDomain(): MonitorConfig {
    return MonitorConfig(
        symbol = symbol,
        market = market.toMarket(),
        securityType = securityType.toSecurityType(),
        enabled = enabled,
        enableCost = enableCost,
        enableChange = enableChange,
        enableVolume = enableVolume,
        enableMa = enableMa,
        enableRsi = enableRsi,
        enableGap = enableGap,
        enableTrailingStop = enableTrailingStop,
        costProfitPercent = costProfitPercent,
        costLossPercent = costLossPercent,
        changePercent = changePercent,
        volumeSurgeMultiplier = volumeSurgeMultiplier,
        volumeShrinkMultiplier = volumeShrinkMultiplier,
        rsiHigh = rsiHigh,
        rsiLow = rsiLow,
        gapPercent = gapPercent,
        trailingProfitStartPercent = trailingProfitStartPercent,
        trailingWarningDrawdownPercent = trailingWarningDrawdownPercent,
        trailingCriticalDrawdownPercent = trailingCriticalDrawdownPercent,
        highestPrice = highestPrice,
        updatedAtMillis = updatedAtMillis
    )
}

/**
 * 将领域层监控配置转换为 Room 实体。
 */
fun MonitorConfig.toEntity(now: Long = System.currentTimeMillis()): MonitorConfigEntity {
    return MonitorConfigEntity(
        symbol = symbol,
        market = market.name,
        securityType = securityType.name,
        enabled = enabled,
        enableCost = enableCost,
        enableChange = enableChange,
        enableVolume = enableVolume,
        enableMa = enableMa,
        enableRsi = enableRsi,
        enableGap = enableGap,
        enableTrailingStop = enableTrailingStop,
        costProfitPercent = costProfitPercent,
        costLossPercent = costLossPercent,
        changePercent = changePercent,
        volumeSurgeMultiplier = volumeSurgeMultiplier,
        volumeShrinkMultiplier = volumeShrinkMultiplier,
        rsiHigh = rsiHigh,
        rsiLow = rsiLow,
        gapPercent = gapPercent,
        trailingProfitStartPercent = trailingProfitStartPercent,
        trailingWarningDrawdownPercent = trailingWarningDrawdownPercent,
        trailingCriticalDrawdownPercent = trailingCriticalDrawdownPercent,
        highestPrice = highestPrice,
        updatedAtMillis = now
    )
}

/**
 * 将预警实体转换为领域模型。
 */
fun MonitorAlertEntity.toDomain(): MonitorAlert {
    return MonitorAlert(
        id = id,
        symbol = symbol,
        name = name,
        market = market.toMarket(),
        level = level.toMonitorAlertLevel(),
        type = type.toMonitorAlertType(),
        title = title,
        message = message,
        latestPrice = latestPrice,
        changePercent = changePercent,
        triggeredAtMillis = triggeredAtMillis,
        isRead = isRead
    )
}

/**
 * 将领域层预警转换为 Room 实体。
 */
fun MonitorAlert.toEntity(): MonitorAlertEntity {
    return MonitorAlertEntity(
        id = id,
        symbol = symbol,
        name = name,
        market = market.name,
        level = level.name,
        type = type.name,
        title = title,
        message = message,
        latestPrice = latestPrice,
        changePercent = changePercent,
        triggeredAtMillis = triggeredAtMillis,
        isRead = isRead
    )
}

/**
 * 将 K 线缓存实体转换为领域模型。
 */
fun KLineCacheEntity.toDomain(): KLinePoint {
    return KLinePoint(
        symbol = symbol,
        date = date,
        open = open,
        close = close,
        high = high,
        low = low,
        volume = volume,
        amount = amount
    )
}

/**
 * 将领域层 K 线转换为缓存实体。
 */
fun KLinePoint.toEntity(now: Long): KLineCacheEntity {
    return KLineCacheEntity(
        symbol = symbol,
        date = date,
        open = open,
        close = close,
        high = high,
        low = low,
        volume = volume,
        amount = amount,
        updatedAtMillis = now
    )
}

private fun String.toMarket(): Market = runCatching { Market.valueOf(this) }.getOrDefault(Market.UNKNOWN)

private fun String.toSecurityType(): SecurityType = runCatching { SecurityType.valueOf(this) }.getOrDefault(SecurityType.STOCK)

private fun String.toMonitorAlertLevel(): MonitorAlertLevel =
    runCatching { MonitorAlertLevel.valueOf(this) }.getOrDefault(MonitorAlertLevel.INFO)

private fun String.toMonitorAlertType(): MonitorAlertType =
    runCatching { MonitorAlertType.valueOf(this) }.getOrDefault(MonitorAlertType.CHANGE_RISE)

private fun String.toTradeOperationSide(): TradeOperationSide =
    runCatching { TradeOperationSide.valueOf(this) }.getOrDefault(TradeOperationSide.BUY)
