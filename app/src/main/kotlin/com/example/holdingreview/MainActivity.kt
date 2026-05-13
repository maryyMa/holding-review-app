package com.example.holdingreview

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.holdingreview.presentation.HoldingReviewApp
import com.example.holdingreview.ui.theme.HoldingReviewTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 承载 Compose 应用的单 Activity 入口。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var notificationAlertId by mutableStateOf<String?>(null)

    /**
     * 使用应用主题和根导航器创建 Compose 内容树。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationAlertId = intent.getStringExtra(EXTRA_ALERT_ID)
        setContent {
            HoldingReviewTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val alertId = notificationAlertId
                    HoldingReviewApp(
                        notificationAlertId = alertId,
                        onNotificationAlertHandled = {
                            if (notificationAlertId == alertId) {
                                notificationAlertId = null
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationAlertId = intent.getStringExtra(EXTRA_ALERT_ID)
    }

    companion object {
        const val EXTRA_ALERT_ID = "com.example.holdingreview.extra.ALERT_ID"
    }
}
