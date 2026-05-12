package com.example.holdingreview.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.holdingreview.data.local.entities.QuoteSnapshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用于缓存行情快照的 Room DAO。
 */
@Dao
interface QuoteSnapshotDao {
    /**
     * 观察所有缓存行情快照。
     */
    @Query("SELECT * FROM quote_snapshots")
    fun observeAll(): Flow<List<QuoteSnapshotEntity>>

    /**
     * 一次性读取所有缓存行情快照。
     */
    @Query("SELECT * FROM quote_snapshots")
    suspend fun getAllOnce(): List<QuoteSnapshotEntity>

    /**
     * 插入或更新刷新后的行情快照。
     */
    @Upsert
    suspend fun upsertAll(entities: List<QuoteSnapshotEntity>)
}
