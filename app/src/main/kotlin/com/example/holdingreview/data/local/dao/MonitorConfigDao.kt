package com.example.holdingreview.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.holdingreview.data.local.entities.MonitorConfigEntity
import kotlinx.coroutines.flow.Flow

/**
 * 管理每只股票监控规则配置的 Room DAO。
 */
@Dao
interface MonitorConfigDao {
    @Query("SELECT * FROM monitor_configs ORDER BY symbol")
    fun observeAll(): Flow<List<MonitorConfigEntity>>

    @Query("SELECT * FROM monitor_configs WHERE symbol = :symbol LIMIT 1")
    fun observeBySymbol(symbol: String): Flow<MonitorConfigEntity?>

    @Query("SELECT * FROM monitor_configs")
    suspend fun getAllOnce(): List<MonitorConfigEntity>

    @Query("SELECT * FROM monitor_configs WHERE symbol = :symbol LIMIT 1")
    suspend fun findBySymbol(symbol: String): MonitorConfigEntity?

    @Upsert
    suspend fun upsert(entity: MonitorConfigEntity)

    @Query("UPDATE monitor_configs SET enabled = :enabled, updatedAtMillis = :updatedAtMillis WHERE symbol = :symbol")
    suspend fun updateEnabled(symbol: String, enabled: Boolean, updatedAtMillis: Long)

    @Query("UPDATE monitor_configs SET highestPrice = :highestPrice, updatedAtMillis = :updatedAtMillis WHERE symbol = :symbol")
    suspend fun updateHighestPrice(symbol: String, highestPrice: Double, updatedAtMillis: Long)
}
