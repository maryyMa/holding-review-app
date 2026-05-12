package com.example.holdingreview.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.holdingreview.domain.usecase.RunStockMonitorUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 后台执行股票监控并生成本地预警的 WorkManager 任务。
 */
@HiltWorker
class StockMonitorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runStockMonitor: RunStockMonitorUseCase,
    private val notifier: StockMonitorNotifier
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runStockMonitor().fold(
            onSuccess = { result ->
                notifier.notify(result.alerts)
                Result.success()
            },
            onFailure = { Result.retry() }
        )
    }
}
