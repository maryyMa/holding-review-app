package com.example.holdingreview.data.repository

import com.example.holdingreview.data.local.dao.DailyReviewDao
import com.example.holdingreview.data.local.dao.HoldingDao
import com.example.holdingreview.data.local.dao.KLineCacheDao
import com.example.holdingreview.data.local.dao.MonitorConfigDao
import com.example.holdingreview.data.local.dao.QuoteSnapshotDao
import com.example.holdingreview.data.local.dao.TradeOperationDao
import com.example.holdingreview.data.local.dao.WatchStockDao
import com.example.holdingreview.data.local.entities.DailyReviewEntity
import com.example.holdingreview.data.local.entities.HoldingEntity
import com.example.holdingreview.data.local.entities.KLineCacheEntity
import com.example.holdingreview.data.local.entities.MonitorConfigEntity
import com.example.holdingreview.data.local.entities.QuoteSnapshotEntity
import com.example.holdingreview.data.local.entities.TradeOperationEntity
import com.example.holdingreview.data.local.entities.WatchStockEntity
import com.example.holdingreview.data.remote.KLineRemoteDataSource
import com.example.holdingreview.data.remote.QuoteRemoteDataSource
import com.example.holdingreview.data.remote.RemoteQuote
import com.example.holdingreview.data.seed.PersonalHoldingSeed
import com.example.holdingreview.data.seed.PersonalMonitorConfigSeed
import com.example.holdingreview.data.seed.PersonalPortfolioSeed
import com.example.holdingreview.data.seed.PersonalPortfolioSeedSource
import com.example.holdingreview.data.seed.PersonalWatchStockSeed
import com.example.holdingreview.domain.model.KLinePoint
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.TradeOperationInput
import com.example.holdingreview.domain.model.TradeOperationSide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 默认仓库单股行情查询的单元测试。
 */
class DefaultPortfolioRepositoryLookupQuoteTest {
    /**
     * 查询成功时，应返回行情快照并写入本地缓存。
     */
    @Test
    fun `lookup quote returns quote and caches snapshot`() = runTest {
        val quoteDao = FakeQuoteSnapshotDao()
        val repository = DefaultPortfolioRepository(
            holdingDao = FakeHoldingDao(),
            watchStockDao = FakeWatchStockDao(),
            quoteSnapshotDao = quoteDao,
            dailyReviewDao = FakeDailyReviewDao(),
            monitorConfigDao = FakeMonitorConfigDao(),
            tradeOperationDao = FakeTradeOperationDao(),
            personalSeedDataSource = FakePersonalSeedSource(),
            quoteRemoteDataSource = FakeQuoteRemoteDataSource(
                Result.success(
                    listOf(
                        RemoteQuote(
                            symbol = "600519",
                            name = "贵州茅台",
                            market = Market.SH,
                            latestPrice = 1586.3,
                            previousClose = 1566.88,
                            changePercent = 1.24,
                            volume = null,
                            turnoverAmount = null,
                            turnoverRate = null,
                            amplitude = null
                        )
                    )
                )
            ),
            kLineCacheDao = FakeKLineCacheDao(),
            kLineRemoteDataSource = FakeKLineRemoteDataSource()
        )

        val result = repository.lookupQuote("600519")

        assertTrue(result.isSuccess)
        assertEquals("600519", result.getOrThrow().symbol)
        assertEquals("贵州茅台", result.getOrThrow().name)
        assertEquals(1586.3, result.getOrThrow().latestPrice, 0.01)
        assertEquals(1, quoteDao.upserted.size)
        assertEquals("600519", quoteDao.upserted.first().symbol)
    }

    @Test
    fun `buy operation inserts trade and recalculates holding cost with fee`() = runTest {
        val holdingDao = FakeHoldingDao(
            mutableListOf(
                HoldingEntity("holding-1", "600519", "贵州茅台", Market.SH.name, 100.0, 10.0, 10.0, "", 0L)
            )
        )
        val tradeDao = FakeTradeOperationDao()
        val repository = DefaultPortfolioRepository(
            holdingDao = holdingDao,
            watchStockDao = FakeWatchStockDao(),
            quoteSnapshotDao = FakeQuoteSnapshotDao(),
            dailyReviewDao = FakeDailyReviewDao(),
            monitorConfigDao = FakeMonitorConfigDao(),
            tradeOperationDao = tradeDao,
            personalSeedDataSource = FakePersonalSeedSource(),
            quoteRemoteDataSource = FakeQuoteRemoteDataSource(Result.success(emptyList())),
            kLineCacheDao = FakeKLineCacheDao(),
            kLineRemoteDataSource = FakeKLineRemoteDataSource()
        )

        val result = repository.addTradeOperation(
            TradeOperationInput("600519", TradeOperationSide.BUY, 100.0, 20.0, 1_000L, "加仓")
        )

        assertTrue(result.isSuccess)
        assertEquals(1, tradeDao.inserted.size)
        val holding = holdingDao.items.single()
        assertEquals(200.0, holding.quantity, 0.001)
        assertEquals(15.001, holding.costPrice, 0.001)
    }

