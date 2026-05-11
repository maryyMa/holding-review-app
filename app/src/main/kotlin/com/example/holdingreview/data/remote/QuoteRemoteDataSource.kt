package com.example.holdingreview.data.remote

import com.example.holdingreview.domain.model.Market
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

interface QuoteRemoteDataSource {
    suspend fun fetchQuotes(symbols: List<String>): Result<List<RemoteQuote>>
}

@Singleton
class TencentQuoteRemoteDataSource @Inject constructor(
    private val api: TencentQuoteApi,
    private val parser: TencentQuoteParser
) : QuoteRemoteDataSource {
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
