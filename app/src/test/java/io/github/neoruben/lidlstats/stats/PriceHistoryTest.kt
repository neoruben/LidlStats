package io.github.neoruben.lidlstats.stats

import io.github.neoruben.lidlstats.data.StoredTicket
import io.github.neoruben.lidlstats.parser.ParsedTicketItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceHistoryTest {

    private fun ticket(id: Long, date: String, vararg items: ParsedTicketItem) = StoredTicket(
        id = id, ticketId = "t$id", storeName = "Lidl", dateTimeIso = "$date 10:00",
        totalAmount = items.sumOf { it.totalPrice }, totalVatAmount = 0.0,
        items = items.toList(), vatBreakdown = emptyList(), receiptText = ""
    )

    private fun item(name: String, unitPrice: Double?, qty: Double = 1.0, total: Double = (unitPrice ?: 0.0) * qty, unit: String? = null) =
        ParsedTicketItem(qty, unit, name, unitPrice, total)

    private val tickets = listOf(
        // Orden desordenado a propósito: el histórico debe salir cronológico.
        ticket(3, "2026-03-10", item("PAN  BARRA", 0.35, qty = 2.0)),
        ticket(1, "2026-01-05", item("Pan barra", 0.29), item("LECHE", 0.89)),
        ticket(2, "2026-02-01", item("PAN BARRA", 0.32, qty = 3.0, total = 0.75)), // con descuento
        ticket(4, "2026-04-01", item("LECHE", 0.95))
    )

    @Test
    fun historyIsChronologicalAndMatchesNormalizedName() {
        val h = StatsCalculator.priceHistory(tickets, "PAN BARRA")
        assertEquals(listOf("2026-01-05", "2026-02-01", "2026-03-10"), h.points.map { it.dateIso })
        assertEquals(listOf(0.29, 0.32, 0.35), h.points.map { it.unitPrice })
        assertEquals(0.25, h.points[1].paidUnitPrice, 1e-9)
    }

    @Test
    fun summaryStats() {
        val h = StatsCalculator.priceHistory(tickets, "PAN BARRA")
        assertEquals(0.35, h.currentPrice!!, 1e-9)
        assertEquals(0.29, h.minPrice!!, 1e-9)
        assertEquals(0.35, h.maxPrice!!, 1e-9)
        assertEquals(0.32, h.averagePrice!!, 1e-9)
        assertEquals(0.06, h.change!!, 1e-9)
        assertEquals(0.06 / 0.29 * 100, h.changePercent!!, 1e-9)
    }

    @Test
    fun singlePurchaseHasNoChange() {
        val h = StatsCalculator.priceHistory(tickets.take(1), "PAN BARRA")
        assertEquals(1, h.points.size)
        assertNull(h.change)
    }

    @Test
    fun missingUnitPriceFallsBackToPaidPerUnit() {
        val h = StatsCalculator.priceHistory(
            listOf(ticket(1, "2026-01-01", item("PLATANO", null, qty = 0.5, total = 0.75, unit = "kg"))), "PLATANO"
        )
        assertEquals(1.5, h.points.single().unitPrice, 1e-9)
        assertEquals("kg", h.unit)
    }

    @Test
    fun keyMatchesProductStats() {
        val keys = StatsCalculator.calculate(tickets).productStats.map { it.productKey }.toSet()
        keys.forEach { assertEquals(it, StatsCalculator.priceHistory(tickets, it).productKey) }
        assertEquals(setOf("PAN BARRA", "LECHE"), keys)
    }
}
