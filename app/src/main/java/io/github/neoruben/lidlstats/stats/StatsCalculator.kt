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

/** Precio de un producto en una compra: PVP unitario (€/ud o €/kg) y lo pagado de verdad tras descuentos. */
data class PricePoint(
    val dateIso: String, // YYYY-MM-DD
    val unitPrice: Double,
    val paidUnitPrice: Double
)

data class ProductPriceHistory(
    val productKey: String,
    val displayName: String,
    val unit: String?,
    /** Una entrada por ticket, en orden cronológico. */
    val points: List<PricePoint>
) {
    val currentPrice: Double? get() = points.lastOrNull()?.unitPrice
    val firstPrice: Double? get() = points.firstOrNull()?.unitPrice
    val minPrice: Double? get() = points.minOfOrNull { it.unitPrice }
    val maxPrice: Double? get() = points.maxOfOrNull { it.unitPrice }
    val averagePrice: Double? get() = if (points.isEmpty()) null else points.sumOf { it.unitPrice } / points.size
    /** Variación del precio actual respecto a la primera compra. */
    val change: Double? get() = if (points.size < 2) null else currentPrice!! - firstPrice!!
    val changePercent: Double?
        get() = change?.let { c -> firstPrice!!.takeIf { it > 0.0 }?.let { c / it * 100.0 } }
}

object StatsCalculator {

    private val MONTH_NAMES_ES = listOf(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    )

    private fun normalizedName(description: String) = description.trim().replace(Regex("\\s+"), " ")
    private fun productKey(name: String) = name.uppercase(Locale.ROOT)

    /** Histórico de precios de un producto (misma clave que [ProductSpendStat.productKey]). */
    fun priceHistory(tickets: List<StoredTicket>, productKey: String): ProductPriceHistory {
        var displayName = productKey
        var unit: String? = null
        val points = mutableListOf<PricePoint>()
        for (ticket in tickets.sortedBy { it.dateTimeIso }) {
            val lines = ticket.items.filter { productKey(normalizedName(it.description)) == productKey }
            if (lines.isEmpty()) continue
            displayName = normalizedName(lines.first().description)
            if (unit == null) unit = lines.firstNotNullOfOrNull { it.unit }
            val quantity = lines.sumOf { it.quantity }
            val paid = lines.sumOf { it.totalPrice }
            val paidUnit = if (quantity > 0) paid / quantity else paid
            // PVP de la etiqueta; si el ticket no lo trae, lo pagado por unidad.
            val shelf = lines.mapNotNull { it.unitPrice }.takeIf { it.isNotEmpty() }?.average() ?: paidUnit
            points.add(PricePoint(ticket.dateTimeIso.take(10), shelf, paidUnit))
        }
        return ProductPriceHistory(productKey, displayName, unit, points)
    }

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
                val name = normalizedName(item.description)
                val key = productKey(name)
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
