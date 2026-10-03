package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Helper for integer minor-unit money arithmetic (Paisa).
 * Rule: Always store and calculate financial values as integer paisa (1 PKR = 100 paisa)
 * to avoid floating-point inaccuracies.
 */
object PaisaHelper {
    private val pkrFormatter: DecimalFormat by lazy {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }
        DecimalFormat("#,##0", symbols)
    }

    private val pkrWithDecimalsFormatter: DecimalFormat by lazy {
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }
        DecimalFormat("#,##0.00", symbols)
    }

    /**
     * Converts a PKR decimal input string or double to paisa (Long)
     * e.g. "1500.50" -> 150050L
     */
    fun pkrStringToPaisa(pkrString: String): Long {
        val clean = pkrString.trim().replace(",", "")
        if (clean.isEmpty()) return 0L
        val amountDouble = clean.toDoubleOrNull() ?: 0.0
        return Math.round(amountDouble * 100.0)
    }

    fun pkrDoubleToPaisa(amountPkr: Double): Long {
        return Math.round(amountPkr * 100.0)
    }

    fun paisaToPkrDouble(paisa: Long): Double {
        return paisa / 100.0
    }

    /**
     * Formats paisa to PKR display string
     * e.g. 2500000L -> "PKR 25,000"
     * If there are fractional paisa/cents, displays decimals e.g. "PKR 250.50"
     */
    fun formatPkr(paisa: Long, includeCurrencyCode: Boolean = true, currencyCode: String = "PKR"): String {
        val prefix = if (includeCurrencyCode) "$currencyCode " else ""
        val isNegative = paisa < 0
        val absPaisa = Math.abs(paisa)
        val wholePkr = absPaisa / 100
        val remainderPaisa = absPaisa % 100

        val formattedAmount = if (remainderPaisa == 0L) {
            pkrFormatter.format(wholePkr)
        } else {
            pkrWithDecimalsFormatter.format(absPaisa / 100.0)
        }

        return if (isNegative) "-$prefix$formattedAmount" else "$prefix$formattedAmount"
    }

    /**
     * Formats paisa as raw PKR number string for editing fields
     * e.g. 150000L -> "1500" or 150050L -> "1500.50"
     */
    fun paisaToEditableString(paisa: Long): String {
        if (paisa == 0L) return ""
        val whole = paisa / 100
        val rem = paisa % 100
        return if (rem == 0L) {
            whole.toString()
        } else {
            String.format(Locale.US, "%.2f", paisa / 100.0)
        }
    }

    /**
     * Calculates percentage of spent vs budget limit (0.0 to 100.0+)
     */
    fun calculateBudgetPercentage(spentMinor: Long, limitMinor: Long): Float {
        if (limitMinor <= 0L) return 0f
        return (spentMinor.toFloat() / limitMinor.toFloat()) * 100f
    }
}
