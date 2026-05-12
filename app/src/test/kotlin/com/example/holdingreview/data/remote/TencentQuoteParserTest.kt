package com.example.holdingreview.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 腾讯行情响应解析的单元测试。
 */
class TencentQuoteParserTest {
    /** 被测解析器。 */
    private val parser = TencentQuoteParser()

    /**
     * 验证有效的腾讯行情记录会映射为标准化行情。
     */
    @Test
    fun `parses tencent quote text`() {
        val raw = """
            v_sh600519="1~贵州茅台~600519~1586.30~1566.88~1570.00~12500~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~20260507150000~19.42~1.24~1600.00~1550.00~0~0~123456789~2.10~0~0~0~0~3.20";
        """.trimIndent()

        val quotes = parser.parse(raw)

        assertEquals(1, quotes.size)
        assertEquals("600519", quotes.first().symbol)
        assertEquals("贵州茅台", quotes.first().name)
        assertEquals(1586.30, quotes.first().latestPrice, 0.01)
        assertEquals(1.24, quotes.first().changePercent, 0.01)
    }
}
