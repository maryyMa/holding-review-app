package com.example.holdingreview.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.holdingreview.data.local.entities.KLineCacheEntity

/**
 * 管理日 K 线缓存的 Room DAO。
 */
@Dao
interface KLineCacheDao {
    @Query("SELECT * FROM kline_cache WHERE symbol = :symbol ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(symbol: String, limit: Int): List<KLineCacheEntity>

    @Upsert
    suspend fun upsertAll(entities: List<KLineCacheEntity>)

    @Query("DELETE FROM kline_cache WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)
}
