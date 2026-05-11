package com.example.holdingreview.domain.usecase

import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.Market
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateDailyReviewUseCaseTest {
    private val calculator = CalculatePortfolioUseCase()
    private val analyzer = AnalyzeMarketSignalsUseCase(calculator)
    private val useCase = GenerateDailyReviewUseCase(calculator)

    @Test
    fun `generates review and ai prompt`() {
        val holdings = listOf(
            Holding("1", "600519", "贵州茅台", Market.SH, 100.0, 1560.0, 1586.3, 1.24, "核心观察仓", 0)
        )
        val signals = analyzer(holdings, emptyList())

        val draft = useCase(holdings, signals)

        assertTrue(draft.summary.contains("持仓复盘"))
        assertTrue(draft.summary.contains("主要贡献"))
        assertTrue(draft.aiPrompt.contains("投资复盘助手"))
    }
}
