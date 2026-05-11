package com.example.holdingreview.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.holdingreview.data.local.entities.HoldingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HoldingDao {
    @Query("SELECT * FROM holdings ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings WHERE id = :id")
    fun observeById(id: String): Flow<HoldingEntity?>

    @Query("SELECT * FROM holdings WHERE symbol = :symbol LIMIT 1")
    suspend fun findBySymbol(symbol: String): HoldingEntity?

    @Query("SELECT * FROM holdings")
    suspend fun getAllOnce(): List<HoldingEntity>

    @Upsert
    suspend fun upsert(entity: HoldingEntity)

    @Upsert
    suspend fun upsertAll(entities: List<HoldingEntity>)

    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun deleteById(id: String)
}
