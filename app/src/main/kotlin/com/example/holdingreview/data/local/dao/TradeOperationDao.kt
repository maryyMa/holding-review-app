package com.example.holdingreview.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.holdingreview.data.local.entities.TradeOperationEntity
import kotlinx.coroutines.flow.Flow

/**
 * 管理用户手动记录的交易操作。
 */
@Dao
interface TradeOperationDao {
    @Query("SELECT * FROM trade_operations WHERE symbol = :symbol ORDER BY occurredAtMillis DESC, createdAtMillis DESC")
    fun observeBySymbol(symbol: String): Flow<List<TradeOperationEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TradeOperationEntity)
}
