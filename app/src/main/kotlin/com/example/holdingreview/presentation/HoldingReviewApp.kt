package com.example.holdingreview.presentation

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.HoldingInput
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.MarketSignal
import com.example.holdingreview.domain.model.MonitorAlert
import com.example.holdingreview.domain.model.MonitorAlertLevel
import com.example.holdingreview.domain.model.MonitorConfig
import com.example.holdingreview.domain.model.MonitorTarget
import com.example.holdingreview.domain.model.PortfolioSnapshot
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.SecurityType
import com.example.holdingreview.domain.util.compactNumber
import com.example.holdingreview.domain.util.money
import com.example.holdingreview.domain.util.percent
import com.example.holdingreview.domain.util.signedMoney
import com.example.holdingreview.domain.util.signedPercent

/**
 * 单 Activity 导航图使用的路由名称。
 */
private object Routes {
    /** 首页仪表盘路由。 */
    const val HOME = "home"
    /** 关注列表路由。 */
    const val WATCH = "watch"
    /** 添加关注股票路由。 */
    const val WATCH_ADD = "watch/add"
    /** 股票预警列表路由。*/
    const val MONITOR = "monitor"
    /** 预警详情路由。*/
    const val MONITOR_DETAIL = "monitor/{alertId}"
    /** 单只股票监控设置路由。*/
    const val MONITOR_SETTINGS = "monitor/settings/{symbol}"
    /** OCR 导入路由。 */
    const val OCR = "ocr"
    /** 每日复盘路由。 */
    const val REVIEW = "review"
    /** 带有持仓 id 参数的持仓编辑路由模板。 */
    const val EDIT = "edit/{holdingId}"
    /**
     * 为新增或已有持仓构建具体的编辑路由。
     */
    fun edit(id: String = "new") = "edit/$id"
    fun monitorDetail(id: String) = "monitor/$id"
    fun monitorSettings(symbol: String) = "monitor/settings/$symbol"
}

/** 已持仓股票在关注列表中的强调色。 */
private val HoldingWatchPurple = Color(0xFF7B1FA2)
/** 中国市场习惯中的上涨红色。 */
private val ChinaRiseRed = Color(0xFFC62828)
/** 中国市场习惯中的下跌绿色。 */
private val ChinaFallGreen = Color(0xFF2E7D32)

/**
 * 底部导航项元数据。
 */
private data class NavItem(
    /** 点击该项时打开的导航路由。 */
    val route: String,
    /** 显示在图标下方的标签。 */
    val label: String,
    /** 显示在导航栏中的图标 Composable。 */
    val icon: @Composable () -> Unit
)

/**
 * 展示组合仪表盘、异动信号列表和持仓列表。
 */