    @Test
    fun `sell operation records realized profit and deletes holding when cleared`() = runTest {
        val holdingDao = FakeHoldingDao(
            mutableListOf(
                HoldingEntity("holding-1", "600519", "贵州茅台", Market.SH.name, 100.0, 10.0, 10.0, "", 0L)
            )
        )
        val tradeDao = FakeTradeOperationDao()
        val repository = DefaultPortfolioRepository(
            holdingDao = holdingDao,
            watchStockDao = FakeWatchStockDao(),
            quoteSnapshotDao = FakeQuoteSnapshotDao(),
            dailyReviewDao = FakeDailyReviewDao(),
            monitorConfigDao = FakeMonitorConfigDao(),
            tradeOperationDao = tradeDao,
            personalSeedDataSource = FakePersonalSeedSource(),
            quoteRemoteDataSource = FakeQuoteRemoteDataSource(Result.success(emptyList())),
            kLineCacheDao = FakeKLineCacheDao(),
            kLineRemoteDataSource = FakeKLineRemoteDataSource()
        )

        val result = repository.addTradeOperation(
            TradeOperationInput("600519", TradeOperationSide.SELL, 100.0, 12.0, 1_000L, "清仓")
        )

        assertTrue(result.isSuccess)
        assertTrue(holdingDao.items.isEmpty())
        assertEquals(199.88, result.getOrThrow().realizedProfit ?: 0.0, 0.001)
        assertEquals(result.getOrThrow().realizedProfit, tradeDao.inserted.single().realizedProfit)
    }

    @Test
    fun `sell operation fails when quantity is greater than holding`() = runTest {
        val holdingDao = FakeHoldingDao(
            mutableListOf(
                HoldingEntity("holding-1", "600519", "贵州茅台", Market.SH.name, 50.0, 10.0, 10.0, "", 0L)
            )
        )
        val tradeDao = FakeTradeOperationDao()
        val repository = DefaultPortfolioRepository(
            holdingDao = holdingDao,
            watchStockDao = FakeWatchStockDao(),
            quoteSnapshotDao = FakeQuoteSnapshotDao(),
            dailyReviewDao = FakeDailyReviewDao(),
            monitorConfigDao = FakeMonitorConfigDao(),
            tradeOperationDao = tradeDao,
            personalSeedDataSource = FakePersonalSeedSource(),
            quoteRemoteDataSource = FakeQuoteRemoteDataSource(Result.success(emptyList())),
            kLineCacheDao = FakeKLineCacheDao(),
            kLineRemoteDataSource = FakeKLineRemoteDataSource()
        )

        val result = repository.addTradeOperation(
            TradeOperationInput("600519", TradeOperationSide.SELL, 100.0, 12.0, 1_000L, "超量卖出")
        )

        assertTrue(result.isFailure)
        assertEquals(50.0, holdingDao.items.single().quantity, 0.001)
        assertTrue(tradeDao.inserted.isEmpty())
    }

