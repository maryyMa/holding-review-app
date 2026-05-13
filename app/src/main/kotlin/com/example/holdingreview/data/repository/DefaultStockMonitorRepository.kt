package com.example.holdingreview.data.repository

import com.example.holdingreview.data.local.dao.HoldingDao
import com.example.holdingreview.data.local.dao.KLineCacheDao
import com.example.holdingreview.data.local.dao.MonitorAlertDao
import com.example.holdingreview.data.local.dao.MonitorConfigDao
import com.example.holdingreview.data.local.dao.QuoteSnapshotDao
import com.example.holdingreview.data.local.dao.WatchStockDao
import com.example.holdingreview.data.remote.KLineRemoteDataSource
import com.example.holdingreview.data.remote.QuoteRemoteDataSource
import com.example.holdingreview.domain.model.KLinePoint
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.MonitorAlert
import com.example.holdingreview.domain.model.MonitorAlertType
import com.example.holdingreview.domain.model.MonitorConfig
import com.example.holdingreview.domain.model.MonitorTarget
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.SecurityType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 股票监控模块的默认数据实现，复用持仓、关注和行情缓存。
 */
@Singleton
class DefaultStockMonitorRepository @Inject constructor(
    private val holdingDao: HoldingDao,
    private val watchStockDao: WatchStockDao,
    private val quoteSnapshotDao: QuoteSnapshotDao,
    private val monitorConfigDao: MonitorConfigDao,
    private val monitorAlertDao: MonitorAlertDao,
    private val kLineCacheDao: KLineCacheDao,
    private val quoteRemoteDataSource: QuoteRemoteDataSource,
    private val kLineRemoteDataSource: KLineRemoteDataSource
) : StockMonitorRepository {
    override fun observeTargets(): Flow<List<MonitorTarget>> {
        return combine(holdingDao.observeAll(), watchStockDao.observeAll(), quoteSnapshotDao.observeAll()) { holdings, watches, quotes ->
            buildTargets(
                holdings = holdings,
                watches = watches,
                quoteMap = quotes.associateBy { it.symbol }
            )
        }
    }

    override fun observeAlerts(): Flow<List<MonitorAlert>> {
        return monitorAlertDao.observeAll().map { alerts -> alerts.map { it.toDomain() } }
    }

    override fun observeAlert(id: String): Flow<MonitorAlert?> {
        return monitorAlertDao.observeById(id).map { it?.toDomain() }
    }

    override fun observeUnreadAlertCount(): Flow<Int> = monitorAlertDao.observeUnreadCount()

    override fun observeConfigs(): Flow<List<MonitorConfig>> {
        return monitorConfigDao.observeAll().map { configs -> configs.map { it.toDomain() } }
    }

    override fun observeConfig(symbol: String): Flow<MonitorConfig?> {
        val normalizedSymbol = symbol.trim()
        return monitorConfigDao.observeBySymbol(normalizedSymbol).map { config ->
            config?.toDomain() ?: MonitorConfig.defaultFor(normalizedSymbol)
        }
    }

    override suspend fun getTargets(): List<MonitorTarget> {
        return buildTargets(
            holdings = holdingDao.getAllOnce(),
            watches = watchStockDao.getAllOnce(),
            quoteMap = quoteSnapshotDao.getAllOnce().associateBy { it.symbol }
        )
    }

    override suspend fun getConfig(symbol: String, market: Market): MonitorConfig {
        val normalizedSymbol = symbol.trim()
        val existing = monitorConfigDao.findBySymbol(normalizedSymbol)?.toDomain()
        if (existing != null) return existing
        val defaultConfig = MonitorConfig.defaultFor(normalizedSymbol, market)
        monitorConfigDao.upsert(defaultConfig.toEntity())
        return defaultConfig
    }

    override suspend fun upsertConfig(config: MonitorConfig) {
        monitorConfigDao.upsert(config.copy(symbol = config.symbol.trim()).toEntity())
    }

    override suspend fun updateConfigEnabled(symbol: String, enabled: Boolean) {
        val normalizedSymbol = symbol.trim()
        if (monitorConfigDao.findBySymbol(normalizedSymbol) == null) {
            monitorConfigDao.upsert(MonitorConfig.defaultFor(normalizedSymbol).copy(enabled = enabled).toEntity())
        } else {
            monitorConfigDao.updateEnabled(normalizedSymbol, enabled, System.currentTimeMillis())
        }
    }

    override suspend fun updateHighestPrice(symbol: String, highestPrice: Double) {
        val normalizedSymbol = symbol.trim()
        if (monitorConfigDao.findBySymbol(normalizedSymbol) == null) {
            monitorConfigDao.upsert(MonitorConfig.defaultFor(normalizedSymbol).copy(highestPrice = highestPrice).toEntity())
        } else {
            monitorConfigDao.updateHighestPrice(normalizedSymbol, highestPrice, System.currentTimeMillis())
        }
    }

    override suspend fun fetchQuotes(symbols: List<String>): Result<List<QuoteSnapshot>> {
        val normalizedSymbols = symbols.map { it.trim() }.filter { it.length == 6 }.distinct()
        if (normalizedSymbols.isEmpty()) return Result.success(emptyList())
        val remote = quoteRemoteDataSource.fetchQuotes(normalizedSymbols)
        remote.onSuccess { quotes ->
            quoteSnapshotDao.upsertAll(quotes.map { it.toEntity(System.currentTimeMillis()) })
        }
        return remote.fold(
            onSuccess = { quotes -> Result.success(quotes.map { it.toEntity(System.currentTimeMillis()).toDomain() }) },
            onFailure = { error ->
                val cached = quoteSnapshotDao.getAllOnce()
                    .filter { it.symbol in normalizedSymbols }
                    .map { it.toDomain() }
                if (cached.isNotEmpty()) Result.success(cached) else Result.failure(error)
            }
        )
    }

    override suspend fun fetchKLines(symbol: String, market: Market, limit: Int): Result<List<KLinePoint>> {
        val normalizedSymbol = symbol.trim()
        val remote = kLineRemoteDataSource.fetchDailyKLines(normalizedSymbol, market, limit)
        remote.onSuccess { points ->
            kLineCacheDao.upsertAll(points.map { it.toEntity(System.currentTimeMillis()) })
        }
        return remote.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                val cached = kLineCacheDao.getRecent(normalizedSymbol, limit)
                    .map { it.toDomain() }
                    .sortedBy { it.date }
                if (cached.isNotEmpty()) Result.success(cached) else Result.failure(error)
            }
        )
    }

    override suspend fun hasRecentAlert(symbol: String, type: MonitorAlertType, afterMillis: Long): Boolean {
        return monitorAlertDao.findRecent(symbol.trim(), type.name, afterMillis) != null
    }

    override suspend fun insertAlerts(alerts: List<MonitorAlert>) {
        if (alerts.isNotEmpty()) {
            monitorAlertDao.insertAll(alerts.map { it.toEntity() })
        }
    }

    override suspend fun markAlertRead(id: String) {
        monitorAlertDao.markRead(id)
    }

    override suspend fun markAllAlertsRead() {
        monitorAlertDao.markAllRead()
    }

    override suspend fun clearReadAlerts() {
        monitorAlertDao.deleteRead()
    }

    override suspend fun clearReadAlerts(symbol: String) {
        monitorAlertDao.deleteReadBySymbol(symbol.trim())
    }

    override suspend fun deleteAlertsForSymbol(symbol: String) {
        monitorAlertDao.deleteBySymbol(symbol.trim())
    }

    private fun buildTargets(
        holdings: List<com.example.holdingreview.data.local.entities.HoldingEntity>,
        watches: List<com.example.holdingreview.data.local.entities.WatchStockEntity>,
        quoteMap: Map<String, com.example.holdingreview.data.local.entities.QuoteSnapshotEntity>
    ): List<MonitorTarget> {
        val watchMap = watches.associateBy { it.symbol }
        val holdingSymbols = holdings.map { it.symbol }.toSet()
        val holdingTargets = holdings.map { holding ->
            val quote = quoteMap[holding.symbol]
            val watch = watchMap[holding.symbol]
            MonitorTarget(
                symbol = holding.symbol,
                name = quote?.name?.ifBlank { holding.name } ?: holding.name,
                market = Market.fromSymbol(holding.symbol).takeUnless { it == Market.UNKNOWN } ?: runCatching {
                    Market.valueOf(holding.market)
                }.getOrDefault(Market.UNKNOWN),
                securityType = SecurityType.fromSymbol(holding.symbol),
                costPrice = holding.costPrice,
                latestPrice = quote?.latestPrice ?: holding.manualCurrentPrice,
                dayChangePercent = quote?.changePercent ?: 0.0,
                isHolding = true,
                isWatched = watch != null
            )
        }
        val watchTargets = watches.filterNot { it.symbol in holdingSymbols }.map { watch ->
            val quote = quoteMap[watch.symbol]
            MonitorTarget(
                symbol = watch.symbol,
                name = quote?.name?.ifBlank { watch.name } ?: watch.name,
                market = Market.fromSymbol(watch.symbol).takeUnless { it == Market.UNKNOWN } ?: runCatching {
                    Market.valueOf(watch.market)
                }.getOrDefault(Market.UNKNOWN),
                securityType = SecurityType.fromSymbol(watch.symbol),
                costPrice = null,
                latestPrice = quote?.latestPrice,
                dayChangePercent = quote?.changePercent,
                isHolding = false,
                isWatched = true
            )
        }
        return holdingTargets + watchTargets
    }
}
