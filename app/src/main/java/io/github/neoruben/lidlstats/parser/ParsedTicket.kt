package io.github.neoruben.lidlstats.parser

data class ParsedTicketItem(
    val quantity: Double,
    val unit: String?, // null para unidades, "kg" para productos al peso
    val description: String,
    val unitPrice: Double?,
    val totalPrice: Double // importe final de la línea, con descuentos aplicados
)

data class ParsedVatBreakdown(
    val vatRatePercent: Int,
    val baseAmount: Double,
    val vatAmount: Double,
    val totalAmount: Double
)

data class ParsedTicket(
    val ticketId: String,
    val storeName: String,
    val dateTimeIso: String, // YYYY-MM-DD HH:mm
    val totalAmount: Double,
    val totalVatAmount: Double,
    val items: List<ParsedTicketItem>,
    val vatBreakdown: List<ParsedVatBreakdown>,
    val receiptText: String // ticket tal como se imprime (texto monoespaciado)
)