    @Test
    fun `seed imports personal portfolio when database is empty`() = runTest {
        val holdingDao = FakeHoldingDao()
        val watchStockDao = FakeWatchStockDao()
        val monitorConfigDao = FakeMonitorConfigDao()
        val quoteDao = FakeQuoteSnapshotDao()
        val repository = DefaultPortfolioRepository(
            holdingDao = holdingDao,
            watchStockDao = watchStockDao,
            quoteSnapshotDao = quoteDao,
            dailyReviewDao = FakeDailyReviewDao(),
            monitorConfigDao = monitorConfigDao,
            tradeOperationDao = FakeTradeOperationDao(),
            personalSeedDataSource = FakePersonalSeedSource(personalSeed()),
            quoteRemoteDataSource = FakeQuoteRemoteDataSource(
                Result.success(
                    listOf(
                        RemoteQuote(
                            symbol = "600519",
                            name = "贵州茅台",
                            market = Market.SH,
                            latestPrice = 1586.3,
                            previousClose = null,
                            changePercent = 1.0,
                            volume = null,
                            turnoverAmount = null,
                            turnoverRate = null,
                            amplitude = null
                        ),
                        RemoteQuote(
                            symbol = "688981",
                            name = "中芯国际",
                            market = Market.SH,
                            latestPrice = 35.0,
                            previousClose = null,
                            changePercent = 1.0,
                            volume = null,
                            turnoverAmount = null,
                            turnoverRate = null,
                            amplitude = null
                        )
                    )
                )
            ),
            kLineCacheDao = FakeKLineCacheDao(),
            kLineRemoteDataSource = FakeKLineRemoteDataSource()
        )

        val result = repository.seedIfEmpty()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())
        assertEquals("600519", holdingDao.items.single().symbol)
        assertEquals("贵州茅台", holdingDao.items.single().name)
        assertEquals(1586.3, holdingDao.items.single().manualCurrentPrice, 0.001)
        assertEquals("688981", watchStockDao.items.single().symbol)
        assertEquals("中芯国际", watchStockDao.items.single().name)
        assertEquals(2, quoteDao.upserted.size)
        assertEquals("600519", monitorConfigDao.items.single().symbol)
        assertEquals(8.0, monitorConfigDao.items.single().costProfitPercent, 0.001)
    }

    @Test
    fun `seed imports missing personal symbols without overriding existing local portfolio`() = runTest {
        val holdingDao = FakeHoldingDao(
            mutableListOf(
                HoldingEntity("holding-1", "600519", "本地名称", Market.SH.name, 50.0, 10.0, 10.0, "本地备注", 0L)
            )
        )
        val watchStockDao = FakeWatchStockDao()
        val monitorConfigDao = FakeMonitorConfigDao()
        val repository = DefaultPortfolioRepository(
            holdingDao = holdingDao,
            watchStockDao = watchStockDao,
            quoteSnapshotDao = FakeQuoteSnapshotDao(),
            dailyReviewDao = FakeDailyReviewDao(),
            monitorConfigDao = monitorConfigDao,
            tradeOperationDao = FakeTradeOperationDao(),
            personalSeedDataSource = FakePersonalSeedSource(personalSeed()),
            quoteRemoteDataSource = FakeQuoteRemoteDataSource(Result.success(emptyList())),
            kLineCacheDao = FakeKLineCacheDao(),
            kLineRemoteDataSource = FakeKLineRemoteDataSource()
        )

        val result = repository.seedIfEmpty()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())
        assertEquals(1, holdingDao.items.size)
        assertEquals("本地名称", holdingDao.items.single().name)
        assertEquals(50.0, holdingDao.items.single().quantity, 0.001)
        assertEquals(listOf("688981"), watchStockDao.items.map { it.symbol })
        assertEquals(listOf("600519"), monitorConfigDao.items.map { it.symbol })
    }

    /**
     * 只记录写入缓存的行情快照。
     */
    private class FakeQuoteSnapshotDao : QuoteSnapshotDao {
        /** 被写入缓存的行情快照。 */
        val upserted = mutableListOf<QuoteSnapshotEntity>()

        override fun observeAll(): Flow<List<QuoteSnapshotEntity>> = flowOf(upserted)

        override suspend fun getAllOnce(): List<QuoteSnapshotEntity> = upserted

        override suspend fun upsertAll(entities: List<QuoteSnapshotEntity>) {
            upserted.clear()
            upserted.addAll(entities)
        }
    }

    /**
     * 提供测试用远程行情结果。
     */
    private class FakeQuoteRemoteDataSource(
        private val result: Result<List<RemoteQuote>>
    ) : QuoteRemoteDataSource {
        override suspend fun fetchQuotes(symbols: List<String>): Result<List<RemoteQuote>> = result
    }

    /**
     * 本测试不会访问持仓 DAO。
     */
    private class FakeHoldingDao(
        val items: MutableList<HoldingEntity> = mutableListOf()
    ) : HoldingDao {
        override fun observeAll(): Flow<List<HoldingEntity>> = flowOf(items)

        override fun observeById(id: String): Flow<HoldingEntity?> = flowOf(null)

        override suspend fun findBySymbol(symbol: String): HoldingEntity? = items.firstOrNull { it.symbol == symbol }

        override suspend fun getAllOnce(): List<HoldingEntity> = items

        override suspend fun upsert(entity: HoldingEntity) {
            val index = items.indexOfFirst { it.id == entity.id || it.symbol == entity.symbol }
            if (index >= 0) {
                items[index] = entity
            } else {
                items.add(entity)
            }
        }

        override suspend fun upsertAll(entities: List<HoldingEntity>) {
            entities.forEach { upsert(it) }
        }

        override suspend fun deleteById(id: String) {
            items.removeAll { it.id == id }
        }
    }

    /**
     * 本测试不会访问关注列表 DAO。
     */
    private fun personalSeed(): PersonalPortfolioSeed {
        return PersonalPortfolioSeed(
            holdings = listOf(
                PersonalHoldingSeed(
                    symbol = "600519",
                    quantity = 100.0,
                    costPrice = 1560.0,
                    note = "核心观察仓"
                )
            ),
            watchStocks = listOf(
                PersonalWatchStockSeed(
                    symbol = "688981",
                    reason = "观察半导体板块异动",
                    industry = "半导体,科技"
                )
            ),
            monitorConfigs = listOf(
                PersonalMonitorConfigSeed(
                    symbol = "600519",
                    enabled = true,
                    costProfitPercent = 8.0,
                    costLossPercent = -5.0,
                    changePercent = 3.0,
                    volumeSurgeMultiplier = 2.0,
                    volumeShrinkMultiplier = 0.5,
                    rsiHigh = 75.0,
                    rsiLow = 25.0,
                    gapPercent = 2.5,
                    trailingProfitStartPercent = 10.0,
                    trailingWarningDrawdownPercent = 3.0,
                    trailingCriticalDrawdownPercent = 6.0
                )
            )
        )
    }

    private class FakeWatchStockDao(
        val items: MutableList<WatchStockEntity> = mutableListOf()
    ) : WatchStockDao {
        override fun observeAll(): Flow<List<WatchStockEntity>> = flowOf(items)

        override suspend fun getAllOnce(): List<WatchStockEntity> = items

        override suspend fun findBySymbol(symbol: String): WatchStockEntity? = items.firstOrNull { it.symbol == symbol }

        override suspend fun upsert(entity: WatchStockEntity) {
            val index = items.indexOfFirst { it.symbol == entity.symbol }
            if (index >= 0) {
                items[index] = entity
            } else {
                items.add(entity)
            }
        }

        override suspend fun upsertAll(entities: List<WatchStockEntity>) {
            entities.forEach { upsert(it) }
        }

        override suspend fun updateWatchBaseClose(symbol: String, baseClose: Double, baseCloseDate: String) = Unit

        override suspend fun deleteBySymbol(symbol: String) = Unit
    }

    private class FakeKLineCacheDao : KLineCacheDao {
        override suspend fun getRecent(symbol: String, limit: Int): List<KLineCacheEntity> = emptyList()

        override suspend fun upsertAll(entities: List<KLineCacheEntity>) = Unit

        override suspend fun deleteBySymbol(symbol: String) = Unit
    }

    private class FakeKLineRemoteDataSource : KLineRemoteDataSource {
        override suspend fun fetchDailyKLines(symbol: String, market: Market, limit: Int): Result<List<KLinePoint>> =
            Result.success(emptyList())
    }

    /**
     * 本测试不会访问每日复盘 DAO。
     */
    private class FakeDailyReviewDao : DailyReviewDao {
        override fun observeLatest(): Flow<DailyReviewEntity?> = flowOf(null)

        override suspend fun upsert(entity: DailyReviewEntity) = Unit
    }

    private class FakeMonitorConfigDao : MonitorConfigDao {
        val items = mutableListOf<MonitorConfigEntity>()

        override fun observeAll(): Flow<List<MonitorConfigEntity>> = flowOf(items)

        override fun observeBySymbol(symbol: String): Flow<MonitorConfigEntity?> =
            flowOf(items.firstOrNull { it.symbol == symbol })

        override suspend fun getAllOnce(): List<MonitorConfigEntity> = items

        override suspend fun findBySymbol(symbol: String): MonitorConfigEntity? =
            items.firstOrNull { it.symbol == symbol }

        override suspend fun upsert(entity: MonitorConfigEntity) {
            val index = items.indexOfFirst { it.symbol == entity.symbol }
            if (index >= 0) {
                items[index] = entity
            } else {
                items.add(entity)
            }
        }

        override suspend fun updateEnabled(symbol: String, enabled: Boolean, updatedAtMillis: Long) = Unit

        override suspend fun updateHighestPrice(symbol: String, highestPrice: Double, updatedAtMillis: Long) = Unit
    }

    private class FakePersonalSeedSource(
        private val seed: PersonalPortfolioSeed? = null
    ) : PersonalPortfolioSeedSource {
        override fun load(): PersonalPortfolioSeed? = seed
    }

    private class FakeTradeOperationDao : TradeOperationDao {
        val inserted = mutableListOf<TradeOperationEntity>()

        override fun observeBySymbol(symbol: String): Flow<List<TradeOperationEntity>> = flowOf(emptyList())

        override suspend fun insert(entity: TradeOperationEntity) {
            inserted.add(entity)
        }
    }
}
