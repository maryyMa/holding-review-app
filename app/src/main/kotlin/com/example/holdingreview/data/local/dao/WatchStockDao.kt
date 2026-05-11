package com.example.holdingreview.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.holdingreview.data.local.entities.WatchStockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchStockDao {
    @Query("SELECT * FROM watch_stocks ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<WatchStockEntity>>

    @Query("SELECT * FROM watch_stocks")
    suspend fun getAllOnce(): List<WatchStockEntity>

    @Upsert
    suspend fun upsert(entity: WatchStockEntity)

    @Upsert
    suspend fun upsertAll(entities: List<WatchStockEntity>)

    @Query("DELETE FROM watch_stocks WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)
}
