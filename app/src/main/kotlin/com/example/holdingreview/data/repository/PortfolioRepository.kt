package com.example.holdingreview.data.repository

import com.example.holdingreview.domain.model.DailyReview
import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.HoldingInput
import com.example.holdingreview.domain.model.OcrHoldingDraft
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.TradeOperation
import com.example.holdingreview.domain.model.TradeOperationInput
import com.example.holdingreview.domain.model.WatchStock
import com.example.holdingreview.domain.model.WatchStockInput
import kotlinx.coroutines.flow.Flow

/**
 * 组合数据、行情刷新、OCR 导入和复盘的仓库边界。
 */
interface PortfolioRepository {
    /**
     * 观察已合并最新缓存行情的持仓。
     */
    fun observeHoldings(): Flow<List<Holding>>
    /**
     * 观察已合并最新缓存行情的单个持仓。
     */
    fun observeHolding(id: String): Flow<Holding?>
    /**
     * 观察已合并缓存行情的关注股票。
     */
    fun observeWatchStocks(): Flow<List<WatchStock>>
    /**
     * 观察某只股票的交易操作记录。
     */
    fun observeTradeOperations(symbol: String): Flow<List<TradeOperation>>
    /**
     * 观察所有缓存行情快照。
     */
    fun observeQuotes(): Flow<List<QuoteSnapshot>>
    /**
     * 观察最新保存的每日复盘。
     */
    fun observeLatestReview(): Flow<DailyReview?>
    /**
     * 查询单只股票的最新行情，并返回标准化行情快照。
     */
    suspend fun lookupQuote(symbol: String): Result<QuoteSnapshot>

    /**
     * 导入内置个人数据里本地缺失的记录；没有内置数据且数据库为空时写入演示数据。
     */
    suspend fun seedIfEmpty(): Result<Boolean>
    /**
     * 根据表单输入创建或更新持仓。
     */
    suspend fun upsertHolding(input: HoldingInput)
    /**
     * 持久化一条已确认的 OCR 持仓草稿。
     */
    suspend fun upsertOcrDraft(draft: OcrHoldingDraft)
    /**
     * 根据本地 id 删除持仓。
     */
    suspend fun deleteHolding(id: String)
    /**
     * 创建或更新关注股票。
     */
    suspend fun upsertWatchStock(input: WatchStockInput)
    /**
     * 根据股票代码删除关注股票。
     */
    suspend fun deleteWatchStock(symbol: String)
    /**
     * 保存一笔交易操作，并同步更新持仓。
     */
    suspend fun addTradeOperation(input: TradeOperationInput): Result<TradeOperation>
    /**
     * 为持仓和关注股票刷新远程行情。
     */
    suspend fun refreshQuotes(): Result<Int>
    /**
     * 保存生成的每日复盘。
     */
    suspend fun saveDailyReview(review: DailyReview)
}
