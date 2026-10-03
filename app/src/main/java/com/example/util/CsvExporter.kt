package com.example.util

import com.example.data.model.TransactionWithCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    fun generateCsv(transactions: List<TransactionWithCategory>): String {
        val sb = StringBuilder()
        sb.append("Transaction ID,Date,Type,Category,Note,Amount (PKR),Amount (Paisa)\n")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = DateUtils.KARACHI_TIMEZONE
        }

        for (item in transactions) {
            val tx = item.transaction
            val categoryName = item.category?.name ?: "Uncategorized"
            val dateStr = dateFormat.format(Date(tx.occurred_on))
            val pkrDouble = PaisaHelper.paisaToPkrDouble(tx.amount_minor)
            val noteEscaped = "\"" + tx.note.replace("\"", "\"\"") + "\""
            val catEscaped = "\"" + categoryName.replace("\"", "\"\"") + "\""

            sb.append("${tx.id},$dateStr,${tx.type},$catEscaped,$noteEscaped,$pkrDouble,${tx.amount_minor}\n")
        }
        return sb.toString()
    }
}
