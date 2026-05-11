package com.example.holdingreview.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holdingreview.data.ocr.OcrTextRecognizer
import com.example.holdingreview.data.repository.PortfolioRepository
import com.example.holdingreview.domain.model.DailyReview
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.HoldingInput
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.MarketSignal
import com.example.holdingreview.domain.model.OcrHoldingDraft
import com.example.holdingreview.domain.model.PortfolioSnapshot
import com.example.holdingreview.domain.model.ReviewDraft
import com.example.holdingreview.domain.model.WatchStock
import com.example.holdingreview.domain.model.WatchStockInput
import com.example.holdingreview.domain.usecase.AnalyzeMarketSignalsUseCase
import com.example.holdingreview.domain.usecase.CalculatePortfolioUseCase
import com.example.holdingreview.domain.usecase.GenerateDailyReviewUseCase
import com.example.holdingreview.domain.usecase.ParseOcrHoldingUseCase
import com.example.holdingreview.domain.usecase.RefreshQuotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val holdings: List<Holding> = emptyList(),
    val watchStocks: List<WatchStock> = emptyList(),
    val snapshot: PortfolioSnapshot = PortfolioSnapshot(),
    val signals: List<MarketSignal> = emptyList(),
    val latestReview: DailyReview? = null,
    val isRefreshing: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: PortfolioRepository,
    private val calculatePortfolio: CalculatePortfolioUseCase,
    private val analyzeMarketSignals: AnalyzeMarketSignalsUseCase,
    private val refreshQuotes: RefreshQuotesUseCase
) : ViewModel() {
    private val isRefreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeHoldings(),
        repository.observeWatchStocks(),
        repository.observeLatestReview(),
        isRefreshing,
        message
    ) { holdings, watchStocks, latestReview, refreshing, text ->
        HomeUiState(
            holdings = holdings,
            watchStocks = watchStocks,
            snapshot = calculatePortfolio(holdings),
            signals = analyzeMarketSignals(holdings, watchStocks),
            latestReview = latestReview,
            isRefreshing = refreshing,
            message = text
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(isRefreshing = true))

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            val result = refreshQuotes()
            message.value = result.fold(
                onSuccess = { count -> "行情已更新 $count 条" },
                onFailure = { "行情更新失败，显示缓存数据：${it.message ?: "网络异常"}" }
            )
            isRefreshing.value = false
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

data class HoldingEditUiState(
    val holding: Holding? = null
)

@HiltViewModel
class HoldingEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PortfolioRepository
) : ViewModel() {
    private val holdingId: String = savedStateHandle["holdingId"] ?: "new"
    val uiState: StateFlow<HoldingEditUiState> = (
        if (holdingId == "new") flowOf(null) else repository.observeHolding(holdingId)
    ).map { holding -> HoldingEditUiState(holding) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HoldingEditUiState())

    fun save(input: HoldingInput) {
        viewModelScope.launch {
            repository.upsertHolding(input)
        }
    }

    fun deleteCurrent() {
        if (holdingId == "new") return
        viewModelScope.launch {
            repository.deleteHolding(holdingId)
        }
    }
}

