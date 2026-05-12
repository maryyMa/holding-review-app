package com.example.holdingreview.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class EastmoneyKLineParserTest {
    private val parser = EastmoneyKLineParser()

    @Test
    fun `parses eastmoney daily kline response`() {
        val raw = """
            {
              "data": {
                "klines": [
                  "2026-05-08,10.00,10.50,10.80,9.90,12345,678900,0,0,0,0",
                  "2026-05-11,10.60,10.20,10.70,10.10,22345,778900,0,0,0,0"
                ]
              }
            }
        """.trimIndent()

        val points = parser.parse("600519", raw)

        assertEquals(2, points.size)
        assertEquals("600519", points[0].symbol)
        assertEquals("2026-05-08", points[0].date)
        assertEquals(10.0, points[0].open, 0.001)
        assertEquals(10.5, points[0].close, 0.001)
        assertEquals(12345.0, points[0].volume, 0.001)
    }
}
