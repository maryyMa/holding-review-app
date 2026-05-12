package com.example.holdingreview

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.holdingreview.worker.QuoteRefreshWorker
import com.example.holdingreview.worker.StockMonitorWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 负责接入 Hilt 并调度周期行情刷新的 Application 类。
 */
@HiltAndroidApp
class HoldingReviewApplication : Application(), Configuration.Provider {
    /** 支持 WorkManager 任务依赖注入所需的 Hilt Worker 工厂。 */
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** 将 Worker 创建委托给 Hilt 的 WorkManager 配置。 */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * 应用创建后启动应用级后台调度。
     */
    override fun onCreate() {
        super.onCreate()
        scheduleQuoteRefresh()
        scheduleStockMonitor()
    }

    /**
     * 在网络可用时调度唯一的周期行情刷新任务。
     */
    private fun scheduleQuoteRefresh() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<QuoteRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "quote_refresh",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * 调度股票监控任务。WorkManager 周期任务的稳定最小间隔为 15 分钟。
     */
    private fun scheduleStockMonitor() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<StockMonitorWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "stock_monitor",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
