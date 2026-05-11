package com.example.holdingreview.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseOcrHoldingUseCaseTest {
    private val useCase = ParseOcrHoldingUseCase()

    @Test
    fun `parses holding drafts from broker screenshot text`() {
        val rawText = """
            持仓 股票代码 股票名称 数量 成本价 现价
            600519 贵州茅台 100 1560.00 1586.30
            300750 宁德时代 200 186.50 181.80
        """.trimIndent()

        val drafts = useCase(rawText)

        assertEquals(2, drafts.size)
        assertEquals("600519", drafts[0].symbol)
        assertEquals(100.0, drafts[0].quantity, 0.01)
        assertEquals(1560.0, drafts[0].costPrice, 0.01)
        assertTrue(drafts[0].confidence >= 0.8f)
    }
}
