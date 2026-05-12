package com.example.holdingreview.data.repository

import com.example.holdingreview.domain.model.KLinePoint
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.MonitorAlert
import com.example.holdingreview.domain.model.MonitorAlertType
import com.example.holdingreview.domain.model.MonitorConfig
import com.example.holdingreview.domain.model.MonitorTarget
import com.example.holdingreview.domain.model.QuoteSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * 股票监控模块的数据边界。
 */
interface StockMonitorRepository {
    fun observeTargets(): Flow<List<MonitorTarget>>
    fun observeAlerts(): Flow<List<MonitorAlert>>
    fun observeAlert(id: String): Flow<MonitorAlert?>
    fun observeUnreadAlertCount(): Flow<Int>
    fun observeConfigs(): Flow<List<MonitorConfig>>
    fun observeConfig(symbol: String): Flow<MonitorConfig?>

    suspend fun getTargets(): List<MonitorTarget>
    suspend fun getConfig(symbol: String, market: Market = Market.fromSymbol(symbol)): MonitorConfig
    suspend fun upsertConfig(config: MonitorConfig)
    suspend fun updateConfigEnabled(symbol: String, enabled: Boolean)
    suspend fun updateHighestPrice(symbol: String, highestPrice: Double)

    suspend fun fetchQuotes(symbols: List<String>): Result<List<QuoteSnapshot>>
    suspend fun fetchKLines(symbol: String, market: Market, limit: Int = 30): Result<List<KLinePoint>>

    suspend fun hasRecentAlert(symbol: String, type: MonitorAlertType, afterMillis: Long): Boolean
    suspend fun insertAlerts(alerts: List<MonitorAlert>)
    suspend fun markAlertRead(id: String)
    suspend fun markAllAlertsRead()
}
