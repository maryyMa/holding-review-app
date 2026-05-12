package com.example.holdingreview.data.remote

import com.example.holdingreview.domain.model.Market
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 仓库使用的远程行情源抽象。
 */
interface QuoteRemoteDataSource {
    /**
     * 为传入的本地代码拉取标准化行情。
     */
    suspend fun fetchQuotes(symbols: List<String>): Result<List<RemoteQuote>>
}

/**
 * 基于腾讯行情的实现，负责构建查询并解析响应。
 */
@Singleton
class TencentQuoteRemoteDataSource @Inject constructor(
    /** 腾讯端点的 Retrofit API。 */
    private val api: TencentQuoteApi,
    /** 理解腾讯波浪号分隔响应的解析器。 */
    private val parser: TencentQuoteParser
) : QuoteRemoteDataSource {
    /**
     * 在 IO 调度器上拉取行情，并解码腾讯 GBK 响应。
     */
    override suspend fun fetchQuotes(symbols: List<String>): Result<List<RemoteQuote>> = withContext(Dispatchers.IO) {
        runCatching {
            val query = symbols.distinct().joinToString(",") { symbol ->
                val market = Market.fromSymbol(symbol)
                val prefix = if (market == Market.UNKNOWN) "" else market.tencentPrefix
                "$prefix${symbol.trim()}"
            }
            val body = api.getQuotes(query)
            val bytes = body.bytes()
            val raw = runCatching { bytes.toString(Charset.forName("GBK")) }
                .getOrElse { bytes.toString(Charsets.UTF_8) }
            parser.parse(raw)
        }
    }
}
