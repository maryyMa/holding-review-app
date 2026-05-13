package com.example.holdingreview.presentation

import androidx.lifecycle.SavedStateHandle
import com.example.holdingreview.data.repository.PortfolioRepository
import com.example.holdingreview.data.repository.StockMonitorRepository
import com.example.holdingreview.domain.model.DailyReview
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.HoldingInput
import com.example.holdingreview.domain.model.KLinePoint
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.MonitorAlert
import com.example.holdingreview.domain.model.MonitorAlertLevel
import com.example.holdingreview.domain.model.MonitorAlertType
import com.example.holdingreview.domain.model.MonitorConfig
import com.example.holdingreview.domain.model.MonitorTarget
import com.example.holdingreview.domain.model.OcrHoldingDraft
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.TradeOperation
import com.example.holdingreview.domain.model.TradeOperationInput
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
        val stockRepository = FakeStockMonitorRepository(
            alerts = listOf(
                alert("alert-1", "600519", MonitorAlertLevel.INFO, 1_000L),
                alert("alert-2", "600519", MonitorAlertLevel.WARNING, 2_000L),
                alert("alert-3", "688981", MonitorAlertLevel.CRITICAL, 3_000L)
            )
        )
        val viewModel = WatchListViewModel(repository, stockRepository, RefreshQuotesUseCase(repository))
        val collectJob = collectWatchListState(viewModel)

        advanceUntilIdle()

        val items = viewModel.uiState.value.items
        assertEquals(listOf("600519", "688981"), items.map { it.symbol })
        assertTrue(items[0].isHolding)
        assertTrue(items[0].isWatched)
        assertEquals("核心观察", items[0].reason)
        assertEquals("白酒", items[0].industry)
        assertEquals(2, items[0].alertCount)
        assertEquals(25.0, items[0].watchChangePercent ?: 0.0, 0.001)
        assertFalse(items[1].isHolding)
        assertTrue(items[1].isWatched)
        assertEquals(1, items[1].alertCount)
        collectJob.cancel()
    }

    @Test
    fun `stock alerts sort by severity then newest time`() = runTest(dispatcher) {
        val stockRepository = FakeStockMonitorRepository(
            alerts = listOf(
                alert("info-new", "600519", MonitorAlertLevel.INFO, 3_000L),
                alert("critical-old", "600519", MonitorAlertLevel.CRITICAL, 1_000L),
                alert("warning-new", "600519", MonitorAlertLevel.WARNING, 4_000L),
                alert("critical-new", "600519", MonitorAlertLevel.CRITICAL, 2_000L)
            )
        )
        val viewModel = WatchAlertsViewModel(
            SavedStateHandle(mapOf("symbol" to "600519")),
            FakePortfolioRepository(),
            stockRepository
        )
        val collectJob = launch { viewModel.uiState.collect {} }

        advanceUntilIdle()

        assertEquals(
            listOf("critical-new", "critical-old", "warning-new", "info-new"),
            viewModel.uiState.value.alerts.map { it.id }
        )
        collectJob.cancel()
    }

    @Test
    fun `delete watch stock also deletes stock alerts`() = runTest(dispatcher) {
        val repository = FakePortfolioRepository()
        val stockRepository = FakeStockMonitorRepository()
        val viewModel = WatchListViewModel(repository, stockRepository, RefreshQuotesUseCase(repository))

        viewModel.delete("600519")
        advanceUntilIdle()

        assertEquals("600519", repository.deletedWatchSymbol)
        assertEquals("600519", stockRepository.deletedAlertsSymbol)
    }

    @Test
    fun `watch alert detail clears only current stock read alerts`() = runTest(dispatcher) {
        val stockRepository = FakeStockMonitorRepository()
        val viewModel = WatchAlertsViewModel(
            SavedStateHandle(mapOf("symbol" to "600519")),
            FakePortfolioRepository(),
            stockRepository
        )

        viewModel.clearReadAlerts()
        advanceUntilIdle()

        assertEquals("600519", stockRepository.clearedReadSymbol)
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
        var saved = false
        viewModel.save("600519", "", "核心观察", "") { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
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
        var saved = false
        viewModel.save("600519", "手填股票", "临时观察", "新能源") { saved = true }
        advanceUntilIdle()

        assertTrue(saved)
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
            watchedAtMillis = 0L,
            watchBaseClose = if (symbol == "600519") 1269.04 else 8.0,
            watchBaseCloseDate = "2026-05-11",
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

    private fun alert(id: String, symbol: String, level: MonitorAlertLevel, triggeredAtMillis: Long): MonitorAlert {
        return MonitorAlert(
            id = id,
            symbol = symbol,
            name = "测试股票",
            market = Market.fromSymbol(symbol),
            level = level,
            type = MonitorAlertType.CHANGE_RISE,
            title = "测试预警",
            message = "测试消息",
            latestPrice = 10.0,
            changePercent = 1.0,
            triggeredAtMillis = triggeredAtMillis,
            isRead = false
        )
    }

    private class FakeStockMonitorRepository(
        private val alerts: List<MonitorAlert> = emptyList(),
        private val targets: List<MonitorTarget> = emptyList()
    ) : StockMonitorRepository {
        var clearedReadAll: Boolean = false
        var clearedReadSymbol: String? = null
        var deletedAlertsSymbol: String? = null

        override fun observeTargets(): Flow<List<MonitorTarget>> = flowOf(targets)

        override fun observeAlerts(): Flow<List<MonitorAlert>> = flowOf(alerts)

        override fun observeAlert(id: String): Flow<MonitorAlert?> = flowOf(alerts.firstOrNull { it.id == id })

        override fun observeUnreadAlertCount(): Flow<Int> = flowOf(alerts.count { !it.isRead })

        override fun observeConfigs(): Flow<List<MonitorConfig>> = flowOf(emptyList())

        override fun observeConfig(symbol: String): Flow<MonitorConfig?> = flowOf(null)

        override suspend fun getTargets(): List<MonitorTarget> = targets

        override suspend fun getConfig(symbol: String, market: Market): MonitorConfig = MonitorConfig.defaultFor(symbol, market)

        override suspend fun upsertConfig(config: MonitorConfig) = Unit

        override suspend fun updateConfigEnabled(symbol: String, enabled: Boolean) = Unit

        override suspend fun updateHighestPrice(symbol: String, highestPrice: Double) = Unit

        override suspend fun fetchQuotes(symbols: List<String>): Result<List<QuoteSnapshot>> = Result.success(emptyList())

        override suspend fun fetchKLines(symbol: String, market: Market, limit: Int): Result<List<KLinePoint>> = Result.success(emptyList())

        override suspend fun hasRecentAlert(symbol: String, type: MonitorAlertType, afterMillis: Long): Boolean = false

        override suspend fun insertAlerts(alerts: List<MonitorAlert>) = Unit

        override suspend fun markAlertRead(id: String) = Unit

        override suspend fun markAllAlertsRead() = Unit

        override suspend fun clearReadAlerts() {
            clearedReadAll = true
        }

        override suspend fun clearReadAlerts(symbol: String) {
            clearedReadSymbol = symbol
        }

        override suspend fun deleteAlertsForSymbol(symbol: String) {
            deletedAlertsSymbol = symbol
        }
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
        var deletedWatchSymbol: String? = null

        override fun observeHoldings(): Flow<List<Holding>> = flowOf(holdings)

        override fun observeHolding(id: String): Flow<Holding?> = flowOf(null)

        override fun observeWatchStocks(): Flow<List<WatchStock>> = flowOf(watchStocks)

        override fun observeTradeOperations(symbol: String): Flow<List<TradeOperation>> = flowOf(emptyList())

        override fun observeQuotes(): Flow<List<QuoteSnapshot>> = flowOf(emptyList())

        override fun observeLatestReview(): Flow<DailyReview?> = flowOf(null)

        override suspend fun lookupQuote(symbol: String): Result<QuoteSnapshot> = lookupResult

        override suspend fun seedIfEmpty(): Result<Boolean> = Result.success(false)

        override suspend fun upsertHolding(input: HoldingInput) = Unit

        override suspend fun upsertOcrDraft(draft: OcrHoldingDraft) = Unit

        override suspend fun deleteHolding(id: String) = Unit

        override suspend fun upsertWatchStock(input: WatchStockInput) {
            savedWatchStock = input
        }

        override suspend fun deleteWatchStock(symbol: String) {
            deletedWatchSymbol = symbol
        }

        override suspend fun addTradeOperation(input: TradeOperationInput): Result<TradeOperation> =
            Result.failure(UnsupportedOperationException("not configured"))

        override suspend fun refreshQuotes(): Result<Int> = Result.success(0)

        override suspend fun saveDailyReview(review: DailyReview) = Unit
    }
}
