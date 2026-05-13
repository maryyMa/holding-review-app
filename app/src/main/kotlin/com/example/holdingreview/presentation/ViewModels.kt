package com.example.holdingreview.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.holdingreview.data.ocr.OcrTextRecognizer
import com.example.holdingreview.data.repository.PortfolioRepository
import com.example.holdingreview.data.repository.StockMonitorRepository
import com.example.holdingreview.domain.model.DailyReview
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.HoldingInput
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.MarketSignal
import com.example.holdingreview.domain.model.MonitorAlert
import com.example.holdingreview.domain.model.MonitorAlertLevel
import com.example.holdingreview.domain.model.MonitorConfig
import com.example.holdingreview.domain.model.MonitorTarget
import com.example.holdingreview.domain.model.OcrHoldingDraft
import com.example.holdingreview.domain.model.PortfolioSnapshot
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.ReviewDraft
import com.example.holdingreview.domain.model.TradeOperation
import com.example.holdingreview.domain.model.TradeOperationInput
import com.example.holdingreview.domain.model.TradeOperationSide
import com.example.holdingreview.domain.model.WatchStock
import com.example.holdingreview.domain.model.WatchStockInput
import com.example.holdingreview.domain.usecase.AnalyzeMarketSignalsUseCase
import com.example.holdingreview.domain.usecase.CalculatePortfolioUseCase
import com.example.holdingreview.domain.usecase.GenerateDailyReviewUseCase
import com.example.holdingreview.domain.usecase.InferIndustryUseCase
import com.example.holdingreview.domain.usecase.LookupQuoteUseCase
import com.example.holdingreview.domain.usecase.ParseOcrHoldingUseCase
import com.example.holdingreview.domain.usecase.RefreshQuotesUseCase
import com.example.holdingreview.domain.usecase.RunStockMonitorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

/**
 * 首页仪表盘的 UI 状态。
 */
data class HomeUiState(
    /** 聚合后的组合指标。 */
    val snapshot: PortfolioSnapshot = PortfolioSnapshot(),
    /** 监控模块生成的股票预警。 */
    val alerts: List<MonitorAlert> = emptyList(),
    /** 当前监控覆盖的股票数量。 */
    val targetCount: Int = 0,
    /** 尚未阅读的预警数量。 */
    val unreadCount: Int = 0,
    /** 最近保存的每日复盘；没有则为空。 */
    val latestReview: DailyReview? = null,
    /** 行情刷新进行中时为 true。 */
    val isRefreshing: Boolean = false,
    /** 手动监控执行中时为 true。 */
    val isRunningMonitor: Boolean = false,
    /** 页面显示的一次性消息。 */
    val message: String? = null
)

private data class HomePortfolioState(
    val holdings: List<Holding>,
    val latestReview: DailyReview?,
    val isRefreshing: Boolean,
    val message: String?
)

private data class HomeMonitorState(
    val alerts: List<MonitorAlert>,
    val targetCount: Int,
    val unreadCount: Int,
    val isRunning: Boolean
)

