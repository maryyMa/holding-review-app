package com.example.holdingreview.domain.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val moneyFormat = DecimalFormat("¥#,##0.00", DecimalFormatSymbols(Locale.CHINA))
private val numberFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.CHINA))
private val percentFormat = DecimalFormat("0.00", DecimalFormatSymbols(Locale.CHINA))

fun money(value: Double): String = moneyFormat.format(value)

fun signedMoney(value: Double): String = when {
    value > 0 -> "+${money(value)}"
    value < 0 -> "-${money(kotlin.math.abs(value))}"
    else -> money(0.0)
}

fun percent(value: Double): String = "${percentFormat.format(value)}%"

fun signedPercent(value: Double): String = when {
    value > 0 -> "+${percent(value)}"
    value < 0 -> "-${percent(kotlin.math.abs(value))}"
    else -> percent(0.0)
}

fun compactNumber(value: Double?): String {
    if (value == null) return "--"
    return when {
        value >= 100_000_000 -> "${percentFormat.format(value / 100_000_000)}亿"
        value >= 10_000 -> "${percentFormat.format(value / 10_000)}万"
        else -> numberFormat.format(value)
    }
}
