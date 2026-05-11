package com.example.holdingreview.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.holdingreview.data.local.entities.DailyReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReviewDao {
    @Query("SELECT * FROM daily_reviews ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeLatest(): Flow<DailyReviewEntity?>

    @Upsert
    suspend fun upsert(entity: DailyReviewEntity)
}
