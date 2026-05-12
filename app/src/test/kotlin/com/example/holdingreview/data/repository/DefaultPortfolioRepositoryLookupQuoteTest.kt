package com.example.holdingreview.data.repository

import com.example.holdingreview.data.local.dao.DailyReviewDao
import com.example.holdingreview.data.local.dao.HoldingDao
import com.example.holdingreview.data.local.dao.QuoteSnapshotDao
import com.example.holdingreview.data.local.dao.WatchStockDao
import com.example.holdingreview.data.local.entities.DailyReviewEntity
import com.example.holdingreview.data.local.entities.HoldingEntity
import com.example.holdingreview.data.local.entities.QuoteSnapshotEntity
import com.example.holdingreview.data.local.entities.WatchStockEntity
import com.example.holdingreview.data.remote.QuoteRemoteDataSource
import com.example.holdingreview.data.remote.RemoteQuote
import com.example.holdingreview.domain.model.Market
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
            )
        )

        val result = repository.lookupQuote("600519")

        assertTrue(result.isSuccess)
        assertEquals("600519", result.getOrThrow().symbol)
        assertEquals("贵州茅台", result.getOrThrow().name)
        assertEquals(1586.3, result.getOrThrow().latestPrice, 0.01)
        assertEquals(1, quoteDao.upserted.size)
        assertEquals("600519", quoteDao.upserted.first().symbol)
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
    private class FakeHoldingDao : HoldingDao {
        override fun observeAll(): Flow<List<HoldingEntity>> = flowOf(emptyList())

        override fun observeById(id: String): Flow<HoldingEntity?> = flowOf(null)

        override suspend fun findBySymbol(symbol: String): HoldingEntity? = null

        override suspend fun getAllOnce(): List<HoldingEntity> = emptyList()

        override suspend fun upsert(entity: HoldingEntity) = Unit

        override suspend fun upsertAll(entities: List<HoldingEntity>) = Unit

        override suspend fun deleteById(id: String) = Unit
    }

    /**
     * 本测试不会访问关注列表 DAO。
     */
    private class FakeWatchStockDao : WatchStockDao {
        override fun observeAll(): Flow<List<WatchStockEntity>> = flowOf(emptyList())

        override suspend fun getAllOnce(): List<WatchStockEntity> = emptyList()

        override suspend fun upsert(entity: WatchStockEntity) = Unit

        override suspend fun upsertAll(entities: List<WatchStockEntity>) = Unit

        override suspend fun deleteBySymbol(symbol: String) = Unit
    }

    /**
     * 本测试不会访问每日复盘 DAO。
     */
    private class FakeDailyReviewDao : DailyReviewDao {
        override fun observeLatest(): Flow<DailyReviewEntity?> = flowOf(null)

        override suspend fun upsert(entity: DailyReviewEntity) = Unit
    }
}
