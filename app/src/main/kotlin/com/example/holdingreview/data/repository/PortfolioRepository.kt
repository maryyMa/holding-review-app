package com.example.holdingreview.data.repository

import com.example.holdingreview.domain.model.DailyReview
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.HoldingInput
import com.example.holdingreview.domain.model.OcrHoldingDraft
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.WatchStock
import com.example.holdingreview.domain.model.WatchStockInput
import kotlinx.coroutines.flow.Flow

interface PortfolioRepository {
    fun observeHoldings(): Flow<List<Holding>>
    fun observeHolding(id: String): Flow<Holding?>
    fun observeWatchStocks(): Flow<List<WatchStock>>
    fun observeQuotes(): Flow<List<QuoteSnapshot>>
    fun observeLatestReview(): Flow<DailyReview?>

    suspend fun seedIfEmpty()
    suspend fun upsertHolding(input: HoldingInput)
    suspend fun upsertOcrDraft(draft: OcrHoldingDraft)
    suspend fun deleteHolding(id: String)
    suspend fun upsertWatchStock(input: WatchStockInput)
    suspend fun deleteWatchStock(symbol: String)
    suspend fun refreshQuotes(): Result<Int>
    suspend fun saveDailyReview(review: DailyReview)
}
