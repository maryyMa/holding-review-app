package com.example.holdingreview.data.repository

import com.example.holdingreview.data.local.dao.DailyReviewDao
import com.example.holdingreview.data.local.dao.HoldingDao
import com.example.holdingreview.data.local.dao.QuoteSnapshotDao
import com.example.holdingreview.data.local.dao.WatchStockDao
import com.example.holdingreview.data.local.entities.HoldingEntity
import com.example.holdingreview.data.local.entities.WatchStockEntity
import com.example.holdingreview.data.remote.QuoteRemoteDataSource
import com.example.holdingreview.domain.model.DailyReview
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.HoldingInput
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.OcrHoldingDraft
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.WatchStock
import com.example.holdingreview.domain.model.WatchStockInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    /** 用于刷新实时行情的远程数据源。 */
    private val quoteRemoteDataSource: QuoteRemoteDataSource
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
     * 首次启动应用时写入示例持仓和关注列表条目。
     */
    override suspend fun seedIfEmpty() {
        if (holdingDao.getAllOnce().isNotEmpty()) return
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
                WatchStockEntity("000001", "平安银行", Market.SZ.name, "观察金融权重情绪", "金融,权重", now),
                WatchStockEntity("688981", "中芯国际", Market.SH.name, "观察半导体板块异动", "半导体,科技", now)
            )
        )
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
        watchStockDao.upsert(
            WatchStockEntity(
                symbol = input.symbol.trim(),
                name = input.name.trim(),
                market = input.market.name,
                reason = input.reason.trim(),
                tags = input.tags.trim(),
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    /**
     * 根据股票代码删除关注股票。
     */
    override suspend fun deleteWatchStock(symbol: String) {
        watchStockDao.deleteBySymbol(symbol)
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
            quoteSnapshotDao.upsertAll(quotes.map { it.toEntity(System.currentTimeMillis()) })
            quotes.size
        }
    }

    /**
     * 通过本地 DAO 保存生成的复盘。
     */
    override suspend fun saveDailyReview(review: DailyReview) {
        dailyReviewDao.upsert(review.toEntity())
    }
}
