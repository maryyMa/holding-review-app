package com.example.holdingreview.data.repository

import com.example.holdingreview.data.local.dao.DailyReviewDao
import com.example.holdingreview.data.local.dao.HoldingDao
import com.example.holdingreview.data.local.dao.KLineCacheDao
import com.example.holdingreview.data.local.dao.MonitorConfigDao
import com.example.holdingreview.data.local.dao.QuoteSnapshotDao
import com.example.holdingreview.data.local.dao.TradeOperationDao
import com.example.holdingreview.data.local.dao.WatchStockDao
import com.example.holdingreview.data.local.entities.HoldingEntity
import com.example.holdingreview.data.local.entities.WatchStockEntity
import com.example.holdingreview.data.remote.KLineRemoteDataSource
import com.example.holdingreview.data.remote.QuoteRemoteDataSource
import com.example.holdingreview.data.seed.PersonalMonitorConfigSeed
import com.example.holdingreview.data.seed.PersonalPortfolioSeed
import com.example.holdingreview.data.seed.PersonalPortfolioSeedSource
import com.example.holdingreview.domain.model.DailyReview
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.HoldingInput
import com.example.holdingreview.domain.model.KLinePoint
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.MonitorConfig
import com.example.holdingreview.domain.model.OcrHoldingDraft
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.SecurityType
import com.example.holdingreview.domain.model.TradeOperation
import com.example.holdingreview.domain.model.TradeOperationInput
import com.example.holdingreview.domain.model.TradeOperationSide
import com.example.holdingreview.domain.model.WatchStock
import com.example.holdingreview.domain.model.WatchStockInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 协调 Room DAO 和远程行情的默认仓库实现。
 */