@Composable
private fun HomeRoute(
    /** 打开新增持仓表单。 */
    onAddHolding: () -> Unit,
    /** 根据持仓 id 打开编辑表单。 */
    onEditHolding: (String) -> Unit,
    /** 提供首页状态和操作的 ViewModel。 */
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MessageBanner(state.message, viewModel::clearMessage)
            SectionTitle("组合概览")
            PortfolioSummary(snapshot = state.snapshot)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = viewModel::refresh, enabled = !state.isRefreshing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isRefreshing) "刷新中" else "刷新行情")
                }
                FilledTonalButton(onClick = onAddHolding, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("新增持仓")
                }
            }
        }
        item {
            SectionTitle("异动提示")
            if (state.signals.isEmpty()) {
                EmptyText("暂无明显异动，刷新行情后会根据涨跌幅、贡献和仓位生成提示。")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.signals.take(5).forEach { SignalCard(it) }
                }
            }
        }
        item { SectionTitle("持仓列表") }
        if (state.holdings.isEmpty()) {
            item { EmptyText("还没有持仓，先新增一只股票或通过截图导入。") }
        } else {
            items(state.holdings, key = { it.id }) { holding ->
                HoldingCard(holding, onClick = { onEditHolding(holding.id) })
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/**
 * 持有导航脚手架和页面目的地的根 Composable。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingReviewApp() {
    val navController = rememberNavController()
    val navItems = listOf(
        NavItem(Routes.HOME, "首页") { Icon(Icons.Filled.Home, contentDescription = null) },
        NavItem(Routes.WATCH, "关注") { Icon(Icons.Filled.Star, contentDescription = null) },
        NavItem(Routes.MONITOR, "预警") { Icon(Icons.Filled.Notifications, contentDescription = null) },
        NavItem(Routes.OCR, "导入") { Icon(Icons.Filled.ImageSearch, contentDescription = null) },
        NavItem(Routes.REVIEW, "复盘") { Icon(Icons.Filled.Article, contentDescription = null) }
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("持仓复盘") },
                actions = {
                    when (currentRoute) {
                        Routes.WATCH -> {
                            TextButton(onClick = { navController.navigate(Routes.WATCH_ADD) }) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("添加关注")
                            }
                        }
                        Routes.WATCH_ADD -> Unit
                        Routes.MONITOR, Routes.MONITOR_DETAIL, Routes.MONITOR_SETTINGS -> Unit
                        else -> {
                            TextButton(onClick = { navController.navigate(Routes.edit()) }) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("持仓")
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                navItems.forEach { item ->
                    val selected = when (item.route) {
                        Routes.WATCH -> currentRoute == Routes.WATCH || currentRoute == Routes.WATCH_ADD
                        Routes.MONITOR -> currentRoute == Routes.MONITOR ||
                            currentRoute == Routes.MONITOR_DETAIL ||
                            currentRoute == Routes.MONITOR_SETTINGS
                        else -> currentDestination?.hierarchy?.any { it.route == item.route } == true
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = item.icon,
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeRoute(
                    onAddHolding = { navController.navigate(Routes.edit()) },
                    onEditHolding = { navController.navigate(Routes.edit(it)) }
                )
            }
            composable(Routes.WATCH) { WatchListRoute() }
            composable(Routes.WATCH_ADD) {
                WatchEditRoute(onDone = { navController.popBackStack() })
            }
            composable(Routes.MONITOR) {
                MonitorRoute(
                    onOpenAlert = { navController.navigate(Routes.monitorDetail(it)) },
                    onOpenSettings = { navController.navigate(Routes.monitorSettings(it)) }
                )
            }
            composable(Routes.MONITOR_DETAIL) {
                MonitorDetailRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.MONITOR_SETTINGS) {
                MonitorSettingsRoute(onDone = { navController.popBackStack() })
            }
            composable(Routes.OCR) { OcrImportRoute() }
            composable(Routes.REVIEW) { ReviewRoute() }
            composable(Routes.EDIT) {
                HoldingEditRoute(onDone = { navController.popBackStack() })
            }
        }
    }
}

/**
 * 展示新增/编辑持仓表单，并分发保存或删除操作。
 */
@Composable
private fun HoldingEditRoute(
    /** 本地保存或删除完成后调用的回调。 */
    onDone: () -> Unit,
    /** 提供可编辑持仓状态的 ViewModel。 */
    viewModel: HoldingEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var loadedId by remember { mutableStateOf<String?>(null) }
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var costPrice by remember { mutableStateOf("") }
    var currentPrice by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val normalizedSymbol = symbol.trim()
    val quote = state.quote?.takeIf { it.symbol == normalizedSymbol }
    val showManualQuoteFields = state.allowManualQuoteInput || (state.holding != null && quote == null)
    val resolvedName = quote?.name ?: name
    val resolvedCurrentPrice = quote?.latestPrice?.toString() ?: currentPrice

    LaunchedEffect(state.holding?.id) {
        val holding = state.holding
        if (holding != null && loadedId != holding.id) {
            loadedId = holding.id
            symbol = holding.symbol
            name = holding.name
            quantity = holding.quantity.toString()
            costPrice = holding.costPrice.toString()
            currentPrice = holding.currentPrice.toString()
            note = holding.note
        }
    }

    LaunchedEffect(state.quote?.symbol) {
        val latestQuote = state.quote
        if (latestQuote != null && latestQuote.symbol == normalizedSymbol) {
            name = latestQuote.name
            currentPrice = latestQuote.latestPrice.toString()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle(if (state.holding == null) "新增持仓" else "编辑持仓")
            MessageBanner(message) { message = null }
            StockFormFields(
                symbol = symbol,
                name = name,
                quantity = quantity,
                costPrice = costPrice,
                currentPrice = currentPrice,
                note = note,
                quote = quote,
                isLookingUp = state.isLookingUp,
                lookupError = state.lookupError,
                showManualQuoteFields = showManualQuoteFields,
                onSymbolChange = {
                    symbol = it
                    viewModel.lookupQuote(it)
                },
                onNameChange = { name = it },
                onQuantityChange = { quantity = it },
                onCostPriceChange = { costPrice = it },
                onCurrentPriceChange = { currentPrice = it },
                onNoteChange = { note = it }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val input = buildHoldingInput(state.holding?.id, symbol, resolvedName, quantity, costPrice, resolvedCurrentPrice, note)
                        if (input == null) {
                            message = "请检查股票代码、数量、成本价、股票名称和现价"
                        } else {
                            viewModel.save(input)
                            onDone()
                        }
                    },
                    enabled = !state.isLookingUp,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("保存")
                }
                if (state.holding != null) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.deleteCurrent()
                            onDone()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("删除")
                    }
                }
            }
        }
    }
}

