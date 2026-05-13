package com.example.holdingreview.data.seed

import android.content.Context
import com.example.holdingreview.domain.model.Market
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从可选的本地 assets JSON 中读取用户自维护的初始组合数据。
 */
@Singleton
class PersonalPortfolioSeedDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : PersonalPortfolioSeedSource {
    override fun load(): PersonalPortfolioSeed? {
        val raw = try {
            context.assets.open(FileName).bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            return null
        }
        if (raw.isBlank()) return null
        return parse(JSONObject(raw))
    }

    private fun parse(root: JSONObject): PersonalPortfolioSeed {
        val schemaVersion = root.optInt("schemaVersion", 1)
        require(schemaVersion == 1) { "不支持的个人数据 schemaVersion：$schemaVersion" }
        return PersonalPortfolioSeed(
            holdings = root.optJSONArray("holdings").toList { item ->
                PersonalHoldingSeed(
                    symbol = item.requiredSymbol(),
                    name = item.optText("name") ?: item.requiredSymbol(),
                    market = item.optMarket("market", item.requiredSymbol()),
                    quantity = item.getDouble("quantity"),
                    costPrice = item.getDouble("costPrice"),
                    manualCurrentPrice = item.getDouble("manualCurrentPrice"),
                    note = item.optText("note").orEmpty()
                )
            },
            watchStocks = root.optJSONArray("watchStocks").toList { item ->
                PersonalWatchStockSeed(
                    symbol = item.requiredSymbol(),
                    name = item.optText("name") ?: item.requiredSymbol(),
                    market = item.optMarket("market", item.requiredSymbol()),
                    reason = item.optText("reason").orEmpty(),
                    industry = item.optText("industry").orEmpty(),
                    watchBaseClose = item.optDoubleOrNull("watchBaseClose"),
                    watchBaseCloseDate = item.optText("watchBaseCloseDate")
                )
            },
            monitorConfigs = root.optJSONArray("monitorConfigs").toList { item ->
                PersonalMonitorConfigSeed(
                    symbol = item.requiredSymbol(),
                    enabled = item.optBoolean("enabled", true),
                    costProfitPercent = item.optDoubleOrNull("costProfitPercent"),
                    costLossPercent = item.optDoubleOrNull("costLossPercent"),
                    changePercent = item.optDoubleOrNull("changePercent"),
                    volumeSurgeMultiplier = item.optDoubleOrNull("volumeSurgeMultiplier"),
                    volumeShrinkMultiplier = item.optDoubleOrNull("volumeShrinkMultiplier"),
                    rsiHigh = item.optDoubleOrNull("rsiHigh"),
                    rsiLow = item.optDoubleOrNull("rsiLow"),
                    gapPercent = item.optDoubleOrNull("gapPercent"),
                    trailingProfitStartPercent = item.optDoubleOrNull("trailingProfitStartPercent"),
                    trailingWarningDrawdownPercent = item.optDoubleOrNull("trailingWarningDrawdownPercent"),
                    trailingCriticalDrawdownPercent = item.optDoubleOrNull("trailingCriticalDrawdownPercent")
                )
            }
        )
    }

    private fun JSONObject.requiredSymbol(): String {
        val symbol = getString("symbol").trim()
        require(symbol.length == 6) { "股票代码必须是 6 位：$symbol" }
        return symbol
    }

    private fun JSONObject.optText(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name).trim().takeIf { it.isNotBlank() } else null
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        return if (has(name) && !isNull(name)) getDouble(name) else null
    }

    private fun JSONObject.optMarket(name: String, symbol: String): Market {
        val value = optText(name) ?: return Market.fromSymbol(symbol)
        return runCatching { Market.valueOf(value) }.getOrElse {
            throw IllegalArgumentException("不支持的市场：$value")
        }
    }

    private fun <T> org.json.JSONArray?.toList(mapper: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return List(length()) { index -> mapper(getJSONObject(index)) }
    }

    private companion object {
        const val FileName = "personal_portfolio.local.json"
    }
}

interface PersonalPortfolioSeedSource {
    fun load(): PersonalPortfolioSeed?
}

data class PersonalPortfolioSeed(
    val holdings: List<PersonalHoldingSeed>,
    val watchStocks: List<PersonalWatchStockSeed>,
    val monitorConfigs: List<PersonalMonitorConfigSeed>
)

data class PersonalHoldingSeed(
    val symbol: String,
    val name: String,
    val market: Market,
    val quantity: Double,
    val costPrice: Double,
    val manualCurrentPrice: Double,
    val note: String
)

data class PersonalWatchStockSeed(
    val symbol: String,
    val name: String,
    val market: Market,
    val reason: String,
    val industry: String,
    val watchBaseClose: Double?,
    val watchBaseCloseDate: String?
)

data class PersonalMonitorConfigSeed(
    val symbol: String,
    val enabled: Boolean,
    val costProfitPercent: Double?,
    val costLossPercent: Double?,
    val changePercent: Double?,
    val volumeSurgeMultiplier: Double?,
    val volumeShrinkMultiplier: Double?,
    val rsiHigh: Double?,
    val rsiLow: Double?,
    val gapPercent: Double?,
    val trailingProfitStartPercent: Double?,
    val trailingWarningDrawdownPercent: Double?,
    val trailingCriticalDrawdownPercent: Double?
)
