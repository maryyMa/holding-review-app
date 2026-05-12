package com.example.holdingreview.data.remote

import com.example.holdingreview.domain.model.Market

/**
 * 远程行情数据源返回的标准化行情。
 */
data class RemoteQuote(
    /** 六位股票或基金代码。 */
    val symbol: String,
    /** 行情提供方返回的展示名称。 */
    val name: String,
    /** 根据代码推断出的市场。 */
    val market: Market,
    /** 最新成交价。 */
    val latestPrice: Double,
    /** 可用时的前收盘价。 */
    val previousClose: Double?,
    /** 日涨跌幅百分比。 */
    val changePercent: Double,
    /** 可用时的成交量。 */
    val volume: Double?,
    /** 可用时的成交额。 */
    val turnoverAmount: Double?,
    /** 可用时的换手率百分比。 */
    val turnoverRate: Double?,
    /** 可用时的日内振幅百分比。 */
    val amplitude: Double?
)
