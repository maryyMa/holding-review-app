package com.example.holdingreview.data.remote

import com.example.holdingreview.domain.model.Market
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class TencentQuoteParser @Inject constructor() {
    private val recordRegex = Regex("""v_[^=]+="([^"]*)"""")

    fun parse(raw: String): List<RemoteQuote> {
        return recordRegex.findAll(raw).mapNotNull { match ->
            val fields = match.groupValues[1].split("~")
            val symbol = fields.getOrNull(2)?.trim().orEmpty()
            val name = fields.getOrNull(1)?.trim().orEmpty()
            val latestPrice = fields.getOrNull(3)?.toDoubleOrNull()
            if (symbol.length != 6 || name.isBlank() || latestPrice == null) {
                return@mapNotNull null
            }

            val previousClose = fields.getOrNull(4)?.toDoubleOrNull()
            val high = fields.getOrNull(33)?.toDoubleOrNull()
            val low = fields.getOrNull(34)?.toDoubleOrNull()
            val changePercent = fields.getOrNull(32)?.toDoubleOrNull()
                ?: if (previousClose != null && previousClose != 0.0) {
                    (latestPrice - previousClose) / previousClose * 100
                } else {
                    0.0
                }
            val amplitude = fields.getOrNull(43)?.toDoubleOrNull()
                ?: if (previousClose != null && previousClose != 0.0 && high != null && low != null) {
                    abs(high - low) / previousClose * 100
                } else {
                    null
                }

            RemoteQuote(
                symbol = symbol,
                name = name,
                market = Market.fromSymbol(symbol),
                latestPrice = latestPrice,
                previousClose = previousClose,
                changePercent = changePercent,
                volume = fields.getOrNull(6)?.toDoubleOrNull(),
                turnoverAmount = fields.getOrNull(37)?.toDoubleOrNull(),
                turnoverRate = fields.getOrNull(38)?.toDoubleOrNull(),
                amplitude = amplitude
            )
        }.toList()
    }
}
