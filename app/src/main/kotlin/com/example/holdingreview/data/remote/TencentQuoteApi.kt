package com.example.holdingreview.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 腾讯纯文本行情接口的 Retrofit API。
 */
interface TencentQuoteApi {
    /**
     * 根据逗号分隔的腾讯查询字符串请求行情记录。
     */
    @GET(".")
    suspend fun getQuotes(@Query("q", encoded = true) query: String): ResponseBody
}
