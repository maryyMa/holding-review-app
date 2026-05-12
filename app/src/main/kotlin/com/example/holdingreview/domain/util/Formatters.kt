package com.example.holdingreview.domain.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val moneyFormat = DecimalFormat("¥#,##0.00", DecimalFormatSymbols(Locale.CHINA))
/** 紧凑行情数值使用的通用数字格式化器。 */
private val numberFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.CHINA))
/** 保留两位小数的百分比格式化器。 */
private val percentFormat = DecimalFormat("0.00", DecimalFormatSymbols(Locale.CHINA))

/**
 * 将数字格式化为货币金额。
 */
fun money(value: Double): String = moneyFormat.format(value)

/**
 * 将货币金额格式化为带明确正负号的文本。
 */
fun signedMoney(value: Double): String = when {
    value > 0 -> "+${money(value)}"
    value < 0 -> "-${money(kotlin.math.abs(value))}"
    else -> money(0.0)
}

/**
 * 将数字格式化为百分比字符串。
 */
fun percent(value: Double): String = "${percentFormat.format(value)}%"

/**
 * 将百分比格式化为带明确正负号的文本。
 */
fun signedPercent(value: Double): String = when {
    value > 0 -> "+${percent(value)}"
    value < 0 -> "-${percent(kotlin.math.abs(value))}"
    else -> percent(0.0)
}

/**
 * 使用中文市场单位格式化可选的大额行情数值。
 */
fun compactNumber(value: Double?): String {
    if (value == null) return "--"
    return when {
        value >= 100_000_000 -> "${percentFormat.format(value / 100_000_000)}亿"
        value >= 10_000 -> "${percentFormat.format(value / 10_000)}万"
        else -> numberFormat.format(value)
    }
}
