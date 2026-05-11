package com.example.holdingreview.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.holdingreview.data.local.entities.QuoteSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteSnapshotDao {
    @Query("SELECT * FROM quote_snapshots")
    fun observeAll(): Flow<List<QuoteSnapshotEntity>>

    @Query("SELECT * FROM quote_snapshots")
    suspend fun getAllOnce(): List<QuoteSnapshotEntity>

    @Upsert
    suspend fun upsertAll(entities: List<QuoteSnapshotEntity>)
}