/**
 * 首页仪表盘和组合概览的 ViewModel。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    /** 提供持仓、关注股票和复盘数据流的仓库。 */
    private val repository: PortfolioRepository,
    /** 提供监控预警和监控目标。 */
    private val stockMonitorRepository: StockMonitorRepository,
    /** 计算组合聚合指标。 */
    private val calculatePortfolio: CalculatePortfolioUseCase,
    /** 刷新本地缓存的行情数据。 */
    private val refreshQuotes: RefreshQuotesUseCase,
    /** 立即执行一次股票监控。 */
    private val runStockMonitor: RunStockMonitorUseCase
) : ViewModel() {
    /** 通过 [uiState] 暴露的内部刷新状态。 */
    private val isRefreshing = MutableStateFlow(false)
    /** 通过 [uiState] 暴露的内部监控执行状态。 */
    private val isRunningMonitor = MutableStateFlow(false)
    /** 通过 [uiState] 暴露的内部消息通道。 */
    private val message = MutableStateFlow<String?>(null)

    private val portfolioState = combine(
        repository.observeHoldings(),
        repository.observeLatestReview(),
        isRefreshing,
        message
    ) { holdings, latestReview, refreshing, text ->
        HomePortfolioState(holdings, latestReview, refreshing, text)
    }

    private val monitorState = combine(
        stockMonitorRepository.observeAlerts(),
        stockMonitorRepository.observeTargets(),
        stockMonitorRepository.observeUnreadAlertCount(),
        isRunningMonitor
    ) { alerts, targets, unreadCount, running ->
        HomeMonitorState(
            alerts = alerts.sortedForDisplay(),
            targetCount = targets.size,
            unreadCount = unreadCount,
            isRunning = running
        )
    }

    /** 首页路由消费的合并后屏幕状态。 */
    val uiState: StateFlow<HomeUiState> = combine(portfolioState, monitorState) { portfolio, monitor ->
        HomeUiState(
            snapshot = calculatePortfolio(portfolio.holdings),
            alerts = monitor.alerts,
            targetCount = monitor.targetCount,
            unreadCount = monitor.unreadCount,
            latestReview = portfolio.latestReview,
            isRefreshing = portfolio.isRefreshing,
            isRunningMonitor = monitor.isRunning,
            message = portfolio.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(isRefreshing = true))

    init {
        viewModelScope.launch {
            repository.seedIfEmpty().onFailure {
                message.value = "个人数据导入失败：${it.message ?: "JSON 格式异常"}"
            }
        }
    }

    /**
     * 刷新行情数据并发布完成消息。
     */
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

    fun runMonitorNow() {
        viewModelScope.launch {
            isRunningMonitor.value = true
            val result = runStockMonitor()
            message.value = result.fold(
                onSuccess = { "已检查 ${it.checkedCount} 只股票，新增 ${it.alertCount} 条预警" },
                onFailure = { "监控执行失败：${it.message ?: "网络异常"}" }
            )
            isRunningMonitor.value = false
        }
    }

    fun markAllAlertsRead() {
        viewModelScope.launch {
            stockMonitorRepository.markAllAlertsRead()
        }
    }

    fun clearReadAlerts() {
        viewModelScope.launch {
            stockMonitorRepository.clearReadAlerts()
        }
    }

    /**
     * 清除当前显示的消息。
     */
    fun clearMessage() {
        message.value = null
    }
}

/**
 * 持仓编辑页面的 UI 状态。
 */
data class HoldingEditUiState(
    /** 正在编辑的已有持仓；新增持仓时为空。 */
    val holding: Holding? = null,
    /** 股票代码自动查询成功后返回的行情。 */
    val quote: QuoteSnapshot? = null,
    /** 股票行情查询进行中时为 true。 */
    val isLookingUp: Boolean = false,
    /** 股票行情查询失败时展示的错误信息。 */
    val lookupError: String? = null,
    /** 查询失败后是否允许展示手动名称和现价输入。 */
    val allowManualQuoteInput: Boolean = false
)

/**
 * 持仓编辑页内部使用的行情查询状态。
 */
private data class HoldingQuoteLookupState(
    /** 查询成功后的行情快照。 */
    val quote: QuoteSnapshot? = null,
    /** 是否正在查询行情。 */
    val isLookingUp: Boolean = false,
    /** 查询失败信息。 */
    val lookupError: String? = null,
    /** 查询失败后是否启用手动输入兜底。 */
    val allowManualQuoteInput: Boolean = false
)

/**
 * 用于新建、编辑和删除单个持仓的 ViewModel。
 */
@HiltViewModel
class HoldingEditViewModel @Inject constructor(
    /** 携带持仓 id 参数的导航保存状态。 */
    savedStateHandle: SavedStateHandle,
    /** 用于加载和保存持仓的仓库。 */
    private val repository: PortfolioRepository,
    /** 用于根据股票代码查询名称和现价。 */
    private val lookupQuoteUseCase: LookupQuoteUseCase
) : ViewModel() {
    /** 持仓 id 路由参数；"new" 表示表单会创建新记录。 */
    private val holdingId: String = savedStateHandle["holdingId"] ?: "new"
    /** 内部行情查询状态。 */
    private val lookupState = MutableStateFlow(HoldingQuoteLookupState())
    /** 最近一次触发查询的股票代码，用于避免重复请求。 */
    private var lastLookupSymbol: String? = null
    /** 当前正在执行的查询任务。 */
    private var lookupJob: Job? = null
    /** 编辑路由下包含当前持仓和可用行情的屏幕状态。 */
    val uiState: StateFlow<HoldingEditUiState> = combine(
        if (holdingId == "new") flowOf<Holding?>(null) else repository.observeHolding(holdingId),
        repository.observeQuotes(),
        lookupState
    ) { holding, quotes, lookup ->
        val cachedQuote = holding?.let { currentHolding ->
            quotes.firstOrNull { it.symbol == currentHolding.symbol }
        }
        val quote = lookup.quote ?: cachedQuote.takeUnless {
            lookup.isLookingUp || lookup.lookupError != null || lookup.allowManualQuoteInput
        }
        HoldingEditUiState(
            holding = holding,
            quote = quote,
            isLookingUp = lookup.isLookingUp,
            lookupError = lookup.lookupError,
            allowManualQuoteInput = lookup.allowManualQuoteInput
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HoldingEditUiState())

    /**
     * 输入满 6 位股票代码后自动查询名称和现价。
     */
    fun lookupQuote(symbol: String) {
        val normalizedSymbol = symbol.trim()
        if (normalizedSymbol.length != 6) {
            lastLookupSymbol = null
            lookupJob?.cancel()
            lookupState.value = HoldingQuoteLookupState()
            return
        }

        val currentLookup = lookupState.value
        if (lastLookupSymbol == normalizedSymbol && (currentLookup.isLookingUp || currentLookup.quote?.symbol == normalizedSymbol)) {
            return
        }

        lastLookupSymbol = normalizedSymbol
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            lookupState.value = HoldingQuoteLookupState(isLookingUp = true)
            val result = lookupQuoteUseCase(normalizedSymbol)
            lookupState.value = result.fold(
                onSuccess = { quote -> HoldingQuoteLookupState(quote = quote) },
                onFailure = { error ->
                    HoldingQuoteLookupState(
                        lookupError = error.message ?: "股票查询失败，请手动填写名称和现价",
                        allowManualQuoteInput = true
                    )
                }
            )
        }
    }

    /**
     * 根据校验后的表单输入保存持仓。
     */
    fun save(input: HoldingInput) {
        viewModelScope.launch {
            repository.upsertHolding(input)
        }
    }

    /**
     * 编辑已有记录时删除当前加载的持仓。
     */
    fun deleteCurrent() {
        if (holdingId == "new") return
        viewModelScope.launch {
            repository.deleteHolding(holdingId)
        }
    }
}

/**
 * 关注 Tab 中展示的统一股票行，可来自持仓、关注列表或两者合并。
 */
data class WatchListItem(
    /** 六位股票代码。 */
    val symbol: String,
    /** 展示名称，持仓优先使用合并行情后的名称。 */
    val name: String,
    /** 用于展示交易市场。 */
    val market: Market,
    /** 关注原因；只有关注列表中存在该股票时通常有值。 */
    val reason: String,
    /** 行业文本，复用关注股票的 tags 字段保存。 */
    val industry: String,
    /** 最新已知价格。 */
    val latestPrice: Double?,
    /** 最新日涨跌幅。 */
    val dayChangePercent: Double?,
    /** 当前市值；未持仓时为空。 */
    val marketValue: Double?,
    /** 总盈亏；未持仓时为空。 */
    val totalProfit: Double?,
    /** 持仓成本价；未持仓时为空。 */
    val costPrice: Double?,
    /** 持仓数量；未持仓时为空。 */
    val quantity: Double?,
    /** 当日盈亏；未持仓时为空。 */
    val dayProfit: Double?,
    /** 从关注基准收盘价到最新价的涨跌幅；没有关注基准时为空。 */
    val watchChangePercent: Double?,
    /** 该股票历史预警数量。 */
    val alertCount: Int,
    /** 该股票是否已经在持仓中。 */
    val isHolding: Boolean,
    /** 该股票是否也存在于关注列表中。 */
    val isWatched: Boolean
)

/**
 * 关注列表页面的 UI 状态。
 */
data class WatchListUiState(
    /** 已按持仓优先合并后的股票列表。 */
    val items: List<WatchListItem> = emptyList(),
    /** 行情刷新进行中时为 true。 */
    val isRefreshing: Boolean = false,
    /** 页面显示的一次性消息。 */
    val message: String? = null
)

/**
 * 用于添加、删除和刷新关注股票的 ViewModel。
 */
@HiltViewModel
class WatchListViewModel @Inject constructor(
    /** 用于持久化关注列表的仓库。 */
    private val repository: PortfolioRepository,
    /** 用于读取每只股票预警数量。 */
    private val stockMonitorRepository: StockMonitorRepository,
    /** 刷新本地缓存的行情数据。 */
    private val refreshQuotes: RefreshQuotesUseCase
) : ViewModel() {
    /** 通过 [uiState] 暴露的内部刷新状态。 */
    private val isRefreshing = MutableStateFlow(false)
    /** 通过 [uiState] 暴露的内部消息通道。 */
    private val message = MutableStateFlow<String?>(null)

    /** 关注列表路由消费的合并后屏幕状态。 */
    val uiState: StateFlow<WatchListUiState> = combine(
        repository.observeHoldings(),
        repository.observeWatchStocks(),
        stockMonitorRepository.observeAlerts(),
        isRefreshing,
        message
    ) { holdings, stocks, alerts, refreshing, text ->
        val watchMap = stocks.associateBy { it.symbol }
        val alertCounts = alerts.groupingBy { it.symbol }.eachCount()
        val holdingSymbols = holdings.map { it.symbol }.toSet()
        val holdingItems = holdings.map { holding ->
            val watch = watchMap[holding.symbol]
            WatchListItem(
                symbol = holding.symbol,
                name = holding.name,
                market = holding.market,
                reason = watch?.reason.orEmpty(),
                industry = watch?.tags.orEmpty(),
                latestPrice = holding.currentPrice,
                dayChangePercent = holding.dayChangePercent,
                marketValue = holding.marketValue,
                totalProfit = holding.totalProfit,
                costPrice = holding.costPrice,
                quantity = holding.quantity,
                dayProfit = holding.dayProfit,
                watchChangePercent = watch?.watchChangePercentFrom(holding.currentPrice),
                alertCount = alertCounts[holding.symbol] ?: 0,
                isHolding = true,
                isWatched = watch != null
            )
        }
        val watchOnlyItems = stocks.filterNot { it.symbol in holdingSymbols }.map { stock ->
            WatchListItem(
                symbol = stock.symbol,
                name = stock.name,
                market = stock.market,
                reason = stock.reason,
                industry = stock.tags,
                latestPrice = stock.latestPrice,
                dayChangePercent = stock.dayChangePercent,
                marketValue = null,
                totalProfit = null,
                costPrice = null,
                quantity = null,
                dayProfit = null,
                watchChangePercent = stock.watchChangePercentFrom(stock.latestPrice),
                alertCount = alertCounts[stock.symbol] ?: 0,
                isHolding = false,
                isWatched = true
            )
        }
        WatchListUiState((holdingItems + watchOnlyItems).sortedByStockName(), refreshing, text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchListUiState())

    /**
     * 添加或更新关注股票。
     */
    fun add(input: WatchStockInput) {
        viewModelScope.launch {
            repository.upsertWatchStock(input)
            message.value = "已加入关注列表"
        }
    }

    /**
     * 根据股票代码删除关注股票。
     */
    fun delete(symbol: String) {
        viewModelScope.launch {
            repository.deleteWatchStock(symbol)
            stockMonitorRepository.deleteAlertsForSymbol(symbol)
        }
    }

    /**
     * 刷新行情数据并发布完成消息。
     */
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

    /**
     * 清除当前显示的消息。
     */
    fun clearMessage() {
        message.value = null
    }
}

/**
 * 添加关注股票页面的 UI 状态。
 */
data class WatchEditUiState(
    /** 自动查询成功后返回的行情。 */
    val quote: QuoteSnapshot? = null,
    /** 自动查询进行中时为 true。 */
    val isLookingUp: Boolean = false,
    /** 保存关注股票进行中时为 true。 */
    val isSaving: Boolean = false,
    /** 查询失败时展示的错误信息。 */
    val lookupError: String? = null,
    /** 查询失败后是否允许手动填写股票名称。 */
    val allowManualNameInput: Boolean = false,
    /** 查询成功后按代码和名称推断出的行业。 */
    val suggestedIndustry: String? = null,
    /** 保存或校验失败时展示的消息。 */
    val message: String? = null
)

/**
 * 添加关注股票页面内部使用的查询状态。
 */
private data class WatchQuoteLookupState(
    /** 查询成功后的行情快照。 */
    val quote: QuoteSnapshot? = null,
    /** 是否正在查询行情。 */
    val isLookingUp: Boolean = false,
    /** 查询失败信息。 */
    val lookupError: String? = null,
    /** 查询失败后是否启用手动名称输入。 */
    val allowManualNameInput: Boolean = false,
    /** 自动推断出的行业建议。 */
    val suggestedIndustry: String? = null
)

/**
 * 用于添加关注股票并自动补全名称和行业的 ViewModel。
 */
@HiltViewModel
class WatchEditViewModel @Inject constructor(
    /** 负责保存关注股票和查询行情的仓库。 */
    private val repository: PortfolioRepository,
    /** 根据股票代码查询名称、市场和现价。 */
    private val lookupQuoteUseCase: LookupQuoteUseCase,
    /** 在用户未填写行业时推断行业。 */
    private val inferIndustry: InferIndustryUseCase
) : ViewModel() {
    /** 内部行情查询状态。 */
    private val lookupState = MutableStateFlow(WatchQuoteLookupState())
    /** 保存进行中状态。 */
    private val isSaving = MutableStateFlow(false)
    /** 页面提示消息。 */
    private val message = MutableStateFlow<String?>(null)
    /** 最近一次触发查询的股票代码，用于避免重复请求。 */
    private var lastLookupSymbol: String? = null
    /** 当前正在执行的查询任务。 */
    private var lookupJob: Job? = null

    /** 添加关注页面消费的屏幕状态。 */
    val uiState: StateFlow<WatchEditUiState> = combine(lookupState, isSaving, message) { lookup, saving, text ->
        WatchEditUiState(
            quote = lookup.quote,
            isLookingUp = lookup.isLookingUp,
            isSaving = saving,
            lookupError = lookup.lookupError,
            allowManualNameInput = lookup.allowManualNameInput,
            suggestedIndustry = lookup.suggestedIndustry,
            message = text
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchEditUiState())

    /**
     * 输入满 6 位股票代码后自动查询名称和行情。
     */
    fun lookupQuote(symbol: String) {
        val normalizedSymbol = symbol.trim()
        if (normalizedSymbol.length != 6) {
            lastLookupSymbol = null
            lookupJob?.cancel()
            lookupState.value = WatchQuoteLookupState()
            return
        }

        val currentLookup = lookupState.value
        if (lastLookupSymbol == normalizedSymbol && (currentLookup.isLookingUp || currentLookup.quote?.symbol == normalizedSymbol)) {
            return
        }

        lastLookupSymbol = normalizedSymbol
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            lookupState.value = WatchQuoteLookupState(isLookingUp = true)
            val result = lookupQuoteUseCase(normalizedSymbol)
            lookupState.value = result.fold(
                onSuccess = { quote ->
                    WatchQuoteLookupState(
                        quote = quote,
                        suggestedIndustry = inferIndustry(normalizedSymbol, quote.name, "")
                    )
                },
                onFailure = { error ->
                    WatchQuoteLookupState(
                        lookupError = error.message ?: "股票查询失败，请手动填写股票名称",
                        allowManualNameInput = true
                    )
                }
            )
        }
    }

    /**
     * 校验表单文本并保存关注股票。
     */
    fun save(symbol: String, name: String, reason: String, industry: String, onSaved: () -> Unit) {
        val normalizedSymbol = symbol.trim()
        val lookup = lookupState.value
        if (normalizedSymbol.length != 6 || lookup.isLookingUp || isSaving.value) {
            message.value = "请检查股票代码和股票名称"
            return
        }

        val quote = lookup.quote?.takeIf { it.symbol == normalizedSymbol }
        val resolvedName = quote?.name ?: name.trim()
        if (resolvedName.isBlank()) {
            message.value = "请填写股票名称"
            return
        }

        val input = WatchStockInput(
            symbol = normalizedSymbol,
            name = resolvedName,
            market = quote?.market ?: Market.fromSymbol(normalizedSymbol),
            reason = reason,
            tags = inferIndustry(normalizedSymbol, resolvedName, industry)
        )
        viewModelScope.launch {
            isSaving.value = true
            runCatching {
                repository.upsertWatchStock(input)
            }.fold(
                onSuccess = {
                    isSaving.value = false
                    onSaved()
                },
                onFailure = {
                    isSaving.value = false
                    message.value = "保存失败：${it.message ?: "请稍后重试"}"
                }
            )
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

/**
 * 导入确认表单使用的可编辑 OCR 草稿行。
 */
/**
 * 预警 Tab 的屏幕状态。
 */
data class MonitorUiState(
    val alerts: List<MonitorAlert> = emptyList(),
    val targets: List<MonitorTarget> = emptyList(),
    val configs: Map<String, MonitorConfig> = emptyMap(),
    val unreadCount: Int = 0,
    val isRunning: Boolean = false,
    val message: String? = null
)

/**
 * 股票预警列表和立即检查入口的 ViewModel。
 */
@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val repository: StockMonitorRepository,
    private val runStockMonitor: RunStockMonitorUseCase
) : ViewModel() {
    private val isRunning = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val baseState = combine(
        repository.observeAlerts(),
        repository.observeTargets(),
        repository.observeConfigs(),
        repository.observeUnreadAlertCount(),
        isRunning
    ) { alerts: List<MonitorAlert>, targets: List<MonitorTarget>, configs: List<MonitorConfig>, unreadCount: Int, running: Boolean ->
        MonitorUiState(
            alerts = alerts.sortedForDisplay(),
            targets = targets,
            configs = configs.associateBy { it.symbol },
            unreadCount = unreadCount,
            isRunning = running
        )
    }

    val uiState: StateFlow<MonitorUiState> = combine(baseState, message) { state, text ->
        state.copy(message = text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitorUiState())

    fun runNow() {
        viewModelScope.launch {
            isRunning.value = true
            val result = runStockMonitor()
            message.value = result.fold(
                onSuccess = { "已检查 ${it.checkedCount} 只股票，新增 ${it.alertCount} 条预警" },
                onFailure = { "监控执行失败：${it.message ?: "网络异常"}" }
            )
            isRunning.value = false
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            repository.markAllAlertsRead()
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

/**
 * 关注页单只股票预警列表状态。
 */
data class WatchAlertsUiState(
    val symbol: String = "",
    val holding: Holding? = null,
    val watchStock: WatchStock? = null,
    val target: MonitorTarget? = null,
    val config: MonitorConfig? = null,
    val alerts: List<MonitorAlert> = emptyList(),
    val operations: List<TradeOperation> = emptyList()
)

private data class WatchPortfolioDetailState(
    val holding: Holding?,
    val watchStock: WatchStock?,
    val operations: List<TradeOperation>
)

private data class WatchMonitorDetailState(
    val target: MonitorTarget?,
    val config: MonitorConfig?,
    val alerts: List<MonitorAlert>
)

/**
 * 根据股票代码展示该股票对应的全部预警。
 */
@HiltViewModel
class WatchAlertsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PortfolioRepository,
    private val stockMonitorRepository: StockMonitorRepository
) : ViewModel() {
    private val symbol: String = savedStateHandle["symbol"] ?: ""

    private val portfolioState = combine(
        repository.observeHoldings(),
        repository.observeWatchStocks(),
        repository.observeTradeOperations(symbol)
    ) { holdings, watchStocks, operations ->
        WatchPortfolioDetailState(
            holding = holdings.firstOrNull { it.symbol == symbol },
            watchStock = watchStocks.firstOrNull { it.symbol == symbol },
            operations = operations
        )
    }

    private val monitorState = combine(
        stockMonitorRepository.observeAlerts(),
        stockMonitorRepository.observeTargets(),
        stockMonitorRepository.observeConfig(symbol)
    ) { alerts, targets, config ->
        WatchMonitorDetailState(
            target = targets.firstOrNull { it.symbol == symbol },
            config = config,
            alerts = alerts.filter { it.symbol == symbol }.sortedForDisplay()
        )
    }

    val uiState: StateFlow<WatchAlertsUiState> = combine(portfolioState, monitorState) { portfolio, monitor ->
        WatchAlertsUiState(
            symbol = symbol,
            holding = portfolio.holding,
            watchStock = portfolio.watchStock,
            target = monitor.target,
            config = monitor.config,
            alerts = monitor.alerts,
            operations = portfolio.operations
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchAlertsUiState(symbol = symbol))

    fun clearReadAlerts() {
        viewModelScope.launch {
            stockMonitorRepository.clearReadAlerts(symbol)
        }
    }
}

data class TradeOperationFormUiState(
    val symbol: String = "",
    val holding: Holding? = null,
    val latestPrice: Double? = null,
    val isSaving: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class TradeOperationFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PortfolioRepository
) : ViewModel() {
    private val symbol: String = savedStateHandle["symbol"] ?: ""
    private val isSaving = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TradeOperationFormUiState> = combine(
        repository.observeHoldings(),
        repository.observeQuotes(),
        isSaving,
        message
    ) { holdings, quotes, saving, text ->
        val holding = holdings.firstOrNull { it.symbol == symbol }
        val quote = quotes.firstOrNull { it.symbol == symbol }
        TradeOperationFormUiState(
            symbol = symbol,
            holding = holding,
            latestPrice = quote?.latestPrice ?: holding?.currentPrice,
            isSaving = saving,
            message = text
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TradeOperationFormUiState(symbol = symbol))

    fun save(
        side: TradeOperationSide,
        quantityText: String,
        priceText: String,
        occurredAtMillis: Long,
        note: String,
        onSaved: () -> Unit
    ) {
        val quantity = quantityText.toDoubleOrNull()
        val price = priceText.toDoubleOrNull()
        if (symbol.length != 6 || quantity == null || quantity <= 0 || price == null || price <= 0) {
            message.value = "请检查数量和价格"
            return
        }
        viewModelScope.launch {
            isSaving.value = true
            val result = repository.addTradeOperation(
                TradeOperationInput(
                    symbol = symbol,
                    side = side,
                    quantity = quantity,
                    price = price,
                    occurredAtMillis = occurredAtMillis,
                    note = note
                )
            )
            isSaving.value = false
            result.fold(
                onSuccess = {
                    message.value = "操作已保存"
                    onSaved()
                },
                onFailure = { message.value = it.message ?: "操作保存失败" }
            )
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

/**
 * 预警详情页状态。
 */
data class MonitorDetailUiState(
    val alert: MonitorAlert? = null,
    val isLoaded: Boolean = false
)

/**
 * 预警详情 ViewModel。
 */
@HiltViewModel
class MonitorDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StockMonitorRepository
) : ViewModel() {
    private val alertId: String = savedStateHandle["alertId"] ?: ""
    val uiState: StateFlow<MonitorDetailUiState> = repository.observeAlert(alertId)
        .map { MonitorDetailUiState(alert = it, isLoaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitorDetailUiState())

    fun markRead() {
        if (alertId.isBlank()) return
        viewModelScope.launch {
            repository.markAlertRead(alertId)
        }
    }
}

/**
 * 监控设置页状态。
 */
data class MonitorSettingsUiState(
    val config: MonitorConfig? = null,
    val message: String? = null
)

/**
 * 单只股票监控阈值设置 ViewModel。
 */
@HiltViewModel
class MonitorSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StockMonitorRepository
) : ViewModel() {
    private val symbol: String = savedStateHandle["symbol"] ?: ""
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MonitorSettingsUiState> = combine(
        repository.observeConfig(symbol),
        message
    ) { config, text ->
        MonitorSettingsUiState(config = config ?: MonitorConfig.defaultFor(symbol), message = text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitorSettingsUiState())

    fun save(config: MonitorConfig) {
        viewModelScope.launch {
            repository.upsertConfig(config)
            message.value = "监控设置已保存"
        }
    }

    fun clearMessage() {
        message.value = null
    }
}

private fun List<MonitorAlert>.sortedForDisplay(): List<MonitorAlert> {
    return sortedWith(
        compareBy<MonitorAlert> { it.level.displayOrder() }
            .thenByDescending { it.triggeredAtMillis }
    )
}

private fun MonitorAlertLevel.displayOrder(): Int {
    return when (this) {
        MonitorAlertLevel.CRITICAL -> 0
        MonitorAlertLevel.WARNING -> 1
        MonitorAlertLevel.INFO -> 2
    }
}

private fun WatchStock.watchChangePercentFrom(latestPrice: Double?): Double? {
    val baseClose = watchBaseClose?.takeIf { it > 0 } ?: return null
    val latest = latestPrice ?: return null
    return (latest - baseClose) / baseClose * 100
}

private fun List<WatchListItem>.sortedByStockName(): List<WatchListItem> {
    val collator = Collator.getInstance(Locale.CHINA)
    return sortedWith { left, right ->
        val nameResult = collator.compare(left.name, right.name)
        if (nameResult != 0) nameResult else left.symbol.compareTo(right.symbol)
    }
}

data class OcrDraftForm(
    /** 从 OCR 草稿复制来的稳定行 id。 */
    val id: String,
    /** 可编辑的股票或基金代码。 */
    val symbol: String,
    /** 可编辑的持仓名称。 */
    val name: String,
    /** 根据代码推断出的可编辑市场。 */
    val market: Market,
    /** 可编辑的数量文本。 */
    val quantity: String,
    /** 可编辑的成本价文本。 */
    val costPrice: String,
    /** 可编辑的现价文本。 */
    val currentPrice: String,
    /** 可编辑的备注文本。 */
    val note: String,
    /** 展示给用户的 OCR 置信度。 */
    val confidence: Float
) {
    /**
     * 将校验后的表单文本转换回领域层 OCR 草稿。
     */
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

/**
 * OCR 导入页面的 UI 状态。
 */
data class OcrImportUiState(
    /** ML Kit 识别进行中时为 true。 */
    val isProcessing: Boolean = false,
    /** 用于核对展示的 OCR 原始文本。 */
    val rawText: String = "",
    /** 从 OCR 文本解析出的可编辑草稿行。 */
    val drafts: List<OcrDraftForm> = emptyList(),
    /** 页面显示的一次性消息。 */
    val message: String? = null
)

/**
 * 截图 OCR 导入和确认的 ViewModel。
 */
@HiltViewModel
class OcrImportViewModel @Inject constructor(
    /** 从所选图片中提取原始文本的 ML Kit 封装。 */
    private val ocrTextRecognizer: OcrTextRecognizer,
    /** 将 OCR 文本转换为持仓草稿的解析器。 */
    private val parseOcrHolding: ParseOcrHoldingUseCase,
    /** 用于保存已确认 OCR 草稿的仓库。 */
    private val repository: PortfolioRepository
) : ViewModel() {
    /** OCR 导入路由的可变后备状态。 */
    private val _uiState = MutableStateFlow(OcrImportUiState())
    /** OCR 导入路由的只读屏幕状态。 */
    val uiState: StateFlow<OcrImportUiState> = _uiState

    /**
     * 对选中的图片 URI 执行 OCR，并填充可编辑草稿。
     */
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

    /**
     * 替换一行可编辑 OCR 草稿。
     */
    fun updateDraft(index: Int, draft: OcrDraftForm) {
        _uiState.update { state ->
            state.copy(drafts = state.drafts.toMutableList().also { it[index] = draft })
        }
    }

    /**
     * 将所有有效 OCR 草稿行保存到组合中。
     */
    fun confirmDrafts() {
        viewModelScope.launch {
            val validDrafts = _uiState.value.drafts.mapNotNull { it.toDraft() }
            validDrafts.forEach { repository.upsertOcrDraft(it) }
            _uiState.update { it.copy(message = "已确认导入 ${validDrafts.size} 条持仓") }
        }
    }

    /**
     * 清除当前显示的消息。
     */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * 将领域层 OCR 草稿转换为可编辑表单文本。
     */
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

/**
 * 每日复盘页面的 UI 状态。
 */
data class ReviewUiState(
    /** 用于生成复盘的当前持仓。 */
    val holdings: List<Holding> = emptyList(),
    /** 包含在生成复盘中的信号。 */
    val signals: List<MarketSignal> = emptyList(),
    /** 当前未保存的生成复盘草稿。 */
    val draft: ReviewDraft? = null,
    /** 最近保存的复盘；没有则为空。 */
    val latestReview: DailyReview? = null,
    /** 页面显示的一次性消息。 */
    val message: String? = null
)

/**
 * 用于生成和保存每日组合复盘的 ViewModel。
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    /** 提供持仓、关注股票和复盘数据流的仓库。 */
    private val repository: PortfolioRepository,
    /** 构建生成复盘中包含的信号。 */
    private val analyzeMarketSignals: AnalyzeMarketSignalsUseCase,
    /** 生成每日复盘草稿文本。 */
    private val generateDailyReview: GenerateDailyReviewUseCase
) : ViewModel() {
    /** 通过 [uiState] 暴露的内部消息通道。 */
    private val message = MutableStateFlow<String?>(null)
    /** 复盘路由消费的合并后屏幕状态。 */
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

    /**
     * 保存当前生成的复盘草稿；没有草稿时不处理。
     */
    fun saveCurrentReview() {
        val draft = uiState.value.draft ?: return
        viewModelScope.launch {
            repository.saveDailyReview(generateDailyReview.toDailyReview(draft))
            message.value = "今日复盘已保存"
        }
    }

    /**
     * 清除当前显示的消息。
     */
    fun clearMessage() {
        message.value = null
    }
}
