package com.example.holdingreview.data.remote

import com.example.holdingreview.domain.model.Market

data class RemoteQuote(
    val symbol: String,
    val name: String,
    val market: Market,
    val latestPrice: Double,
    val previousClose: Double?,
    val changePercent: Double,
    val volume: Double?,
    val turnoverAmount: Double?,
    val turnoverRate: Double?,
    val amplitude: Double?
)
