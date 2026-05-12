package com.example.holdingreview.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.QueryMap

/**
 * 东方财富历史 K 线接口。
 */
interface EastmoneyKLineApi {
    @GET("api/qt/stock/kline/get")
    suspend fun getKLines(@QueryMap(encoded = true) params: Map<String, String>): ResponseBody
}
