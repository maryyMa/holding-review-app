package com.example.holdingreview.di

import android.content.Context
import androidx.room.Room
import com.example.holdingreview.data.local.HoldingReviewDatabase
import com.example.holdingreview.data.local.dao.DailyReviewDao
import com.example.holdingreview.data.local.dao.HoldingDao
import com.example.holdingreview.data.local.dao.KLineCacheDao
import com.example.holdingreview.data.local.dao.MonitorAlertDao
import com.example.holdingreview.data.local.dao.MonitorConfigDao
import com.example.holdingreview.data.local.dao.QuoteSnapshotDao
import com.example.holdingreview.data.local.dao.WatchStockDao
import com.example.holdingreview.data.remote.EastmoneyKLineApi
import com.example.holdingreview.data.remote.TencentQuoteApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 提供应用级基础设施依赖的 Hilt 模块。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * 提供所有 DAO 共用的 Room 数据库实例。
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HoldingReviewDatabase {
        return Room.databaseBuilder(
            context,
            HoldingReviewDatabase::class.java,
            "holding_review.db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    /**
     * 从 Room 数据库提供持仓 DAO。
     */
    @Provides fun provideHoldingDao(database: HoldingReviewDatabase): HoldingDao = database.holdingDao()
    /**
     * 从 Room 数据库提供关注列表 DAO。
     */
    @Provides fun provideWatchStockDao(database: HoldingReviewDatabase): WatchStockDao = database.watchStockDao()
    /**
     * 从 Room 数据库提供行情快照 DAO。
     */
    @Provides fun provideQuoteSnapshotDao(database: HoldingReviewDatabase): QuoteSnapshotDao = database.quoteSnapshotDao()
    /**
     * 从 Room 数据库提供每日复盘 DAO。
     */
    @Provides fun provideDailyReviewDao(database: HoldingReviewDatabase): DailyReviewDao = database.dailyReviewDao()
    /**
     * 从 Room 数据库提供监控配置 DAO。
     */
    @Provides fun provideMonitorConfigDao(database: HoldingReviewDatabase): MonitorConfigDao = database.monitorConfigDao()
    /**
     * 从 Room 数据库提供监控预警 DAO。
     */
    @Provides fun provideMonitorAlertDao(database: HoldingReviewDatabase): MonitorAlertDao = database.monitorAlertDao()
    /**
     * 从 Room 数据库提供 K 线缓存 DAO。
     */
    @Provides fun provideKLineCacheDao(database: HoldingReviewDatabase): KLineCacheDao = database.kLineCacheDao()

    /**
     * 提供带短网络超时和基础日志的 OkHttp 客户端。
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    /**
     * 提供用于腾讯行情请求的 Retrofit 实现。
     */
    @Provides
    @Singleton
    fun provideTencentQuoteApi(okHttpClient: OkHttpClient): TencentQuoteApi {
        return Retrofit.Builder()
            .baseUrl("https://qt.gtimg.cn/")
            .client(okHttpClient)
            .build()
            .create(TencentQuoteApi::class.java)
    }

    /**
     * 提供用于东方财富日 K 线请求的 Retrofit 实现。
     */
    @Provides
    @Singleton
    fun provideEastmoneyKLineApi(okHttpClient: OkHttpClient): EastmoneyKLineApi {
        return Retrofit.Builder()
            .baseUrl("https://push2his.eastmoney.com/")
            .client(okHttpClient)
            .build()
            .create(EastmoneyKLineApi::class.java)
    }
}