/**
 * 展示已持仓股票和关注股票合并后的列表。
 */
@Composable
private fun WatchListRoute(viewModel: WatchListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MessageBanner(state.message, viewModel::clearMessage)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("关注股票")
                FilledTonalButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("刷新")
                }
            }
        }
        if (state.items.isEmpty()) {
            item { EmptyText("暂无持仓或关注股票") }
        }
        items(state.items, key = { it.symbol }) { stock ->
            WatchStockCard(stock, onDelete = { viewModel.delete(stock.symbol) })
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/**
 * 展示添加关注股票表单，并在代码满 6 位后自动查询名称和行情。
 */
@Composable
private fun WatchEditRoute(
    /** 保存或取消后返回上一页。 */
    onDone: () -> Unit,
    /** 提供自动查询和保存逻辑的 ViewModel。 */
    viewModel: WatchEditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var industry by remember { mutableStateOf("") }
    var industryTouched by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val normalizedSymbol = symbol.trim()
    val quote = state.quote?.takeIf { it.symbol == normalizedSymbol }

    LaunchedEffect(quote?.symbol) {
        if (quote != null) {
            name = quote.name
        }
    }

    LaunchedEffect(state.suggestedIndustry, normalizedSymbol) {
        val suggestedIndustry = state.suggestedIndustry
        if (!industryTouched && !suggestedIndustry.isNullOrBlank()) {
            industry = suggestedIndustry
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("添加关注")
            MessageBanner(message) { message = null }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = {
                        val oldSymbol = symbol.trim()
                        symbol = it
                        if (it.trim() != oldSymbol) {
                            name = ""
                            if (!industryTouched) industry = ""
                        }
                        viewModel.lookupQuote(it)
                    },
                    label = { Text("股票代码") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.isLookingUp) {
                    Text("正在查询股票名称和现价...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                quote?.let {
                    CardWithPadding {
                        Text("股票名称：${it.name}", fontWeight = FontWeight.SemiBold)
                        Text("当前价格：${money(it.latestPrice)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.lookupError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (state.allowManualNameInput) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("股票名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("关注原因（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = industry,
                    onValueChange = {
                        industry = it
                        industryTouched = true
                    },
                    label = { Text("行业（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val saved = viewModel.save(symbol, name, reason, industry)
                            if (saved) {
                                onDone()
                            } else {
                                message = "请检查股票代码和股票名称"
                            }
                        },
                        enabled = !state.isLookingUp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("保存")
                    }
                    FilledTonalButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

/**
 * 展示图片选择、OCR 原始文本和可编辑 OCR 草稿行。
 */
@Composable
private fun MonitorRoute(
    onOpenAlert: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    viewModel: MonitorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.runNow()
    }

    fun runMonitor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.runNow()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MessageBanner(state.message, viewModel::clearMessage)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("股票预警")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = viewModel::markAllRead, enabled = state.unreadCount > 0) {
                        Text("全部已读")
                    }
                    Button(onClick = ::runMonitor, enabled = !state.isRunning) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isRunning) "监控中" else "立即监控")
                    }
                }
            }
            Text(
                "已覆盖 ${state.targets.size} 只股票，未读预警 ${state.unreadCount} 条",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.alerts.isEmpty()) {
            item { EmptyText("暂无预警。点击立即监控后，会根据成本、涨跌幅、量能、均线、RSI、跳空和动态止盈生成提醒。") }
        } else {
            items(state.alerts, key = { it.id }) { alert ->
                MonitorAlertCard(alert = alert, onClick = { onOpenAlert(alert.id) })
            }
        }
        item { SectionTitle("监控股票") }
        if (state.targets.isEmpty()) {
            item { EmptyText("暂无持仓或关注股票，先添加持仓或关注后再启用监控。") }
        } else {
            items(state.targets, key = { it.symbol }) { target ->
                MonitorTargetCard(
                    target = target,
                    config = state.configs[target.symbol],
                    onSettings = { onOpenSettings(target.symbol) }
                )
            }
        }
        item { EmptyText("预警只用于辅助观察，不构成投资建议。后台监控由 WorkManager 调度，默认约 15 分钟执行一次。") }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MonitorDetailRoute(
    onBack: () -> Unit,
    viewModel: MonitorDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.alert?.id) {
        if (state.alert != null) viewModel.markRead()
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val alert = state.alert
        if (alert == null) {
            item { EmptyText("没有找到这条预警记录。") }
        } else {
            item {
                SectionTitle(alert.title)
                CardWithPadding {
                    Text("${alert.symbol} · ${alert.market.displayName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("级别：${alert.level.label()}", color = alert.level.color(), fontWeight = FontWeight.Bold)
                    Text("现价：${money(alert.latestPrice)} · 涨跌：${signedPercent(alert.changePercent)}")
                    Text("类型：${alert.type.displayName}")
                }
            }
            item {
                CardWithPadding {
                    Text("触发原因", fontWeight = FontWeight.Bold)
                    Text(alert.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            FilledTonalButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
        }
    }
}

@Composable
private fun MonitorSettingsRoute(
    onDone: () -> Unit,
    viewModel: MonitorSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val config = state.config
    var loadedSymbol by remember { mutableStateOf<String?>(null) }
    var enabled by remember { mutableStateOf(true) }
    var enableCost by remember { mutableStateOf(true) }
    var enableChange by remember { mutableStateOf(true) }
    var enableVolume by remember { mutableStateOf(true) }
    var enableMa by remember { mutableStateOf(true) }
    var enableRsi by remember { mutableStateOf(true) }
    var enableGap by remember { mutableStateOf(true) }
    var enableTrailingStop by remember { mutableStateOf(true) }
    var costProfit by remember { mutableStateOf("") }
    var costLoss by remember { mutableStateOf("") }
    var change by remember { mutableStateOf("") }
    var volumeSurge by remember { mutableStateOf("") }
    var volumeShrink by remember { mutableStateOf("") }
    var rsiHigh by remember { mutableStateOf("") }
    var rsiLow by remember { mutableStateOf("") }
    var gap by remember { mutableStateOf("") }
    var trailingStart by remember { mutableStateOf("") }
    var trailingWarning by remember { mutableStateOf("") }
    var trailingCritical by remember { mutableStateOf("") }

    LaunchedEffect(config?.symbol) {
        val current = config ?: return@LaunchedEffect
        if (loadedSymbol == current.symbol) return@LaunchedEffect
        loadedSymbol = current.symbol
        enabled = current.enabled
        enableCost = current.enableCost
        enableChange = current.enableChange
        enableVolume = current.enableVolume
        enableMa = current.enableMa
        enableRsi = current.enableRsi
        enableGap = current.enableGap
        enableTrailingStop = current.enableTrailingStop
        costProfit = current.costProfitPercent.toString()
        costLoss = current.costLossPercent.toString()
        change = current.changePercent.toString()
        volumeSurge = current.volumeSurgeMultiplier.toString()
        volumeShrink = current.volumeShrinkMultiplier.toString()
        rsiHigh = current.rsiHigh.toString()
        rsiLow = current.rsiLow.toString()
        gap = current.gapPercent.toString()
        trailingStart = current.trailingProfitStartPercent.toString()
        trailingWarning = current.trailingWarningDrawdownPercent.toString()
        trailingCritical = current.trailingCriticalDrawdownPercent.toString()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionTitle("监控设置")
            MessageBanner(state.message, viewModel::clearMessage)
            if (config != null) {
                Text("${config.symbol} · ${config.market.displayName} · ${config.securityType.label()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (config == null) {
            item { EmptyText("正在加载监控配置。") }
        } else {
            item {
                CardWithPadding {
                    SettingSwitchRow("启用监控", enabled) { enabled = it }
                    SettingSwitchRow("成本收益率", enableCost) { enableCost = it }
                    SettingSwitchRow("日内涨跌幅", enableChange) { enableChange = it }
                    SettingSwitchRow("成交量异动", enableVolume) { enableVolume = it }
                    SettingSwitchRow("均线金叉/死叉", enableMa) { enableMa = it }
                    SettingSwitchRow("RSI 超买/超卖", enableRsi) { enableRsi = it }
                    SettingSwitchRow("跳空缺口", enableGap) { enableGap = it }
                    SettingSwitchRow("动态止盈", enableTrailingStop) { enableTrailingStop = it }
                }
            }
            item {
                CardWithPadding {
                    OutlinedTextField(costProfit, { costProfit = it }, label = { Text("盈利提醒阈值 %") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(costLoss, { costLoss = it }, label = { Text("亏损提醒阈值 %") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(change, { change = it }, label = { Text("日内涨跌幅阈值 %") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(volumeSurge, { volumeSurge = it }, label = { Text("放量倍数") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(volumeShrink, { volumeShrink = it }, label = { Text("缩量倍数") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(rsiHigh, { rsiHigh = it }, label = { Text("RSI 超买阈值") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(rsiLow, { rsiLow = it }, label = { Text("RSI 超卖阈值") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(gap, { gap = it }, label = { Text("跳空阈值 %") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(trailingStart, { trailingStart = it }, label = { Text("动态止盈启动盈利 %") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(trailingWarning, { trailingWarning = it }, label = { Text("回撤警告 %") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(trailingCritical, { trailingCritical = it }, label = { Text("严重回撤 %") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            viewModel.save(
                                config.copy(
                                    enabled = enabled,
                                    enableCost = enableCost,
                                    enableChange = enableChange,
                                    enableVolume = enableVolume,
                                    enableMa = enableMa,
                                    enableRsi = enableRsi,
                                    enableGap = enableGap,
                                    enableTrailingStop = enableTrailingStop,
                                    costProfitPercent = costProfit.toDoubleOrNull() ?: config.costProfitPercent,
                                    costLossPercent = costLoss.toDoubleOrNull() ?: config.costLossPercent,
                                    changePercent = change.toDoubleOrNull() ?: config.changePercent,
                                    volumeSurgeMultiplier = volumeSurge.toDoubleOrNull() ?: config.volumeSurgeMultiplier,
                                    volumeShrinkMultiplier = volumeShrink.toDoubleOrNull() ?: config.volumeShrinkMultiplier,
                                    rsiHigh = rsiHigh.toDoubleOrNull() ?: config.rsiHigh,
                                    rsiLow = rsiLow.toDoubleOrNull() ?: config.rsiLow,
                                    gapPercent = gap.toDoubleOrNull() ?: config.gapPercent,
                                    trailingProfitStartPercent = trailingStart.toDoubleOrNull() ?: config.trailingProfitStartPercent,
                                    trailingWarningDrawdownPercent = trailingWarning.toDoubleOrNull() ?: config.trailingWarningDrawdownPercent,
                                    trailingCriticalDrawdownPercent = trailingCritical.toDoubleOrNull() ?: config.trailingCriticalDrawdownPercent
                                )
                            )
                            onDone()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("保存")
                    }
                    FilledTonalButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrImportRoute(viewModel: OcrImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.recognizeImage(uri)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MessageBanner(state.message, viewModel::clearMessage)
            SectionTitle("截图导入")
            CardWithPadding {
                Text("从相册选择券商持仓截图，OCR 识别后先生成候选持仓，确认无误再写入本地数据。")
                Button(
                    onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !state.isProcessing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.ImageSearch, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isProcessing) "识别中" else "选择截图")
                }
            }
        }
        if (state.rawText.isNotBlank()) {
            item {
                SectionTitle("OCR 原文")
                CardWithPadding {
                    Text(state.rawText.take(800), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.drafts.isNotEmpty()) {
            item { SectionTitle("确认识别结果") }
            items(state.drafts.size) { index ->
                OcrDraftEditor(
                    draft = state.drafts[index],
                    onChange = { viewModel.updateDraft(index, it) }
                )
            }
            item {
                Button(onClick = viewModel::confirmDrafts, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("确认导入")
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/**
 * 展示生成的每日复盘以及复制/保存操作。
 */
@Composable
private fun ReviewRoute(viewModel: ReviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val draft = state.draft
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MessageBanner(state.message, viewModel::clearMessage)
            SectionTitle("每日复盘")
            if (draft == null) {
                EmptyText("暂无持仓，先新增或导入持仓后再生成复盘。")
            } else {
                CardWithPadding {
                    Text(draft.summary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = viewModel::saveCurrentReview, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("保存")
                        }
                        FilledTonalButton(
                            onClick = { clipboard.setText(AnnotatedString(draft.summary)) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("复制")
                        }
                    }
                }
            }
        }
        if (draft != null) {
            item {
                SectionTitle("AI 润色 Prompt")
                CardWithPadding {
                    Text(draft.aiPrompt, style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(
                        onClick = { clipboard.setText(AnnotatedString(draft.aiPrompt)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("复制 Prompt")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/**
 * 用紧凑的两行摘要展示组合主要总计指标。
 */
@Composable
private fun PortfolioSummary(snapshot: PortfolioSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("总市值", money(snapshot.marketValue), Modifier.weight(1f))
            SummaryCard("当日盈亏", signedMoney(snapshot.dayProfit), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("累计盈亏", signedMoney(snapshot.totalProfit), Modifier.weight(1f))
            SummaryCard("持仓数量", "${snapshot.holdingCount} 只", Modifier.weight(1f))
        }
    }
}

/**
 * 展示一个标签/数值指标卡片。
 */
@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/**
 * 展示一个可点击的持仓行，包含价格和盈亏指标。
 */
@Composable
private fun HoldingCard(holding: Holding, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(holding.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${holding.symbol} · ${holding.market.displayName} · ${holding.quantity} 股", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(signedMoney(holding.dayProfit), fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallMetric("现价", money(holding.currentPrice), Modifier.weight(1f))
                SmallMetric("日涨跌", signedPercent(holding.dayChangePercent), Modifier.weight(1f))
                SmallMetric("总收益", signedPercent(holding.totalProfitPercent), Modifier.weight(1f))
            }
            if (holding.note.isNotBlank()) Text(holding.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 展示一个关注列表行，包含行情数据和删除操作。
 */
@Composable
private fun WatchStockCard(stock: WatchListItem, onDelete: () -> Unit) {
    val identityColor = if (stock.isHolding) HoldingWatchPurple else MaterialTheme.colorScheme.onSurface
    val changeColor = stock.dayChangePercent?.let {
        when {
            it > 0 -> ChinaRiseRed
            it < 0 -> ChinaFallGreen
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stock.name, color = identityColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${stock.symbol} · ${stock.market.displayName} · ${stock.industry.ifBlank { "行业未填" }}",
                    color = identityColor
                )
                Text(
                    "现价 ${stock.latestPrice?.let { money(it) } ?: "--"} · 涨跌 ${stock.dayChangePercent?.let { signedPercent(it) } ?: "--"}",
                    color = changeColor
                )
                if (stock.reason.isNotBlank()) Text(stock.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (stock.isHolding) Text("已持仓", color = HoldingWatchPurple, style = MaterialTheme.typography.labelMedium)
            }
            if (stock.isWatched) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

/**
 * 在带内边距的卡片中展示一条生成信号。
 */
/**
 * 显示一条股票监控预警。
 */
@Composable
private fun MonitorAlertCard(alert: MonitorAlert, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(alert.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${alert.symbol} · ${alert.type.displayName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(
                    onClick = onClick,
                    label = { Text(alert.level.label(), color = alert.level.color()) }
                )
            }
            Text("现价 ${money(alert.latestPrice)} · 涨跌 ${signedPercent(alert.changePercent)}")
            Text(alert.message.lineSequence().firstOrNull().orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!alert.isRead) {
                Text("未读", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * 显示参与监控的一只股票和设置入口。
 */
@Composable
private fun MonitorTargetCard(target: MonitorTarget, config: MonitorConfig?, onSettings: () -> Unit) {
    val enabled = config?.enabled ?: true
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(target.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("${target.symbol} · ${target.market.displayName} · ${target.securityType.label()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "现价 ${target.latestPrice?.let { money(it) } ?: "--"} · 涨跌 ${target.dayChangePercent?.let { signedPercent(it) } ?: "--"}",
                    color = when {
                        (target.dayChangePercent ?: 0.0) > 0 -> ChinaRiseRed
                        (target.dayChangePercent ?: 0.0) < 0 -> ChinaFallGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(if (enabled) "监控已启用" else "监控已暂停", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }
    }
}

/**
 * 监控设置页中的开关行。
 */
@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun MonitorAlertLevel.label(): String {
    return when (this) {
        MonitorAlertLevel.INFO -> "提示"
        MonitorAlertLevel.WARNING -> "警告"
        MonitorAlertLevel.CRITICAL -> "严重"
    }
}

@Composable
private fun MonitorAlertLevel.color(): Color {
    return when (this) {
        MonitorAlertLevel.INFO -> MaterialTheme.colorScheme.primary
        MonitorAlertLevel.WARNING -> Color(0xFFF57C00)
        MonitorAlertLevel.CRITICAL -> MaterialTheme.colorScheme.error
    }
}

private fun SecurityType.label(): String {
    return when (this) {
        SecurityType.STOCK -> "个股"
        SecurityType.ETF -> "ETF"
    }
}

@Composable
private fun SignalCard(signal: MarketSignal) {
    CardWithPadding {
        Text(signal.title, fontWeight = FontWeight.Bold)
        Text(signal.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 在 surface-variant 卡片中展示小指标。
 */
@Composable
private fun SmallMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * 新增/编辑页面共用的持仓输入字段。
 */
@Composable
private fun StockFormFields(
    /** 可编辑的代码文本。 */
    symbol: String,
    /** 手动兜底时可编辑的持仓名称文本。 */
    name: String,
    /** 可编辑的数量文本。 */
    quantity: String,
    /** 可编辑的成本价文本。 */
    costPrice: String,
    /** 可编辑的现价文本。 */
    currentPrice: String,
    /** 可编辑的备注文本。 */
    note: String,
    /** 自动查询成功后返回的行情。 */
    quote: QuoteSnapshot?,
    /** 是否正在查询股票名称和现价。 */
    isLookingUp: Boolean,
    /** 查询失败时展示的错误信息。 */
    lookupError: String?,
    /** 是否展示名称和现价的手动兜底输入。 */
    showManualQuoteFields: Boolean,
    /** 处理代码文本变化。 */
    onSymbolChange: (String) -> Unit,
    /** 处理名称文本变化。 */
    onNameChange: (String) -> Unit,
    /** 处理数量文本变化。 */
    onQuantityChange: (String) -> Unit,
    /** 处理成本价文本变化。 */
    onCostPriceChange: (String) -> Unit,
    /** 处理现价文本变化。 */
    onCurrentPriceChange: (String) -> Unit,
    /** 处理备注文本变化。 */
    onNoteChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(symbol, onSymbolChange, label = { Text("股票代码") }, modifier = Modifier.fillMaxWidth())
        if (isLookingUp) {
            Text("正在查询股票名称和现价...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        quote?.let {
            CardWithPadding {
                Text("股票名称：${it.name}", fontWeight = FontWeight.SemiBold)
                Text("当前价格：${money(it.latestPrice)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        lookupError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (showManualQuoteFields) {
            OutlinedTextField(name, onNameChange, label = { Text("股票名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(currentPrice, onCurrentPriceChange, label = { Text("当前价格") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(quantity, onQuantityChange, label = { Text("持仓数量") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(costPrice, onCostPriceChange, label = { Text("成本价") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, onNoteChange, label = { Text("备注/交易计划") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * 导入确认前展示一张可编辑 OCR 草稿卡片。
 */
@Composable
private fun OcrDraftEditor(draft: OcrDraftForm, onChange: (OcrDraftForm) -> Unit) {
    CardWithPadding {
        Text("识别置信度 ${percent(draft.confidence.toDouble() * 100)}", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(draft.symbol, { onChange(draft.copy(symbol = it)) }, label = { Text("股票代码") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.name, { onChange(draft.copy(name = it)) }, label = { Text("股票名称") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.quantity, { onChange(draft.copy(quantity = it)) }, label = { Text("持仓数量") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.costPrice, { onChange(draft.copy(costPrice = it)) }, label = { Text("成本价") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.currentPrice, { onChange(draft.copy(currentPrice = it)) }, label = { Text("现价") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.note, { onChange(draft.copy(note = it)) }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * 应用 surface 颜色和统一内边距的标准卡片包装器。
 */
@Composable
private fun CardWithPadding(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/**
 * 长滚动页面中复用的分区标题。
 */
@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleLarge
    )
}

/**
 * 显示在路由顶部、可点击清除的消息条。
 */
@Composable
private fun MessageBanner(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    AssistChip(
        onClick = onDismiss,
        label = { Text(message) },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 显示在标准内边距卡片中的空状态文本。
 */
@Composable
private fun EmptyText(text: String) {
    CardWithPadding {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 用于小数数字输入框的键盘配置。
 */
private fun numberKeyboard(): KeyboardOptions {
    return KeyboardOptions(keyboardType = KeyboardType.Decimal)
}

/**
 * 校验持仓表单文本，并转换为领域层输入。
 */
private fun buildHoldingInput(
    /** 已有持仓 id；新增持仓时为空。 */
    id: String?,
    /** 表单中的原始代码文本。 */
    symbol: String,
    /** 表单中的原始持仓名称文本。 */
    name: String,
    /** 表单中的原始数量文本。 */
    quantity: String,
    /** 表单中的原始成本价文本。 */
    costPrice: String,
    /** 表单中的原始现价文本。 */
    currentPrice: String,
    /** 表单中的原始备注文本。 */
    note: String
): HoldingInput? {
    val normalizedSymbol = symbol.trim()
    if (normalizedSymbol.length != 6 || name.isBlank()) return null
    return HoldingInput(
        id = id,
        symbol = normalizedSymbol,
        name = name.trim(),
        market = Market.fromSymbol(normalizedSymbol),
        quantity = quantity.toDoubleOrNull()?.takeIf { it > 0 } ?: return null,
        costPrice = costPrice.toDoubleOrNull()?.takeIf { it >= 0 } ?: return null,
        manualCurrentPrice = currentPrice.toDoubleOrNull()?.takeIf { it >= 0 } ?: return null,
        note = note
    )
}
