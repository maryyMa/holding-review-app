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

@Singleton
class DefaultPortfolioRepository @Inject constructor(
    private val holdingDao: HoldingDao,
    private val watchStockDao: WatchStockDao,
    private val quoteSnapshotDao: QuoteSnapshotDao,
    private val dailyReviewDao: DailyReviewDao,
    private val quoteRemoteDataSource: QuoteRemoteDataSource
) : PortfolioRepository {
    override fun observeHoldings(): Flow<List<Holding>> {
        return combine(holdingDao.observeAll(), quoteSnapshotDao.observeAll()) { holdings, quotes ->
            val quoteMap = quotes.associateBy { it.symbol }
            holdings.map { it.toDomain(quoteMap[it.symbol]) }
        }
    }

    override fun observeHolding(id: String): Flow<Holding?> {
        return combine(holdingDao.observeById(id), quoteSnapshotDao.observeAll()) { holding, quotes ->
            holding?.toDomain(quotes.associateBy { it.symbol }[holding.symbol])
        }
    }

    override fun observeWatchStocks(): Flow<List<WatchStock>> {
        return combine(watchStockDao.observeAll(), quoteSnapshotDao.observeAll()) { watchStocks, quotes ->
            val quoteMap = quotes.associateBy { it.symbol }
            watchStocks.map { it.toDomain(quoteMap[it.symbol]) }
        }
    }

    override fun observeQuotes(): Flow<List<QuoteSnapshot>> {
        return quoteSnapshotDao.observeAll().map { quotes -> quotes.map { it.toDomain() } }
    }

    override fun observeLatestReview(): Flow<DailyReview?> {
        return dailyReviewDao.observeLatest().map { it?.toDomain() }
    }

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

    override suspend fun deleteHolding(id: String) {
        holdingDao.deleteById(id)
    }

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

    override suspend fun deleteWatchStock(symbol: String) {
        watchStockDao.deleteBySymbol(symbol)
    }

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

    override suspend fun saveDailyReview(review: DailyReview) {
        dailyReviewDao.upsert(review.toEntity())
    }
}
