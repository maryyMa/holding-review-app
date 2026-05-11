package com.example.holdingreview.domain.usecase

import com.example.holdingreview.domain.model.Holding
import com.example.holdingreview.domain.model.Market
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatePortfolioUseCaseTest {
    private val useCase = CalculatePortfolioUseCase()

    @Test
    fun `calculates portfolio totals and contributors`() {
        val holdings = listOf(
            holding("600519", "贵州茅台", quantity = 100.0, cost = 100.0, price = 120.0, change = 2.0),
            holding("300750", "宁德时代", quantity = 200.0, cost = 50.0, price = 45.0, change = -1.0)
        )

        val snapshot = useCase(holdings)

        assertEquals(2, snapshot.holdingCount)
        assertEquals(21_000.0, snapshot.marketValue, 0.01)
        assertEquals(20_000.0, snapshot.costValue, 0.01)
        assertEquals(1_000.0, snapshot.totalProfit, 0.01)
        assertEquals("600519", snapshot.topContributor?.symbol)
        assertEquals("300750", snapshot.topDrag?.symbol)
    }

    private fun holding(
        symbol: String,
        name: String,
        quantity: Double,
        cost: Double,
        price: Double,
        change: Double
    ): Holding {
        return Holding(
            id = symbol,
            symbol = symbol,
            name = name,
            market = Market.fromSymbol(symbol),
            quantity = quantity,
            costPrice = cost,
            currentPrice = price,
            dayChangePercent = change,
            note = "",
            updatedAtMillis = 0
        )
    }
}
