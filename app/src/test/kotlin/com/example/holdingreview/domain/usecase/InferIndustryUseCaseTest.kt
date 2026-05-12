package com.example.holdingreview.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 行业推断逻辑的单元测试。
 */
class InferIndustryUseCaseTest {
    private val inferIndustry = InferIndustryUseCase()

    /**
     * 用户手动填写行业时，应该优先保留用户输入。
     */
    @Test
    fun `manual industry wins`() {
        val result = inferIndustry("600519", "贵州茅台", "核心消费")

        assertEquals("核心消费", result)
    }

    /**
     * 常见代码应该能命中精确行业映射。
     */
    @Test
    fun `known symbol infers industry`() {
        assertEquals("白酒", inferIndustry("600519", "贵州茅台", ""))
        assertEquals("半导体", inferIndustry("688981", "中芯国际", ""))
        assertEquals("银行", inferIndustry("000001", "平安银行", ""))
    }

    /**
     * 未命中精确代码时，按名称关键词推断行业。
     */
    @Test
    fun `name keyword infers industry`() {
        assertEquals("新能源", inferIndustry("300750", "宁德时代电池", ""))
        assertEquals("医药", inferIndustry("600276", "恒瑞医药", ""))
        assertEquals("其他", inferIndustry("123456", "未知公司", ""))
    }
}
