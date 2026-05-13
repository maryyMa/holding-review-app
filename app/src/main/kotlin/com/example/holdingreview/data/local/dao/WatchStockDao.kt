package com.example.holdingreview.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.holdingreview.data.local.entities.WatchStockEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用于关注股票持久化的 Room DAO。
 */
@Dao
interface WatchStockDao {
    /**
     * 按最近更新时间观察关注列表行。
     */
    @Query("SELECT * FROM watch_stocks ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<WatchStockEntity>>

    /**
     * 为行情刷新流程一次性读取关注列表。
     */
    @Query("SELECT * FROM watch_stocks")
    suspend fun getAllOnce(): List<WatchStockEntity>

    /**
     * 根据股票代码读取一条关注记录。
     */
    @Query("SELECT * FROM watch_stocks WHERE symbol = :symbol LIMIT 1")
    suspend fun findBySymbol(symbol: String): WatchStockEntity?

    /**
     * 插入或更新一只关注股票。
     */
    @Upsert
    suspend fun upsert(entity: WatchStockEntity)

    /**
     * 插入或更新多只关注股票。
     */
    @Upsert
    suspend fun upsertAll(entities: List<WatchStockEntity>)

    /**
     * 只补齐关注基准收盘价，不改变用户最近编辑时间。
     */
    @Query("UPDATE watch_stocks SET watchBaseClose = :baseClose, watchBaseCloseDate = :baseCloseDate WHERE symbol = :symbol")
    suspend fun updateWatchBaseClose(symbol: String, baseClose: Double, baseCloseDate: String)

    /**
     * 根据股票代码删除关注股票。
     */
    @Query("DELETE FROM watch_stocks WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)
}
