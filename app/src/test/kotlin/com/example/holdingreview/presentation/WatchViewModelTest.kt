package com.example.holdingreview.presentation

import com.example.holdingreview.data.repository.PortfolioRepository
import com.example.holdingreview.domain.model.DailyReview
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.HoldingInput
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.OcrHoldingDraft
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.WatchStock
import com.example.holdingreview.domain.model.WatchStockInput
import com.example.holdingreview.domain.usecase.InferIndustryUseCase
import com.example.holdingreview.domain.usecase.LookupQuoteUseCase
import com.example.holdingreview.domain.usecase.RefreshQuotesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 关注列表和添加关注 ViewModel 的单元测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchViewModelTest {
    /** 测试主线程调度器。 */
    private val dispatcher = StandardTestDispatcher()

    /**
     * 将 Main 调度器替换为测试调度器。
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    /**
     * 恢复 Main 调度器。
     */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 关注列表应该按代码合并持仓和关注股票，且持仓优先。
     */
    @Test
    fun `watch list merges holdings and watch stocks`() = runTest(dispatcher) {
        val repository = FakePortfolioRepository(
            holdings = listOf(holding()),
            watchStocks = listOf(
                watchStock(symbol = "600519", name = "贵州茅台", reason = "核心观察", tags = "白酒"),
                watchStock(symbol = "688981", name = "中芯国际", reason = "观察半导体", tags = "半导体")
            )
        )
        val viewModel = WatchListViewModel(repository, RefreshQuotesUseCase(repository))
        val collectJob = collectWatchListState(viewModel)

        advanceUntilIdle()

        val items = viewModel.uiState.value.items
        assertEquals(listOf("600519", "688981"), items.map { it.symbol })
        assertTrue(items[0].isHolding)
        assertTrue(items[0].isWatched)
        assertEquals("核心观察", items[0].reason)
        assertEquals("白酒", items[0].industry)
        assertFalse(items[1].isHolding)
        assertTrue(items[1].isWatched)
        collectJob.cancel()
    }

    /**
     * 输入 6 位代码查询成功后，应填充股票名称和建议行业，并保存接口返回值。
     */
    @Test
    fun `watch edit lookup success fills quote and saves inferred industry`() = runTest(dispatcher) {
        val repository = FakePortfolioRepository(lookupResult = Result.success(quoteSnapshot()))
        val viewModel = WatchEditViewModel(
            repository = repository,
            lookupQuoteUseCase = LookupQuoteUseCase(repository),
            inferIndustry = InferIndustryUseCase()
        )
        val collectJob = collectWatchEditState(viewModel)

        viewModel.lookupQuote("600519")
        advanceUntilIdle()

        assertEquals("贵州茅台", viewModel.uiState.value.quote?.name)
        assertEquals("白酒", viewModel.uiState.value.suggestedIndustry)
        assertTrue(viewModel.save("600519", "", "核心观察", ""))
        advanceUntilIdle()

        assertNotNull(repository.savedWatchStock)
        assertEquals("贵州茅台", repository.savedWatchStock?.name)
        assertEquals("白酒", repository.savedWatchStock?.tags)
        collectJob.cancel()
    }

    /**
     * 查询失败后，应允许手动名称和行业保存。
     */
    @Test
    fun `watch edit lookup failure allows manual name save`() = runTest(dispatcher) {
        val repository = FakePortfolioRepository(lookupResult = Result.failure(RuntimeException("网络异常")))
        val viewModel = WatchEditViewModel(
            repository = repository,
            lookupQuoteUseCase = LookupQuoteUseCase(repository),
            inferIndustry = InferIndustryUseCase()
        )
        val collectJob = collectWatchEditState(viewModel)

        viewModel.lookupQuote("600519")
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.quote)
        assertTrue(viewModel.uiState.value.allowManualNameInput)
        assertTrue(viewModel.save("600519", "手填股票", "临时观察", "新能源"))
        advanceUntilIdle()

        assertEquals("手填股票", repository.savedWatchStock?.name)
        assertEquals("新能源", repository.savedWatchStock?.tags)
        collectJob.cancel()
    }

    /**
     * 启动关注列表状态收集，让 WhileSubscribed 状态流开始工作。
     */
    private fun TestScope.collectWatchListState(viewModel: WatchListViewModel) = launch {
        viewModel.uiState.collect {}
    }

    /**
     * 启动添加关注状态收集，让 WhileSubscribed 状态流开始工作。
     */
    private fun TestScope.collectWatchEditState(viewModel: WatchEditViewModel) = launch {
        viewModel.uiState.collect {}
    }

    /**
     * 构造测试持仓。
     */
    private fun holding(): Holding {
        return Holding(
            id = "holding-1",
            symbol = "600519",
            name = "贵州茅台",
            market = Market.SH,
            quantity = 100.0,
            costPrice = 1560.0,
            currentPrice = 1586.3,
            dayChangePercent = 1.24,
            note = "",
            updatedAtMillis = 0L
        )
    }

    /**
     * 构造测试关注股票。
     */
    private fun watchStock(symbol: String, name: String, reason: String, tags: String): WatchStock {
        return WatchStock(
            symbol = symbol,
            name = name,
            market = Market.fromSymbol(symbol),
            reason = reason,
            tags = tags,
            latestPrice = 10.0,
            dayChangePercent = -1.0,
            updatedAtMillis = 0L
        )
    }

    /**
     * 构造测试行情快照。
     */
    private fun quoteSnapshot(): QuoteSnapshot {
        return QuoteSnapshot(
            symbol = "600519",
            name = "贵州茅台",
            market = Market.SH,
            latestPrice = 1586.3,
            previousClose = 1566.88,
            changePercent = 1.24,
            volume = null,
            turnoverAmount = null,
            turnoverRate = null,
            amplitude = null,
            source = "Tencent",
            updatedAtMillis = 0L
        )
    }

    /**
     * 只实现本组测试需要的仓库行为。
     */
    private class FakePortfolioRepository(
        private val holdings: List<Holding> = emptyList(),
        private val watchStocks: List<WatchStock> = emptyList(),
        private val lookupResult: Result<QuoteSnapshot> = Result.failure(IllegalStateException("not configured"))
    ) : PortfolioRepository {
        /** 最近保存的关注股票输入。 */
        var savedWatchStock: WatchStockInput? = null

        override fun observeHoldings(): Flow<List<Holding>> = flowOf(holdings)

        override fun observeHolding(id: String): Flow<Holding?> = flowOf(null)

        override fun observeWatchStocks(): Flow<List<WatchStock>> = flowOf(watchStocks)

        override fun observeQuotes(): Flow<List<QuoteSnapshot>> = flowOf(emptyList())

        override fun observeLatestReview(): Flow<DailyReview?> = flowOf(null)

        override suspend fun lookupQuote(symbol: String): Result<QuoteSnapshot> = lookupResult

        override suspend fun seedIfEmpty() = Unit

        override suspend fun upsertHolding(input: HoldingInput) = Unit

        override suspend fun upsertOcrDraft(draft: OcrHoldingDraft) = Unit

        override suspend fun deleteHolding(id: String) = Unit

        override suspend fun upsertWatchStock(input: WatchStockInput) {
            savedWatchStock = input
        }

        override suspend fun deleteWatchStock(symbol: String) = Unit

        override suspend fun refreshQuotes(): Result<Int> = Result.success(0)

        override suspend fun saveDailyReview(review: DailyReview) = Unit
    }
}
