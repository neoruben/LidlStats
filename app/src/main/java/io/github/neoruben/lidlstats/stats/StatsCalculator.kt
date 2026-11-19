package io.github.neoruben.lidlstats.stats

import io.github.neoruben.lidlstats.data.StoredTicket
import java.time.YearMonth
import java.util.Locale

data class PeriodSpendStat(
    val periodKey: String,   // "2026-11"
    val periodLabel: String, // "Nov 26"
    val totalAmount: Double,
    val totalVatAmount: Double,
    val ticketCount: Int
)

data class ProductSpendStat(
    val productKey: String,
    val displayName: String,
    val unit: String?, // "kg" si se vende al peso
    val totalQuantity: Double,
    val purchaseCount: Int,
    val totalAmount: Double
) {
    /** Para ordenar por cantidad: unidades compradas; los productos al peso cuentan por compra. */
    val quantityRank: Double get() = if (unit == "kg") purchaseCount.toDouble() else totalQuantity
}

data class SpendSummary(
    val totalSpent: Double,
    val totalVat: Double,
    val ticketCount: Int,
    val averageTicket: Double,
    /** Todos los meses entre el primer y el último ticket (incluidos los meses sin compras), en orden ascendente. */
    val monthlyStats: List<PeriodSpendStat>,
    val productStats: List<ProductSpendStat>
)

object StatsCalculator {

    private val MONTH_NAMES_ES = listOf(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    )

    fun calculate(tickets: List<StoredTicket>): SpendSummary {
        if (tickets.isEmpty()) {
            return SpendSummary(0.0, 0.0, 0, 0.0, emptyList(), emptyList())
        }
        val totalSpent = tickets.sumOf { it.totalAmount }
        val totalVat = tickets.sumOf { it.totalVatAmount }

        val byMonth = tickets.groupBy { it.dateTimeIso.take(7) }
        val months = byMonth.keys.mapNotNull { runCatching { YearMonth.parse(it) }.getOrNull() }
        val monthlyStats = mutableListOf<PeriodSpendStat>()
        if (months.isNotEmpty()) {
            var m = months.min()
            val last = months.max()
            while (m <= last) {
                val key = String.format(Locale.US, "%04d-%02d", m.year, m.monthValue)
                val list = byMonth[key].orEmpty()
                monthlyStats.add(
                    PeriodSpendStat(
                        periodKey = key,
                        periodLabel = "${MONTH_NAMES_ES[m.monthValue - 1]} ${m.year % 100}",
                        totalAmount = list.sumOf { it.totalAmount },
                        totalVatAmount = list.sumOf { it.totalVatAmount },
                        ticketCount = list.size
                    )
                )
                m = m.plusMonths(1)
            }
        }

        val products = linkedMapOf<String, Acc>()
        for (ticket in tickets.sortedBy { it.dateTimeIso }) {
            val seen = hashSetOf<String>()
            for (item in ticket.items) {
                val name = item.description.trim().replace(Regex("\\s+"), " ")
                val key = name.uppercase(Locale.ROOT)
                if (key.isBlank()) continue
                val acc = products.getOrPut(key) { Acc(name, item.unit) }
                acc.quantity += item.quantity
                acc.amount += item.totalPrice
                if (acc.unit == null && item.unit != null) acc.unit = item.unit
                if (seen.add(key)) acc.purchases++
            }
        }

        return SpendSummary(
            totalSpent = totalSpent,
            totalVat = totalVat,
            ticketCount = tickets.size,
            averageTicket = totalSpent / tickets.size,
            monthlyStats = monthlyStats,
            productStats = products.map { (k, a) ->
                ProductSpendStat(k, a.name, a.unit, a.quantity, a.purchases, a.amount)
            }.sortedByDescending { it.totalAmount }
        )
    }

    private class Acc(
        val name: String,
        var unit: String?,
        var quantity: Double = 0.0,
        var purchases: Int = 0,
        var amount: Double = 0.0
    )
}
