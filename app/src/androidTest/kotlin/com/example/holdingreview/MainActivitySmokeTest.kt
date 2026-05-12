package com.example.holdingreview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * 主 Compose Activity 的仪器化冒烟测试。
 */
class MainActivitySmokeTest {
    /** 用于启动 [MainActivity] 的 Compose 测试规则。 */
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * 验证应用外壳可以渲染预期标题。
     */
    @Test
    fun homeScreenShowsAppTitle() {
        composeRule.onNodeWithText("持仓复盘").assertIsDisplayed()
    }
}
