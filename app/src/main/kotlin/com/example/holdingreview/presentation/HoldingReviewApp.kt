package com.example.holdingreview.presentation

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
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
import com.example.holdingreview.domain.model.PortfolioSnapshot
import com.example.holdingreview.domain.model.WatchStock
import com.example.holdingreview.domain.model.WatchStockInput
import com.example.holdingreview.domain.util.compactNumber
import com.example.holdingreview.domain.util.money
import com.example.holdingreview.domain.util.percent
import com.example.holdingreview.domain.util.signedMoney
import com.example.holdingreview.domain.util.signedPercent

private object Routes {
    const val HOME = "home"
    const val WATCH = "watch"
    const val OCR = "ocr"
    const val REVIEW = "review"
    const val EDIT = "edit/{holdingId}"
    fun edit(id: String = "new") = "edit/$id"
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingReviewApp() {
    val navController = rememberNavController()
    val navItems = listOf(
        NavItem(Routes.HOME, "首页") { Icon(Icons.Filled.Home, contentDescription = null) },
        NavItem(Routes.WATCH, "关注") { Icon(Icons.Filled.Star, contentDescription = null) },
        NavItem(Routes.OCR, "导入") { Icon(Icons.Filled.ImageSearch, contentDescription = null) },
        NavItem(Routes.REVIEW, "复盘") { Icon(Icons.Filled.Article, contentDescription = null) }
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("持仓复盘") },
                actions = {
                    TextButton(onClick = { navController.navigate(Routes.edit()) }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("持仓")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
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
            composable(Routes.OCR) { OcrImportRoute() }
            composable(Routes.REVIEW) { ReviewRoute() }
            composable(Routes.EDIT) {
                HoldingEditRoute(onDone = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun HomeRoute(
    onAddHolding: () -> Unit,
    onEditHolding: (String) -> Unit,
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

@Composable
private fun HoldingEditRoute(
    onDone: () -> Unit,
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
                onSymbolChange = { symbol = it },
                onNameChange = { name = it },
                onQuantityChange = { quantity = it },
                onCostPriceChange = { costPrice = it },
                onCurrentPriceChange = { currentPrice = it },
                onNoteChange = { note = it }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val input = buildHoldingInput(state.holding?.id, symbol, name, quantity, costPrice, currentPrice, note)
                        if (input == null) {
                            message = "请检查代码、名称、数量和价格"
                        } else {
                            viewModel.save(input)
                            onDone()
                        }
                    },
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

@Composable
private fun WatchListRoute(viewModel: WatchListViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MessageBanner(state.message, viewModel::clearMessage)
            SectionTitle("关注股票")
            CardWithPadding {
                OutlinedTextField(symbol, { symbol = it }, label = { Text("股票代码") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("股票名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reason, { reason = it }, label = { Text("关注原因") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tags, { tags = it }, label = { Text("标签，如 半导体,权重") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (symbol.length == 6 && name.isNotBlank()) {
                                viewModel.add(WatchStockInput(symbol, name, Market.fromSymbol(symbol), reason, tags))
                                symbol = ""
                                name = ""
                                reason = ""
                                tags = ""
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("加入关注")
                    }
                    FilledTonalButton(onClick = viewModel::refresh, enabled = !state.isRefreshing, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("刷新")
                    }
                }
            }
        }
        items(state.watchStocks, key = { it.symbol }) { stock ->
            WatchStockCard(stock, onDelete = { viewModel.delete(stock.symbol) })
        }
        item { Spacer(Modifier.height(12.dp)) }
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

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
    }
}

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

@Composable
private fun WatchStockCard(stock: WatchStock, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stock.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("${stock.symbol} · ${stock.market.displayName} · ${stock.tags}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("现价 ${stock.latestPrice?.let { money(it) } ?: "--"} · 涨跌 ${stock.dayChangePercent?.let { signedPercent(it) } ?: "--"}")
                if (stock.reason.isNotBlank()) Text(stock.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除")
            }
        }
    }
}

@Composable
private fun SignalCard(signal: MarketSignal) {
    CardWithPadding {
        Text(signal.title, fontWeight = FontWeight.Bold)
        Text(signal.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SmallMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StockFormFields(
    symbol: String,
    name: String,
    quantity: String,
    costPrice: String,
    currentPrice: String,
    note: String,
    onSymbolChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onCostPriceChange: (String) -> Unit,
    onCurrentPriceChange: (String) -> Unit,
    onNoteChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(symbol, onSymbolChange, label = { Text("股票代码") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(name, onNameChange, label = { Text("股票名称") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(quantity, onQuantityChange, label = { Text("持仓数量") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(costPrice, onCostPriceChange, label = { Text("成本价") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(currentPrice, onCurrentPriceChange, label = { Text("现价/截图价") }, keyboardOptions = numberKeyboard(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, onNoteChange, label = { Text("备注/交易计划") }, minLines = 2, modifier = Modifier.fillMaxWidth())
    }
}

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

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
private fun MessageBanner(message: String?, onDismiss: () -> Unit) {
    if (message == null) return
    AssistChip(
        onClick = onDismiss,
        label = { Text(message) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EmptyText(text: String) {
    CardWithPadding {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun numberKeyboard(): KeyboardOptions {
    return KeyboardOptions(keyboardType = KeyboardType.Decimal)
}

private fun buildHoldingInput(
    id: String?,
    symbol: String,
    name: String,
    quantity: String,
    costPrice: String,
    currentPrice: String,
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
