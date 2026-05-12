package com.example.holdingreview.domain.usecase

import com.example.holdingreview.domain.model.KLinePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateTechnicalIndicatorsUseCaseTest {
    private val useCase = CalculateTechnicalIndicatorsUseCase()

    @Test
    fun `calculates moving averages rsi volume average and gap`() {
        val lines = (1..15).map { day ->
            KLinePoint(
                symbol = "600519",
                date = "2026-01-${day.toString().padStart(2, '0')}",
                open = day.toDouble(),
                close = day.toDouble(),
                high = day.toDouble() + 0.5,
                low = day.toDouble() - 0.5,
                volume = day * 100.0,
                amount = day * 1000.0
            )
        } + KLinePoint(
            symbol = "600519",
            date = "2026-01-16",
            open = 20.0,
            close = 16.0,
            high = 21.0,
            low = 15.5,
            volume = 1600.0,
            amount = 16000.0
        )

        val indicators = useCase(lines)

        assertEquals(14.0, indicators.ma5 ?: 0.0, 0.001)
        assertEquals(11.5, indicators.ma10 ?: 0.0, 0.001)
        assertEquals(1300.0, indicators.volumeMa5 ?: 0.0, 0.001)
        assertTrue((indicators.rsi14 ?: 0.0) > 90.0)
        assertTrue((indicators.gapPercent ?: 0.0) > 20.0)
    }
}
