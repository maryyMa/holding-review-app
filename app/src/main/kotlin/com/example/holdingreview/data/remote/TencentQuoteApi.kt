package com.example.holdingreview.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

interface TencentQuoteApi {
    @GET(".")
    suspend fun getQuotes(@Query("q", encoded = true) query: String): ResponseBody
}
