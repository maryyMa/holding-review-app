package com.example.holdingreview.presentation

import androidx.lifecycle.SavedStateHandle
import com.example.holdingreview.data.repository.PortfolioRepository
import com.example.holdingreview.domain.model.DailyReview
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.HoldingInput
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.OcrHoldingDraft
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.WatchStock
import com.example.holdingreview.domain.model.WatchStockInput
import com.example.holdingreview.domain.usecase.LookupQuoteUseCase
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
 * 持仓编辑 ViewModel 的自动行情查询测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HoldingEditViewModelTest {
    /** 测试主线程调度器。 */
    private val dispatcher = StandardTestDispatcher()

    /**
     * 将 ViewModel 使用的 Main 调度器切换为测试调度器。
     */
    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    /**
     * 恢复 Main 调度器，避免影响其他测试。
     */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 查询成功后，状态里应包含股票名称和现价。
     */
    @Test
    fun `lookup quote populates quote state`() = runTest(dispatcher) {
        val quote = quoteSnapshot()
        val repository = FakePortfolioRepository(lookupResult = Result.success(quote))
        val viewModel = newViewModel(repository)
        val collectJob = collectUiState(viewModel)

        viewModel.lookupQuote("600519")
        advanceUntilIdle()

        assertEquals(1, repository.lookupCalls)
        assertEquals("600519", viewModel.uiState.value.quote?.symbol)
        assertEquals("贵州茅台", viewModel.uiState.value.quote?.name)
        assertEquals(1586.3, viewModel.uiState.value.quote?.latestPrice ?: 0.0, 0.01)
        assertFalse(viewModel.uiState.value.isLookingUp)
        assertNull(viewModel.uiState.value.lookupError)
        assertFalse(viewModel.uiState.value.allowManualQuoteInput)
        collectJob.cancel()
    }

    /**
     * 查询失败后，应允许用户手动填写名称和现价。
     */
    @Test
    fun `lookup failure enables manual quote input`() = runTest(dispatcher) {
        val repository = FakePortfolioRepository(lookupResult = Result.failure(RuntimeException("网络异常")))
        val viewModel = newViewModel(repository)
        val collectJob = collectUiState(viewModel)

        viewModel.lookupQuote("600519")
        advanceUntilIdle()

        assertEquals(1, repository.lookupCalls)
        assertNull(viewModel.uiState.value.quote)
        assertFalse(viewModel.uiState.value.isLookingUp)
        assertNotNull(viewModel.uiState.value.lookupError)
        assertTrue(viewModel.uiState.value.allowManualQuoteInput)
        collectJob.cancel()
    }

    /**
     * 创建被测 ViewModel。
     */
    private fun newViewModel(repository: FakePortfolioRepository): HoldingEditViewModel {
        return HoldingEditViewModel(
            savedStateHandle = SavedStateHandle(mapOf("holdingId" to "new")),
            repository = repository,
            lookupQuoteUseCase = LookupQuoteUseCase(repository)
        )
    }

    /**
     * 启动 uiState 收集，使 WhileSubscribed 的状态流开始工作。
     */
    private fun TestScope.collectUiState(viewModel: HoldingEditViewModel) = launch {
        viewModel.uiState.collect {}
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
     * 只实现本测试需要的仓库行为。
     */
    private class FakePortfolioRepository(
        private val lookupResult: Result<QuoteSnapshot>
    ) : PortfolioRepository {
        /** 单股查询调用次数。 */
        var lookupCalls: Int = 0

        override fun observeHoldings(): Flow<List<Holding>> = flowOf(emptyList())

        override fun observeHolding(id: String): Flow<Holding?> = flowOf(null)

        override fun observeWatchStocks(): Flow<List<WatchStock>> = flowOf(emptyList())

        override fun observeQuotes(): Flow<List<QuoteSnapshot>> = flowOf(emptyList())

        override fun observeLatestReview(): Flow<DailyReview?> = flowOf(null)

        override suspend fun lookupQuote(symbol: String): Result<QuoteSnapshot> {
            lookupCalls += 1
            return lookupResult
        }

        override suspend fun seedIfEmpty() = Unit

        override suspend fun upsertHolding(input: HoldingInput) = Unit

        override suspend fun upsertOcrDraft(draft: OcrHoldingDraft) = Unit

        override suspend fun deleteHolding(id: String) = Unit

        override suspend fun upsertWatchStock(input: WatchStockInput) = Unit

        override suspend fun deleteWatchStock(symbol: String) = Unit

        override suspend fun refreshQuotes(): Result<Int> = Result.success(0)

        override suspend fun saveDailyReview(review: DailyReview) = Unit
    }
}
