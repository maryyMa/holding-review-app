package com.example.holdingreview.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.holdingreview.domain.usecase.RefreshQuotesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class QuoteRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refreshQuotesUseCase: RefreshQuotesUseCase
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return refreshQuotesUseCase()
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() }
            )
    }
}
