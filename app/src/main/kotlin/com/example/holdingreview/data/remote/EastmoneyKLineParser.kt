package com.example.holdingreview.data.remote

import com.example.holdingreview.domain.model.KLinePoint
import org.json.JSONObject
import javax.inject.Inject

/**
 * 解析东方财富日 K 线 JSON 响应。
 */
class EastmoneyKLineParser @Inject constructor() {
    fun parse(symbol: String, raw: String): List<KLinePoint> {
        val data = JSONObject(raw).optJSONObject("data") ?: return emptyList()
        val klines = data.optJSONArray("klines") ?: return emptyList()
        return buildList {
            for (index in 0 until klines.length()) {
                val fields = klines.optString(index).split(",")
                if (fields.size < 7) continue
                val open = fields[1].toDoubleOrNull() ?: continue
                val close = fields[2].toDoubleOrNull() ?: continue
                val high = fields[3].toDoubleOrNull() ?: continue
                val low = fields[4].toDoubleOrNull() ?: continue
                val volume = fields[5].toDoubleOrNull() ?: continue
                val amount = fields[6].toDoubleOrNull() ?: 0.0
                val point = KLinePoint(
                    symbol = symbol,
                    date = fields[0],
                    open = open,
                    close = close,
                    high = high,
                    low = low,
                    volume = volume,
                    amount = amount
                )
                add(point)
            }
        }
    }
}
