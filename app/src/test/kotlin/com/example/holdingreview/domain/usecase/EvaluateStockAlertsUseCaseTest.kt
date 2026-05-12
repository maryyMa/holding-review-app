package com.example.holdingreview.domain.usecase

import com.example.holdingreview.domain.model.KLinePoint
import com.example.holdingreview.domain.model.Market
import com.example.holdingreview.domain.model.MonitorAlertLevel
import com.example.holdingreview.domain.model.MonitorAlertType
import com.example.holdingreview.domain.model.MonitorConfig
import com.example.holdingreview.domain.model.MonitorTarget
import com.example.holdingreview.domain.model.QuoteSnapshot
import com.example.holdingreview.domain.model.SecurityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateStockAlertsUseCaseTest {
    private val useCase = EvaluateStockAlertsUseCase(CalculateTechnicalIndicatorsUseCase())

    @Test
    fun `multiple rule hits produce critical alert level`() {
        val hits = useCase(
            target = target(costPrice = 100.0),
            quote = quote(latestPrice = 115.0, changePercent = 5.2, volume = 4000.0),
            config = MonitorConfig.defaultFor("600519"),
            kLines = risingKLines(),
            highestPrice = 130.0,
            now = 1000L
        )

        assertTrue(hits.any { it.type == MonitorAlertType.COST_PROFIT })
        assertTrue(hits.any { it.type == MonitorAlertType.CHANGE_RISE })
        assertTrue(hits.any { it.type == MonitorAlertType.TRAILING_STOP_CRITICAL })
        assertEquals(MonitorAlertLevel.CRITICAL, useCase.levelFor(hits))
    }

    @Test
    fun `watch only target skips cost and trailing rules`() {
        val hits = useCase(
            target = target(costPrice = null),
            quote = quote(latestPrice = 115.0, changePercent = 5.2, volume = 4000.0),
            config = MonitorConfig.defaultFor("600519"),
            kLines = risingKLines(),
            highestPrice = 130.0,
            now = 1000L
        )

        assertTrue(hits.none { it.type == MonitorAlertType.COST_PROFIT })
        assertTrue(hits.none { it.type == MonitorAlertType.TRAILING_STOP_CRITICAL })
        assertTrue(hits.any { it.type == MonitorAlertType.CHANGE_RISE })
    }

    private fun target(costPrice: Double?): MonitorTarget {
        return MonitorTarget(
            symbol = "600519",
            name = "贵州茅台",
            market = Market.SH,
            securityType = SecurityType.STOCK,
            costPrice = costPrice,
            latestPrice = 115.0,
            dayChangePercent = 5.2,
            isHolding = costPrice != null,
            isWatched = true
        )
    }

    private fun quote(latestPrice: Double, changePercent: Double, volume: Double): QuoteSnapshot {
        return QuoteSnapshot(
            symbol = "600519",
            name = "贵州茅台",
            market = Market.SH,
            latestPrice = latestPrice,
            previousClose = 109.0,
            changePercent = changePercent,
            volume = volume,
            turnoverAmount = null,
            turnoverRate = null,
            amplitude = null,
            source = "Tencent",
            updatedAtMillis = 0L
        )
    }

    private fun risingKLines(): List<KLinePoint> {
        return (1..30).map { index ->
            KLinePoint(
                symbol = "600519",
                date = "2026-02-${index.toString().padStart(2, '0')}",
                open = index.toDouble(),
                close = index.toDouble(),
                high = index.toDouble() + 0.2,
                low = index.toDouble() - 0.2,
                volume = 1000.0,
                amount = 10_000.0
            )
        }
    }
}