@Singleton
class DefaultPortfolioRepository @Inject constructor(
    /** 组合持仓 DAO。 */
    private val holdingDao: HoldingDao,
    /** 关注列表 DAO。 */
    private val watchStockDao: WatchStockDao,
    /** 缓存行情快照 DAO。 */
    private val quoteSnapshotDao: QuoteSnapshotDao,
    /** 已保存每日复盘 DAO。 */
    private val dailyReviewDao: DailyReviewDao,
    /** 股票监控配置 DAO。 */
    private val monitorConfigDao: MonitorConfigDao,
    /** 交易操作记录 DAO。 */
    private val tradeOperationDao: TradeOperationDao,
    /** 可选的本地个人组合初始化数据。 */
    private val personalSeedDataSource: PersonalPortfolioSeedSource,
    /** 用于刷新实时行情的远程数据源。 */
    private val quoteRemoteDataSource: QuoteRemoteDataSource,
    /** 本地 K 线缓存 DAO。 */
    private val kLineCacheDao: KLineCacheDao,
    /** 用于确认关注日基准收盘价的 K 线数据源。 */
    private val kLineRemoteDataSource: KLineRemoteDataSource
) : PortfolioRepository {
    /**
     * 发出已合并对应缓存行情快照的持仓。
     */
    override fun observeHoldings(): Flow<List<Holding>> {
        return combine(holdingDao.observeAll(), quoteSnapshotDao.observeAll()) { holdings, quotes ->
            val quoteMap = quotes.associateBy { it.symbol }
            holdings.map { it.toDomain(quoteMap[it.symbol]) }
        }
    }

    /**
     * 按 id 发出已合并对应缓存行情快照的单个持仓。
     */
    override fun observeHolding(id: String): Flow<Holding?> {
        return combine(holdingDao.observeById(id), quoteSnapshotDao.observeAll()) { holding, quotes ->
            holding?.toDomain(quotes.associateBy { it.symbol }[holding.symbol])
        }
    }

    /**
     * 发出已合并对应缓存行情快照的关注股票。
     */
    override fun observeWatchStocks(): Flow<List<WatchStock>> {
        return combine(watchStockDao.observeAll(), quoteSnapshotDao.observeAll()) { watchStocks, quotes ->
            val quoteMap = quotes.associateBy { it.symbol }
            watchStocks.map { it.toDomain(quoteMap[it.symbol]) }
        }
    }

    override fun observeTradeOperations(symbol: String): Flow<List<TradeOperation>> {
        return tradeOperationDao.observeBySymbol(symbol.trim()).map { operations ->
            operations.map { it.toDomain() }
        }
    }

    /**
     * 以领域模型形式发出所有缓存行情。
     */
    override fun observeQuotes(): Flow<List<QuoteSnapshot>> {
        return quoteSnapshotDao.observeAll().map { quotes -> quotes.map { it.toDomain() } }
    }

    /**
     * 发出最新保存的每日复盘。
     */
    override fun observeLatestReview(): Flow<DailyReview?> {
        return dailyReviewDao.observeLatest().map { it?.toDomain() }
    }

    /**
     * 查询单只股票行情，成功后同步缓存行情快照。
     */
    override suspend fun lookupQuote(symbol: String): Result<QuoteSnapshot> {
        val normalizedSymbol = symbol.trim()
        if (normalizedSymbol.length != 6) {
            return Result.failure(IllegalArgumentException("股票代码必须是 6 位"))
        }

        return quoteRemoteDataSource.fetchQuotes(listOf(normalizedSymbol)).mapCatching { quotes ->
            val quote = quotes.firstOrNull { it.symbol == normalizedSymbol }
                ?: quotes.firstOrNull()
                ?: throw IllegalStateException("没有查询到股票行情")
            val entity = quote.toEntity(System.currentTimeMillis())
            quoteSnapshotDao.upsertAll(listOf(entity))
            entity.toDomain()
        }
    }

    /**
     * 每次启动尝试补充内置个人数据里本地缺失的记录；没有内置数据且数据库为空时写入示例数据。
     */
    override suspend fun seedIfEmpty(): Result<Boolean> {
        return runCatching {
            val personalSeed = personalSeedDataSource.load()
            if (personalSeed != null) {
                importPersonalSeed(personalSeed)
            } else {
                if (holdingDao.getAllOnce().isNotEmpty() || watchStockDao.getAllOnce().isNotEmpty()) {
                    return@runCatching false
                }
                seedDemoData()
                true
            }
        }
    }

    private suspend fun seedDemoData() {
        val now = System.currentTimeMillis()
        holdingDao.upsertAll(
            listOf(
                HoldingEntity(UUID.randomUUID().toString(), "600519", "贵州茅台", Market.SH.name, 100.0, 1560.0, 1586.3, "核心观察仓，关注消费板块情绪。", now),
                HoldingEntity(UUID.randomUUID().toString(), "300750", "宁德时代", Market.SZ.name, 200.0, 186.5, 181.8, "新能源波动较大，仓位暂不加。", now),
                HoldingEntity(UUID.randomUUID().toString(), "510300", "沪深300ETF", Market.SH.name, 3000.0, 3.72, 3.86, "指数底仓，按计划定投。", now)
            )
        )
        watchStockDao.upsertAll(
            listOf(
                WatchStockEntity("000001", "平安银行", Market.SZ.name, "观察金融权重情绪", "金融,权重", now, null, null, now),
                WatchStockEntity("688981", "中芯国际", Market.SH.name, "观察半导体板块异动", "半导体,科技", now, null, null, now)
            )
        )
    }

    private suspend fun importPersonalSeed(seed: PersonalPortfolioSeed): Boolean {
        val now = System.currentTimeMillis()
        val existingHoldingSymbols = holdingDao.getAllOnce().map { it.symbol }.toSet()
        val existingWatchSymbols = watchStockDao.getAllOnce().map { it.symbol }.toSet()
        val existingConfigSymbols = monitorConfigDao.getAllOnce().map { it.symbol }.toSet()
        val missingHoldings = seed.holdings
            .distinctBy { it.symbol }
            .filterNot { it.symbol in existingHoldingSymbols }
        val missingWatchStocks = seed.watchStocks
            .distinctBy { it.symbol }
            .filterNot { it.symbol in existingWatchSymbols }
        val missingConfigs = seed.monitorConfigs
            .distinctBy { it.symbol }
            .filterNot { it.symbol in existingConfigSymbols }

        if (missingHoldings.isEmpty() && missingWatchStocks.isEmpty() && missingConfigs.isEmpty()) {
            return false
        }

        val symbols = (missingHoldings.map { it.symbol } + missingWatchStocks.map { it.symbol })
            .distinct()
            .filter { it.length == 6 }
        val quotes = if (symbols.isEmpty()) {
            emptyList()
        } else {
            quoteRemoteDataSource.fetchQuotes(symbols).getOrDefault(emptyList())
        }
        if (quotes.isNotEmpty()) {
            quoteSnapshotDao.upsertAll(quotes.map { it.toEntity(now) })
        }
        val quoteMap = quotes.associateBy { it.symbol }
        holdingDao.upsertAll(
            missingHoldings.map { holding ->
                val quote = quoteMap[holding.symbol]
                HoldingEntity(
                    id = UUID.randomUUID().toString(),
                    symbol = holding.symbol,
                    name = quote?.name?.ifBlank { holding.symbol } ?: holding.symbol,
                    market = quote?.market?.name ?: Market.fromSymbol(holding.symbol).name,
                    quantity = holding.quantity,
                    costPrice = holding.costPrice,
                    manualCurrentPrice = quote?.latestPrice ?: holding.costPrice,
                    note = holding.note,
                    updatedAtMillis = now
                )
            }
        )
        watchStockDao.upsertAll(
            missingWatchStocks.map { watch ->
                val quote = quoteMap[watch.symbol]
                WatchStockEntity(
                    symbol = watch.symbol,
                    name = quote?.name?.ifBlank { watch.symbol } ?: watch.symbol,
                    market = quote?.market?.name ?: Market.fromSymbol(watch.symbol).name,
                    reason = watch.reason,
                    tags = watch.industry,
                    watchedAtMillis = now,
                    watchBaseClose = null,
                    watchBaseCloseDate = null,
                    updatedAtMillis = now
                )
            }
        )
        missingConfigs.forEach { config ->
            monitorConfigDao.upsert(config.toMonitorConfig(now).toEntity(now))
        }
        return true
    }

    /**
     * 插入或更新持仓；代码已存在时保留原有 id。
     */
    override suspend fun upsertHolding(input: HoldingInput) {
        val now = System.currentTimeMillis()
        val existing = holdingDao.findBySymbol(input.symbol)
        holdingDao.upsert(
            HoldingEntity(
                id = input.id ?: existing?.id ?: UUID.randomUUID().toString(),
                symbol = input.symbol.trim(),
                name = input.name.trim(),
                market = input.market.name,
                quantity = input.quantity,
                costPrice = input.costPrice,
                manualCurrentPrice = input.manualCurrentPrice,
                note = input.note.trim(),
                updatedAtMillis = now
            )
        )
    }

    /**
     * 将 OCR 草稿转换为普通持仓输入并保存。
     */
    override suspend fun upsertOcrDraft(draft: OcrHoldingDraft) {
        upsertHolding(
            HoldingInput(
                symbol = draft.symbol,
                name = draft.name,
                market = draft.market,
                quantity = draft.quantity,
                costPrice = draft.costPrice,
                manualCurrentPrice = draft.currentPrice,
                note = draft.note
            )
        )
    }

    /**
     * 根据本地 id 删除持仓。
     */
    override suspend fun deleteHolding(id: String) {
        holdingDao.deleteById(id)
    }

    /**
     * 裁剪用户输入文本后插入或更新关注股票。
     */
    override suspend fun upsertWatchStock(input: WatchStockInput) {
        val normalizedSymbol = input.symbol.trim()
        val now = System.currentTimeMillis()
        val existing = watchStockDao.findBySymbol(normalizedSymbol)
        val watchedAtMillis = existing?.watchedAtMillis?.takeIf { it > 0 } ?: now
        val market = input.market.takeUnless { it == Market.UNKNOWN } ?: Market.fromSymbol(normalizedSymbol)
        val baseClose = if (existing?.watchBaseClose == null) {
            resolveWatchBaseClose(normalizedSymbol, market, watchedAtMillis, now)
        } else {
            null
        }
        watchStockDao.upsert(
            WatchStockEntity(
                symbol = normalizedSymbol,
                name = input.name.trim(),
                market = market.name,
                reason = input.reason.trim(),
                tags = input.tags.trim(),
                watchedAtMillis = watchedAtMillis,
                watchBaseClose = existing?.watchBaseClose ?: baseClose?.close,
                watchBaseCloseDate = existing?.watchBaseCloseDate ?: baseClose?.date,
                updatedAtMillis = now
            )
        )
    }

    /**
     * 根据股票代码删除关注股票。
     */
    override suspend fun deleteWatchStock(symbol: String) {
        watchStockDao.deleteBySymbol(symbol)
    }

    override suspend fun addTradeOperation(input: TradeOperationInput): Result<TradeOperation> {
        return runCatching {
            val normalizedSymbol = input.symbol.trim()
            require(normalizedSymbol.length == 6) { "股票代码必须是 6 位" }
            require(input.quantity > 0) { "数量必须大于 0" }
            require(input.price > 0) { "价格必须大于 0" }

            val now = System.currentTimeMillis()
            val existing = holdingDao.findBySymbol(normalizedSymbol)
            val amount = input.quantity * input.price
            val fee = amount * TradeFeeRate
            val realizedProfit = when (input.side) {
                TradeOperationSide.BUY -> null
                TradeOperationSide.SELL -> {
                    require(existing != null) { "没有可卖出的持仓" }
                    require(existing.quantity + QuantityTolerance >= input.quantity) { "卖出数量不能超过当前持仓" }
                    (input.price - existing.costPrice) * input.quantity - fee
                }
            }
            val operation = TradeOperation(
                id = UUID.randomUUID().toString(),
                symbol = normalizedSymbol,
                side = input.side,
                quantity = input.quantity,
                price = input.price,
                fee = fee,
                occurredAtMillis = input.occurredAtMillis,
                note = input.note.trim(),
                realizedProfit = realizedProfit,
                createdAtMillis = now
            )

            syncHoldingForOperation(operation, existing)
            tradeOperationDao.insert(operation.toEntity())
            operation
        }
    }

    /**
     * 为所有已保存和已关注代码拉取行情，并缓存结果。
     */
    override suspend fun refreshQuotes(): Result<Int> {
        val symbols = (holdingDao.getAllOnce().map { it.symbol } + watchStockDao.getAllOnce().map { it.symbol })
            .distinct()
            .filter { it.length == 6 }
        if (symbols.isEmpty()) return Result.success(0)

        return quoteRemoteDataSource.fetchQuotes(symbols).mapCatching { quotes ->
            val now = System.currentTimeMillis()
            quoteSnapshotDao.upsertAll(quotes.map { it.toEntity(now) })
            refreshMissingWatchBaseCloses(now)
            quotes.size
        }
    }

    /**
     * 通过本地 DAO 保存生成的复盘。
     */
    override suspend fun saveDailyReview(review: DailyReview) {
        dailyReviewDao.upsert(review.toEntity())
    }

    private suspend fun refreshMissingWatchBaseCloses(now: Long) {
        watchStockDao.getAllOnce()
            .filter { it.watchBaseClose == null }
            .forEach { watch ->
                val baseClose = resolveWatchBaseClose(
                    symbol = watch.symbol,
                    market = runCatching { Market.valueOf(watch.market) }.getOrDefault(Market.fromSymbol(watch.symbol)),
                    watchedAtMillis = watch.watchedAtMillis,
                    now = now
                )
                if (baseClose != null) {
                    watchStockDao.updateWatchBaseClose(watch.symbol, baseClose.close, baseClose.date)
                }
            }
    }

    private suspend fun resolveWatchBaseClose(
        symbol: String,
        market: Market,
        watchedAtMillis: Long,
        now: Long
    ): WatchBaseClose? {
        val watchedDate = Instant.ofEpochMilli(watchedAtMillis)
            .atZone(ChinaMarketZone)
            .toLocalDate()
        val watchedDateText = watchedDate.toString()
        val points = fetchWatchBaseKLines(symbol, market, now)
        val basePoint = points
            .filter { it.date <= watchedDateText }
            .maxByOrNull { it.date }
            ?: return null

        val nowInChina = Instant.ofEpochMilli(now).atZone(ChinaMarketZone)
        val isTodayBeforeClose =
            basePoint.date == watchedDateText &&
                watchedDate == nowInChina.toLocalDate() &&
                nowInChina.toLocalTime().isBefore(ChinaMarketCloseConfirmation)
        if (isTodayBeforeClose) return null

        return WatchBaseClose(close = basePoint.close, date = basePoint.date)
    }

    private suspend fun fetchWatchBaseKLines(symbol: String, market: Market, now: Long): List<KLinePoint> {
        val normalizedSymbol = symbol.trim()
        val remote = kLineRemoteDataSource.fetchDailyKLines(normalizedSymbol, market, 30)
        return remote.fold(
            onSuccess = { points ->
                kLineCacheDao.upsertAll(points.map { it.toEntity(now) })
                points.sortedBy { it.date }
            },
            onFailure = {
                kLineCacheDao.getRecent(normalizedSymbol, 30)
                    .map { it.toDomain() }
                    .sortedBy { it.date }
            }
        )
    }

    private suspend fun syncHoldingForOperation(operation: TradeOperation, existing: HoldingEntity?) {
        when (operation.side) {
            TradeOperationSide.BUY -> syncBuy(operation, existing)
            TradeOperationSide.SELL -> syncSell(operation, existing ?: error("没有可卖出的持仓"))
        }
    }

    private suspend fun syncBuy(operation: TradeOperation, existing: HoldingEntity?) {
        val quote = quoteSnapshotDao.getAllOnce().firstOrNull { it.symbol == operation.symbol }
        val watch = watchStockDao.findBySymbol(operation.symbol)
        val newQuantity = (existing?.quantity ?: 0.0) + operation.quantity
        val existingCostValue = (existing?.quantity ?: 0.0) * (existing?.costPrice ?: 0.0)
        val newCostPrice = (existingCostValue + operation.amount + operation.fee) / newQuantity
        val resolvedName = existing?.name
            ?: quote?.name?.ifBlank { watch?.name.orEmpty() }
            ?: watch?.name
            ?: operation.symbol
        holdingDao.upsert(
            HoldingEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                symbol = operation.symbol,
                name = resolvedName.ifBlank { operation.symbol },
                market = existing?.market ?: quote?.market ?: watch?.market ?: Market.fromSymbol(operation.symbol).name,
                quantity = newQuantity,
                costPrice = newCostPrice,
                manualCurrentPrice = quote?.latestPrice ?: existing?.manualCurrentPrice ?: operation.price,
                note = existing?.note.orEmpty(),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    private suspend fun syncSell(operation: TradeOperation, existing: HoldingEntity) {
        val remainQuantity = existing.quantity - operation.quantity
        if (remainQuantity <= QuantityTolerance) {
            holdingDao.deleteById(existing.id)
            return
        }
        val quote = quoteSnapshotDao.getAllOnce().firstOrNull { it.symbol == operation.symbol }
        holdingDao.upsert(
            existing.copy(
                quantity = remainQuantity,
                manualCurrentPrice = quote?.latestPrice ?: operation.price,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    private fun PersonalMonitorConfigSeed.toMonitorConfig(now: Long): MonitorConfig {
        val market = Market.fromSymbol(symbol).takeUnless { it == Market.UNKNOWN } ?: Market.UNKNOWN
        val default = MonitorConfig.defaultFor(symbol, market)
        return default.copy(
            securityType = SecurityType.fromSymbol(symbol),
            enabled = enabled,
            costProfitPercent = costProfitPercent ?: default.costProfitPercent,
            costLossPercent = costLossPercent ?: default.costLossPercent,
            changePercent = changePercent ?: default.changePercent,
            volumeSurgeMultiplier = volumeSurgeMultiplier ?: default.volumeSurgeMultiplier,
            volumeShrinkMultiplier = volumeShrinkMultiplier ?: default.volumeShrinkMultiplier,
            rsiHigh = rsiHigh ?: default.rsiHigh,
            rsiLow = rsiLow ?: default.rsiLow,
            gapPercent = gapPercent ?: default.gapPercent,
            trailingProfitStartPercent = trailingProfitStartPercent ?: default.trailingProfitStartPercent,
            trailingWarningDrawdownPercent = trailingWarningDrawdownPercent ?: default.trailingWarningDrawdownPercent,
            trailingCriticalDrawdownPercent = trailingCriticalDrawdownPercent ?: default.trailingCriticalDrawdownPercent,
            updatedAtMillis = now
        )
    }

    private data class WatchBaseClose(val close: Double, val date: String)

    private companion object {
        const val TradeFeeRate: Double = 0.0001
        const val QuantityTolerance: Double = 0.000001
        val ChinaMarketZone: ZoneId = ZoneId.of("Asia/Shanghai")
        val ChinaMarketCloseConfirmation: LocalTime = LocalTime.of(15, 5)
    }
}
