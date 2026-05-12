package com.example.holdingreview.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.holdingreview.data.local.entities.DailyReviewEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用于已保存每日复盘的 Room DAO。
 */
@Dao
interface DailyReviewDao {
    /**
     * 观察最新保存的复盘；没有则为空。
     */
    @Query("SELECT * FROM daily_reviews ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeLatest(): Flow<DailyReviewEntity?>

    /**
     * 按交易日期插入或更新每日复盘。
     */
    @Upsert
    suspend fun upsert(entity: DailyReviewEntity)
}
