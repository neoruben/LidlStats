package io.github.neoruben.lidlstats.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.neoruben.lidlstats.parser.ParsedTicket
import io.github.neoruben.lidlstats.parser.ParsedTicketItem
import io.github.neoruben.lidlstats.parser.ParsedVatBreakdown

data class StoredTicket(
    val id: Long,
    val ticketId: String,
    val storeName: String,
    val dateTimeIso: String, // YYYY-MM-DD HH:mm
    val totalAmount: Double,
    val totalVatAmount: Double,
    val items: List<ParsedTicketItem>,
    val vatBreakdown: List<ParsedVatBreakdown>,
    val receiptText: String
) {
    /** DD/MM/YYYY */
    val dateDisplay: String
        get() {
            val d = dateTimeIso.substringBefore(" ").split("-")
            return if (d.size == 3) "${d[2]}/${d[1]}/${d[0]}" else dateTimeIso
        }
    val timeDisplay: String get() = dateTimeIso.substringAfter(" ", "")
}

/** SQLite local con los tickets de Lidl. El id de ticket de Lidl es único. */
class TicketDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "lidl_tickets.db", null, 2
) {
    private companion object {
        const val T = "tickets"
        const val I = "ticket_items"
        const val V = "ticket_vat"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $T (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                lidl_ticket_id TEXT NOT NULL UNIQUE,
                store_name TEXT NOT NULL,
                date_time_iso TEXT NOT NULL,
                total_amount REAL NOT NULL,
                total_vat_amount REAL NOT NULL,
                receipt_text TEXT NOT NULL,
                created_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $I (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ticket_id INTEGER NOT NULL,
                quantity REAL NOT NULL,
                unit TEXT,
                description TEXT NOT NULL,
                unit_price REAL,
                total_price REAL NOT NULL,
                FOREIGN KEY(ticket_id) REFERENCES $T(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $V (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ticket_id INTEGER NOT NULL,
                vat_rate_percent INTEGER NOT NULL,
                base_amount REAL NOT NULL,
                vat_amount REAL NOT NULL,
                total_amount REAL NOT NULL,
                FOREIGN KEY(ticket_id) REFERENCES $T(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_tickets_date ON $T(date_time_iso DESC)")
        db.execSQL("CREATE INDEX idx_items_ticket ON $I(ticket_id)")
        db.execSQL("CREATE INDEX idx_vat_ticket ON $V(ticket_id)")
    }

    /** Los tickets se pueden volver a descargar de Lidl: al cambiar el parser se borra todo y se resincroniza. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $V")
        db.execSQL("DROP TABLE IF EXISTS $I")
        db.execSQL("DROP TABLE IF EXISTS $T")
        onCreate(db)
    }

    fun getKnownTicketIds(): Set<String> {
        val ids = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT lidl_ticket_id FROM $T", null).use { c ->
            while (c.moveToNext()) ids.add(c.getString(0))
        }
        return ids
    }

    /** @return true si se insertó, false si ya existía. */
    fun insertTicketIfNew(ticket: ParsedTicket): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT 1 FROM $T WHERE lidl_ticket_id = ?", arrayOf(ticket.ticketId)).use {
                if (it.moveToFirst()) return false
            }
            val ticketId = db.insertOrThrow(T, null, ContentValues().apply {
                put("lidl_ticket_id", ticket.ticketId)
                put("store_name", ticket.storeName)
                put("date_time_iso", ticket.dateTimeIso)
                put("total_amount", ticket.totalAmount)
                put("total_vat_amount", ticket.totalVatAmount)
                put("receipt_text", ticket.receiptText)
                put("created_at_ms", System.currentTimeMillis())
            })
            for (item in ticket.items) {
                db.insertOrThrow(I, null, ContentValues().apply {
                    put("ticket_id", ticketId)
                    put("quantity", item.quantity)
                    put("unit", item.unit)
                    put("description", item.description)
                    item.unitPrice?.let { put("unit_price", it) }
                    put("total_price", item.totalPrice)
                })
            }
            for (vat in ticket.vatBreakdown) {
                db.insertOrThrow(V, null, ContentValues().apply {
                    put("ticket_id", ticketId)
                    put("vat_rate_percent", vat.vatRatePercent)
                    put("base_amount", vat.baseAmount)
                    put("vat_amount", vat.vatAmount)
                    put("total_amount", vat.totalAmount)
                })
            }
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun deleteAll() {
        writableDatabase.delete(T, null, null)
    }

    fun getAllTickets(): List<StoredTicket> {
        val db = readableDatabase
        val tickets = mutableListOf<StoredTicket>()
        db.rawQuery(
            """
            SELECT id, lidl_ticket_id, store_name, date_time_iso, total_amount, total_vat_amount, receipt_text
            FROM $T ORDER BY date_time_iso DESC, id DESC
            """.trimIndent(), null
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                tickets.add(
                    StoredTicket(
                        id = id,
                        ticketId = c.getString(1),
                        storeName = c.getString(2),
                        dateTimeIso = c.getString(3),
                        totalAmount = c.getDouble(4),
                        totalVatAmount = c.getDouble(5),
                        items = loadItems(db, id),
                        vatBreakdown = loadVat(db, id),
                        receiptText = c.getString(6)
                    )
                )
            }
        }
        return tickets
    }

    private fun loadItems(db: SQLiteDatabase, ticketId: Long): List<ParsedTicketItem> {
        val list = mutableListOf<ParsedTicketItem>()
        db.rawQuery(
            "SELECT quantity, unit, description, unit_price, total_price FROM $I WHERE ticket_id = ? ORDER BY id",
            arrayOf(ticketId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    ParsedTicketItem(
                        quantity = c.getDouble(0),
                        unit = c.getString(1),
                        description = c.getString(2),
                        unitPrice = if (c.isNull(3)) null else c.getDouble(3),
                        totalPrice = c.getDouble(4)
                    )
                )
            }
        }
        return list
    }

    private fun loadVat(db: SQLiteDatabase, ticketId: Long): List<ParsedVatBreakdown> {
        val list = mutableListOf<ParsedVatBreakdown>()
        db.rawQuery(
            "SELECT vat_rate_percent, base_amount, vat_amount, total_amount FROM $V WHERE ticket_id = ? ORDER BY vat_rate_percent",
            arrayOf(ticketId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(ParsedVatBreakdown(c.getInt(0), c.getDouble(1), c.getDouble(2), c.getDouble(3)))
            }
        }
        return list
    }
}
