package com.example.holdingreview.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.holdingreview.domain.usecase.RefreshQuotesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 在后台刷新行情缓存的 WorkManager 任务。
 */
@HiltWorker
class QuoteRefreshWorker @AssistedInject constructor(
    /** 由 WorkManager 提供的 Worker 上下文。 */
    @Assisted context: Context,
    /** 由 WorkManager 提供的 Worker 参数。 */
    @Assisted params: WorkerParameters,
    /** 执行实际行情刷新的用例。 */
    private val refreshQuotesUseCase: RefreshQuotesUseCase
) : CoroutineWorker(context, params) {
    /**
     * 执行刷新；远程请求失败时稍后重试。
     */
    override suspend fun doWork(): Result {
        return refreshQuotesUseCase()
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() }
            )
    }
}
