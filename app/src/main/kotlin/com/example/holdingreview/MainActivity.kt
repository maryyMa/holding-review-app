package com.example.holdingreview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.holdingreview.presentation.HoldingReviewApp
import com.example.holdingreview.ui.theme.HoldingReviewTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HoldingReviewTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HoldingReviewApp()
                }
            }
        }
    }
}
