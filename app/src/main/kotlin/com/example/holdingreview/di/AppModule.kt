package com.example.holdingreview.di

import android.content.Context
import androidx.room.Room
import com.example.holdingreview.data.local.HoldingReviewDatabase
import com.example.holdingreview.data.local.dao.DailyReviewDao
import com.example.holdingreview.data.local.dao.HoldingDao
import com.example.holdingreview.data.local.dao.QuoteSnapshotDao
import com.example.holdingreview.data.local.dao.WatchStockDao
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

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
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

    @Provides fun provideHoldingDao(database: HoldingReviewDatabase): HoldingDao = database.holdingDao()
    @Provides fun provideWatchStockDao(database: HoldingReviewDatabase): WatchStockDao = database.watchStockDao()
    @Provides fun provideQuoteSnapshotDao(database: HoldingReviewDatabase): QuoteSnapshotDao = database.quoteSnapshotDao()
    @Provides fun provideDailyReviewDao(database: HoldingReviewDatabase): DailyReviewDao = database.dailyReviewDao()

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

    @Provides
    @Singleton
    fun provideTencentQuoteApi(okHttpClient: OkHttpClient): TencentQuoteApi {
        return Retrofit.Builder()
            .baseUrl("https://qt.gtimg.cn/")
            .client(okHttpClient)
            .build()
            .create(TencentQuoteApi::class.java)
    }
}