data class WatchListUiState(
    val watchStocks: List<WatchStock> = emptyList(),
    val isRefreshing: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class WatchListViewModel @Inject constructor(
    private val repository: PortfolioRepository,
    private val refreshQuotes: RefreshQuotesUseCase
) : ViewModel() {
    private val isRefreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<WatchListUiState> = combine(
        repository.observeWatchStocks(),
        isRefreshing,
        message
    ) { stocks, refreshing, text ->
        WatchListUiState(stocks, refreshing, text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchListUiState())

    fun add(input: WatchStockInput) {
        viewModelScope.launch {
            repository.upsertWatchStock(input)
            message.value = "已加入关注列表"
        }
    }

    fun delete(symbol: String) {
        viewModelScope.launch {
            repository.deleteWatchStock(symbol)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            message.value = refreshQuotes().fold(
                onSuccess = { "行情已更新 $it 条" },
                onFailure = { "行情更新失败，显示缓存数据" }
            )
            isRefreshing.value = false
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

data class OcrDraftForm(
    val id: String,
    val symbol: String,
    val name: String,
    val market: Market,
    val quantity: String,
    val costPrice: String,
    val currentPrice: String,
    val note: String,
    val confidence: Float
) {
    fun toDraft(): OcrHoldingDraft? {
        return OcrHoldingDraft(
            id = id,
            symbol = symbol.trim(),
            name = name.trim(),
            market = if (market == Market.UNKNOWN) Market.fromSymbol(symbol) else market,
            quantity = quantity.toDoubleOrNull() ?: return null,
            costPrice = costPrice.toDoubleOrNull() ?: return null,
            currentPrice = currentPrice.toDoubleOrNull() ?: return null,
            note = note.trim(),
            confidence = confidence
        )
    }
}

data class OcrImportUiState(
    val isProcessing: Boolean = false,
    val rawText: String = "",
    val drafts: List<OcrDraftForm> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class OcrImportViewModel @Inject constructor(
    private val ocrTextRecognizer: OcrTextRecognizer,
    private val parseOcrHolding: ParseOcrHoldingUseCase,
    private val repository: PortfolioRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OcrImportUiState())
    val uiState: StateFlow<OcrImportUiState> = _uiState

    fun recognizeImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, message = null) }
            val result = ocrTextRecognizer.recognize(uri)
            result.fold(
                onSuccess = { raw ->
                    val drafts = parseOcrHolding(raw).map { it.toForm() }
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            rawText = raw,
                            drafts = drafts,
                            message = if (drafts.isEmpty()) "未识别到持仓字段，请换一张更清晰的截图" else "识别到 ${drafts.size} 条候选持仓"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isProcessing = false, message = "OCR 识别失败：${error.message ?: "图片无法读取"}")
                    }
                }
            )
        }
    }

    fun updateDraft(index: Int, draft: OcrDraftForm) {
        _uiState.update { state ->
            state.copy(drafts = state.drafts.toMutableList().also { it[index] = draft })
        }
    }

    fun confirmDrafts() {
        viewModelScope.launch {
            val validDrafts = _uiState.value.drafts.mapNotNull { it.toDraft() }
            validDrafts.forEach { repository.upsertOcrDraft(it) }
            _uiState.update { it.copy(message = "已确认导入 ${validDrafts.size} 条持仓") }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun OcrHoldingDraft.toForm(): OcrDraftForm {
        return OcrDraftForm(
            id = id,
            symbol = symbol,
            name = name,
            market = market,
            quantity = quantity.takeIf { it > 0 }?.toString().orEmpty(),
            costPrice = costPrice.takeIf { it > 0 }?.toString().orEmpty(),
            currentPrice = currentPrice.takeIf { it > 0 }?.toString().orEmpty(),
            note = note,
            confidence = confidence
        )
    }
}

data class ReviewUiState(
    val holdings: List<Holding> = emptyList(),
    val signals: List<MarketSignal> = emptyList(),
    val draft: ReviewDraft? = null,
    val latestReview: DailyReview? = null,
    val message: String? = null
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: PortfolioRepository,
    private val analyzeMarketSignals: AnalyzeMarketSignalsUseCase,
    private val generateDailyReview: GenerateDailyReviewUseCase
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    val uiState: StateFlow<ReviewUiState> = combine(
        repository.observeHoldings(),
        repository.observeWatchStocks(),
        repository.observeLatestReview(),
        message
    ) { holdings, watchStocks, latestReview, text ->
        val signals = analyzeMarketSignals(holdings, watchStocks)
        ReviewUiState(
            holdings = holdings,
            signals = signals,
            draft = if (holdings.isEmpty()) null else generateDailyReview(holdings, signals),
            latestReview = latestReview,
            message = text
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    fun saveCurrentReview() {
        val draft = uiState.value.draft ?: return
        viewModelScope.launch {
            repository.saveDailyReview(generateDailyReview.toDailyReview(draft))
            message.value = "今日复盘已保存"
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
