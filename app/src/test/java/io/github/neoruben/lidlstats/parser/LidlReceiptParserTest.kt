package io.github.neoruben.lidlstats.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ticket inventado con la misma estructura que el `htmlPrintedReceipt` de Lidl. */
class LidlReceiptParserTest {

    private val html = """
        <html><head><meta http-equiv="Content-Type" content="text/html; charset=utf-8"/></head><body><pre>
        <span class="header" data-till-country="ES" data-receipt-language="es"><span id="header_line_1"></span>
        <span id="header_line_2">       LIDL SUPERMERCADOS S.A.U.        </span>
        </span><span class="purchase_list"><span id="purchase_list_line_1" class="currency" data-currency="€">                                       EUR</span>
        <span id="purchase_list_line_2" class="article" data-art-id="111" data-art-quantity="3" data-unit-price="0,29" data-tax-type="A" data-art-description="PAN BARRA">PAN BARRA             0,29x   3     0,87 A</span>
        <span id="purchase_list_line_3" class="discount" data-promotion-id="9">     PROMO 3x0,75€                 -0,12</span>
        <span id="purchase_list_line_4" class="article" data-art-id="222" data-unit-price="3,99" data-tax-type="C" data-art-description="CUERDA">CUERDA                               3,99 C</span>
        <span id="purchase_list_line_4b">     Desc.                         -0,90</span>
        <span id="purchase_list_line_5" class="article" data-art-id="333" data-art-quantity="0,628" data-unit-price="1,49" data-tax-type="A" data-art-description="PLATANO">PLATANO                              0,94 A</span>
        <span id="purchase_list_line_5" class="article" data-art-id="333" data-art-quantity="0,628" data-unit-price="1,49" data-tax-type="A" data-art-description="PLATANO">     0,628 kg x 1,49 EUR/kg</span>
        </span><span class="purchase_summary"><span id="purchase_summary_1">------------------------------------------</span>
        <span id="purchase_summary_2">Total                               5,68</span>
        <span id="purchase_summary_3" data-tender-description="ENTREGA">ENTREGA                             5,68</span>
        </span><span class="vat_info">
        <span id="vat_line_1" data-tax-type="A" data-tax-percentage="4" data-tax-base-amount="1,63" data-tax-amount="0,06">A   4%      0,06        1,63        1,69</span>
        <span id="vat_line_2" data-tax-type="C" data-tax-percentage="21" data-tax-base-amount="3,30" data-tax-amount="0,69">C  21%      0,69        3,30        3,99</span>
        </span></pre></body></html>
    """.trimIndent()

    @Test
    fun parsesArticlesDiscountsWeightAndVat() {
        val t = LidlReceiptParser.parse("T1", html, "2026-08-25 10:14", "Tienda", listTotal = null)

        assertEquals(3, t.items.size)
        val pan = t.items[0]
        assertEquals("PAN BARRA", pan.description)
        assertEquals(3.0, pan.quantity, 0.0001)
        assertNull(pan.unit)
        assertEquals(0.75, pan.totalPrice, 0.0001) // 0,87 - 0,12

        assertEquals(1.0, t.items[1].quantity, 0.0001)
        assertEquals(3.09, t.items[1].totalPrice, 0.0001) // 3,99 - 0,90 (Desc.)

        val platano = t.items[2]
        assertEquals("kg", platano.unit)
        assertEquals(0.628, platano.quantity, 0.0001)
        assertEquals(0.94, platano.totalPrice, 0.0001)

        assertEquals(5.68, t.totalAmount, 0.0001)
        assertEquals(2, t.vatBreakdown.size)
        assertEquals(0.75, t.totalVatAmount, 0.0001)
        assertEquals(21, t.vatBreakdown[1].vatRatePercent)
        assertEquals(3.30, t.vatBreakdown[1].baseAmount, 0.0001)
        assertEquals(3.99, t.vatBreakdown[1].totalAmount, 0.0001)
        assertTrue(t.receiptText.contains("PROMO 3x0,75€"))
    }

    @Test
    fun listTotalWins() {
        val t = LidlReceiptParser.parse("T1", html, "2026-08-25 10:14", "Tienda", listTotal = 9.99)
        assertEquals(9.99, t.totalAmount, 0.0001)
    }

    @Test
    fun normalizesDate() {
        assertEquals("2026-08-25 10:14", LidlReceiptParser.normalizeDate("2026-08-25T10:14:05+00:00"))
    }
}
