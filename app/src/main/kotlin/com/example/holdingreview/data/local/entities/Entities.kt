package com.example.holdingreview.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "holdings",
    indices = [Index(value = ["symbol"], unique = true)]
)
data class HoldingEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val name: String,
    val market: String,
    val quantity: Double,
    val costPrice: Double,
    val manualCurrentPrice: Double,
    val note: String,
    val updatedAtMillis: Long
)

@Entity(tableName = "watch_stocks")
data class WatchStockEntity(
    @PrimaryKey val symbol: String,
    val name: String,
    val market: String,
    val reason: String,
    val tags: String,
    val updatedAtMillis: Long
)

@Entity(tableName = "quote_snapshots")
data class QuoteSnapshotEntity(
    @PrimaryKey val symbol: String,
    val name: String,
    val market: String,
    val latestPrice: Double,
    val previousClose: Double?,
    val changePercent: Double,
    val volume: Double?,
    val turnoverAmount: Double?,
    val turnoverRate: Double?,
    val amplitude: Double?,
    val source: String,
    val updatedAtMillis: Long
)

@Entity(tableName = "daily_reviews")
data class DailyReviewEntity(
    @PrimaryKey val tradeDate: String,
    val summary: String,
    val aiPrompt: String,
    val createdAtMillis: Long
)
