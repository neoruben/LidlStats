package io.github.neoruben.lidlstats.parser

import org.jsoup.Jsoup

/**
 * Parser del campo `htmlPrintedReceipt` que devuelve la API de tickets de Lidl
 * (`/mre/api/v1/tickets/{id}`). El HTML es un `<pre>` con `<span>` que llevan los datos
 * en atributos:
 *  - `span.article`  data-art-description, data-art-quantity ("2" o "0,463" si es al peso),
 *                    data-unit-price, data-tax-type. Los productos al peso tienen un segundo
 *                    span "0,463 kg x 17,59 EUR/kg" que se ignora.
 *  - `span.discount` descuento (importe negativo) que se aplica al artículo anterior.
 *  - `span[data-tax-percentage]` desglose de IVA (base y cuota).
 *  - `span[id^=purchase_summary]` con "Total   12,34".
 */
object LidlReceiptParser {

    private val AMOUNT = Regex("""-?\d+(?:\.\d{3})*,\d{2}""")

    fun parse(
        ticketId: String,
        html: String,
        dateTimeIso: String,
        storeName: String,
        listTotal: Double?
    ): ParsedTicket {
        val doc = Jsoup.parse(html)
        val items = mutableListOf<ParsedTicketItem>()
        var lastArticleId: String? = null

        // Líneas de la lista de compra: artículos y cualquier línea con importe negativo
        // (PROMO, PROMO LIDL PLUS, "Desc." de cupones...), que se resta al artículo anterior.
        val lines = doc.select("span[id^=purchase_list_line]").ifEmpty { doc.select("span.article, span.discount") }
        for (span in lines) {
            val text = span.text().replace(Regex("\\s+"), " ").trim()
            val isNegativeLine = Regex("""-\d+(?:\.\d{3})*,\d{2}\s*[A-Z]?$""").containsMatchIn(text)
            if (!span.hasClass("article") && !span.hasClass("discount") && !isNegativeLine) continue
            if (span.hasClass("discount") || (!span.hasClass("article") && isNegativeLine)) {
                val amount = lastAmount(text) ?: continue
                if (items.isNotEmpty()) {
                    val last = items.removeAt(items.lastIndex)
                    items.add(last.copy(totalPrice = round2(last.totalPrice + amount)))
                }
                continue
            }
            val spanId = span.id().ifBlank { null }
            if (spanId != null && spanId == lastArticleId) continue
            if (Regex("""\bkg x\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) &&
                !text.startsWith(span.attr("data-art-description").trim())
            ) continue
            lastArticleId = spanId

            val description = span.attr("data-art-description").trim()
                .ifBlank { text.substringBefore("  ").trim() }
            if (description.isBlank()) continue

            val qtyAttr = span.attr("data-art-quantity").trim()
            val isWeight = qtyAttr.contains(',') || qtyAttr.contains('.')
            val quantity = parseNumber(qtyAttr) ?: 1.0
            val unitPrice = parseNumber(span.attr("data-unit-price"))
            val total = lastAmount(text) ?: round2((unitPrice ?: 0.0) * quantity)

            items.add(
                ParsedTicketItem(
                    quantity = quantity,
                    unit = if (isWeight) "kg" else null,
                    description = description,
                    unitPrice = unitPrice,
                    totalPrice = total
                )
            )
        }

        val vat = doc.select("span[data-tax-percentage]").mapNotNull { span ->
            val rate = Regex("""\d+""").find(span.attr("data-tax-percentage"))?.value?.toIntOrNull()
                ?: return@mapNotNull null
            // El texto impreso es "A   4%   0,11   2,78   2,89" = cuota IVA, base, PVP.
            // (data-tax-base-amount es en realidad el PVP con IVA.)
            val nums = AMOUNT.findAll(span.text()).map { parseNumber(it.value)!! }.toList()
            if (nums.size >= 3) {
                ParsedVatBreakdown(rate, nums[nums.size - 2], nums[nums.size - 3], nums[nums.size - 1])
            } else {
                val gross = parseNumber(span.attr("data-tax-base-amount")) ?: return@mapNotNull null
                val amount = parseNumber(span.attr("data-tax-amount")) ?: return@mapNotNull null
                ParsedVatBreakdown(rate, round2(gross - amount), amount, gross)
            }
        }.distinct()

        val printedTotal = doc.select("span[id^=purchase_summary]")
            .map { it.text().trim() }
            .firstOrNull { it.startsWith("Total", ignoreCase = true) }
            ?.let { lastAmount(it) }

        val total = listTotal?.takeIf { it > 0.0 } ?: printedTotal ?: items.sumOf { it.totalPrice }

        val pre = doc.selectFirst("pre")
        val receiptText = (pre?.wholeText() ?: doc.body().wholeText())
            .lines()
            .dropWhile { it.isBlank() }
            .joinToString("\n") { it.trimEnd() }
            .trimEnd()

        return ParsedTicket(
            ticketId = ticketId,
            storeName = storeName,
            dateTimeIso = dateTimeIso,
            totalAmount = round2(total),
            totalVatAmount = round2(vat.sumOf { it.vatAmount }),
            items = items,
            vatBreakdown = vat,
            receiptText = receiptText
        )
    }

    /** "2026-08-25T10:14:05+00:00" → "2026-08-25 10:14" (Lidl manda la hora local con +00:00). */
    fun normalizeDate(raw: String): String {
        val m = Regex("""(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})""").find(raw)
        return if (m != null) "${m.groupValues[1]} ${m.groupValues[2]}" else raw.take(16)
    }

    private fun lastAmount(text: String): Double? =
        AMOUNT.findAll(text).lastOrNull()?.value?.let { parseNumber(it) }

    fun parseNumber(raw: String?): Double? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val normalized = if (s.contains(',')) s.replace(".", "").replace(',', '.') else s
        return normalized.toDoubleOrNull()
    }

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0
}
