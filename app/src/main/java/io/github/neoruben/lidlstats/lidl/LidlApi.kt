package io.github.neoruben.lidlstats.lidl

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LidlSessionExpiredException : Exception("La sesión de Lidl ha caducado. Vuelve a iniciar sesión.")

data class LidlTicketSummary(
    val id: String,
    val date: String,
    val totalAmount: Double,
    val articlesCount: Int,
    val store: String,
    val isHtml: Boolean
)

data class LidlTicketDetail(
    val id: String,
    val date: String,
    val storeName: String,
    val html: String?
)

/**
 * Cliente de la API web de "Mis tickets" de lidl.es. La autenticación son las cookies
 * de sesión (sobre todo `authToken`) que deja el login hecho en el WebView, así que
 * basta con reenviar las cookies del CookieManager.
 */
class LidlApi(private val userAgent: String) {

    companion object {
        const val BASE = "https://www.lidl.es"
        const val LOGIN_URL =
            "$BASE/mre/purchase-history?client_id=SpainEcommerceClient&country_code=es&language=es-ES"

        /** User-agent de Chrome móvil sin la marca "; wv" de WebView (algunos logins bloquean WebViews). */
        fun browserUserAgent(context: android.content.Context): String =
            android.webkit.WebSettings.getDefaultUserAgent(context)
                .replace("; wv", "")
                .replace(Regex("""Version/\d+(\.\d+)* """), "")
    }

    suspend fun isLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        runCatching { get("/mre/api/v1/tickets?country=ES&page=1") }.isSuccess
    }

    /** Devuelve todos los tickets de la cuenta (paginando). */
    suspend fun listAllTickets(): List<LidlTicketSummary> = withContext(Dispatchers.IO) {
        val result = mutableListOf<LidlTicketSummary>()
        var page = 1
        while (page < 500) {
            val json = JSONObject(get("/mre/api/v1/tickets?country=ES&page=$page"))
            val items = json.optJSONArray("items") ?: break
            if (items.length() == 0) break
            for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                result.add(
                    LidlTicketSummary(
                        id = it.getString("id"),
                        date = it.optString("date"),
                        totalAmount = it.optDouble("totalAmount", 0.0),
                        articlesCount = it.optInt("articlesCount", 0),
                        store = it.optString("store"),
                        isHtml = it.optBoolean("isHtml", true)
                    )
                )
            }
            val total = json.optInt("totalCount", -1)
            if (total in 0..result.size) break
            page++
        }
        result.distinctBy { it.id }
    }

    suspend fun getTicket(id: String): LidlTicketDetail = withContext(Dispatchers.IO) {
        val json = JSONObject(get("/mre/api/v1/tickets/$id?country=ES&languageCode=es-ES"))
        val ticket = json.getJSONObject("ticket")
        val store = ticket.optJSONObject("store")
        LidlTicketDetail(
            id = ticket.optString("id", id),
            date = ticket.optString("date"),
            storeName = store?.optString("name").orEmpty(),
            html = ticket.optString("htmlPrintedReceipt").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun get(path: String): String {
        val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Referer", LOGIN_URL)
            CookieManager.getInstance().getCookie(BASE)?.let { setRequestProperty("Cookie", it) }
        }
        try {
            val code = conn.responseCode
            if (code == 401 || code == 403 || code in 300..399) throw LidlSessionExpiredException()
            if (code !in 200..299) throw IllegalStateException("Lidl respondió HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
