package com.example.holdingreview.data.remote

import com.example.holdingreview.domain.model.KLinePoint
import com.example.holdingreview.domain.model.Market
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 历史 K 线远程数据源。
 */
interface KLineRemoteDataSource {
    suspend fun fetchDailyKLines(symbol: String, market: Market, limit: Int = 30): Result<List<KLinePoint>>
}

/**
 * 基于东方财富日 K 线接口的实现。
 */
@Singleton
class EastmoneyKLineRemoteDataSource @Inject constructor(
    private val api: EastmoneyKLineApi,
    private val parser: EastmoneyKLineParser
) : KLineRemoteDataSource {
    override suspend fun fetchDailyKLines(symbol: String, market: Market, limit: Int): Result<List<KLinePoint>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalizedSymbol = symbol.trim()
                val resolvedMarket = market.takeUnless { it == Market.UNKNOWN } ?: Market.fromSymbol(normalizedSymbol)
                val secidPrefix = when (resolvedMarket) {
                    Market.SH -> "1"
                    Market.SZ -> "0"
                    Market.UNKNOWN -> throw IllegalArgumentException("无法识别股票市场")
                }
                val body = api.getKLines(
                    mapOf(
                        "secid" to "$secidPrefix.$normalizedSymbol",
                        "fields1" to "f1,f2,f3,f4,f5,f6",
                        "fields2" to "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61",
                        "klt" to "101",
                        "fqt" to "0",
                        "end" to "20500101",
                        "lmt" to limit.toString()
                    )
                )
                parser.parse(normalizedSymbol, body.string())
            }
        }
}
