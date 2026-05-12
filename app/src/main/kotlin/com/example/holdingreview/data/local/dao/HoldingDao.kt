package com.example.holdingreview.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.holdingreview.data.local.entities.HoldingEntity
import kotlinx.coroutines.flow.Flow

/**
 * 用于观察和修改组合持仓的 Room DAO。
 */
@Dao
interface HoldingDao {
    /**
     * 按最近本地更新时间观察所有持仓。
     */
    @Query("SELECT * FROM holdings ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<HoldingEntity>>

    /**
     * 根据本地 id 观察单个持仓。
     */
    @Query("SELECT * FROM holdings WHERE id = :id")
    fun observeById(id: String): Flow<HoldingEntity?>

    /**
     * 根据代码查找持仓，用于创建或更新逻辑。
     */
    @Query("SELECT * FROM holdings WHERE symbol = :symbol LIMIT 1")
    suspend fun findBySymbol(symbol: String): HoldingEntity?

    /**
     * 为刷新和种子数据流程一次性读取所有持仓。
     */
    @Query("SELECT * FROM holdings")
    suspend fun getAllOnce(): List<HoldingEntity>

    /**
     * 插入或更新一条持仓记录。
     */
    @Upsert
    suspend fun upsert(entity: HoldingEntity)

    /**
     * 插入或更新多条持仓记录。
     */
    @Upsert
    suspend fun upsertAll(entities: List<HoldingEntity>)

    /**
     * 根据本地 id 删除持仓。
     */
    @Query("DELETE FROM holdings WHERE id = :id")
    suspend fun deleteById(id: String)
}
