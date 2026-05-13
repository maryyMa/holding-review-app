package com.example.holdingreview.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.holdingreview.data.local.entities.MonitorAlertEntity
import kotlinx.coroutines.flow.Flow

/**
 * 管理股票监控预警记录的 Room DAO。
 */
@Dao
interface MonitorAlertDao {
    @Query(
        """
        SELECT * FROM monitor_alerts
        ORDER BY
            CASE level
                WHEN 'CRITICAL' THEN 0
                WHEN 'WARNING' THEN 1
                ELSE 2
            END ASC,
            triggeredAtMillis DESC
        """
    )
    fun observeAll(): Flow<List<MonitorAlertEntity>>

    @Query(
        """
        SELECT * FROM monitor_alerts
        WHERE symbol = :symbol
        ORDER BY
            CASE level
                WHEN 'CRITICAL' THEN 0
                WHEN 'WARNING' THEN 1
                ELSE 2
            END ASC,
            triggeredAtMillis DESC
        """
    )
    fun observeBySymbol(symbol: String): Flow<List<MonitorAlertEntity>>

    @Query("SELECT * FROM monitor_alerts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<MonitorAlertEntity?>

    @Query("SELECT COUNT(*) FROM monitor_alerts WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM monitor_alerts
        WHERE symbol = :symbol AND type = :type AND triggeredAtMillis >= :afterMillis
        ORDER BY triggeredAtMillis DESC
        LIMIT 1
        """
    )
    suspend fun findRecent(symbol: String, type: String, afterMillis: Long): MonitorAlertEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<MonitorAlertEntity>)

    @Query("UPDATE monitor_alerts SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE monitor_alerts SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM monitor_alerts WHERE isRead = 1")
    suspend fun deleteRead()

    @Query("DELETE FROM monitor_alerts WHERE symbol = :symbol AND isRead = 1")
    suspend fun deleteReadBySymbol(symbol: String)

    @Query("DELETE FROM monitor_alerts WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)
}
