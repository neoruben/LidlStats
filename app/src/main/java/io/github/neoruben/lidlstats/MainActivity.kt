package io.github.neoruben.lidlstats

import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.github.neoruben.lidlstats.data.StoredTicket
import io.github.neoruben.lidlstats.data.TicketDatabaseHelper
import io.github.neoruben.lidlstats.lidl.LidlApi
import io.github.neoruben.lidlstats.lidl.LidlSessionExpiredException
import io.github.neoruben.lidlstats.parser.LidlReceiptParser
import io.github.neoruben.lidlstats.stats.StatsCalculator
import io.github.neoruben.lidlstats.ui.LidlLoginScreen
import io.github.neoruben.lidlstats.ui.LidlMainScaffold
import io.github.neoruben.lidlstats.ui.SyncProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var dbHelper: TicketDatabaseHelper
    private lateinit var api: LidlApi
    private val prefs by lazy { getSharedPreferences("lidl_prefs", MODE_PRIVATE) }

    private var tickets by mutableStateOf<List<StoredTicket>>(emptyList())
    private var summary by mutableStateOf(StatsCalculator.calculate(emptyList()))
    private var isLoggedIn by mutableStateOf(false)
    private var showLogin by mutableStateOf(false)
    private var syncProgress by mutableStateOf<SyncProgress?>(null)
    private var statusMessage by mutableStateOf<String?>(null)
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        dbHelper = TicketDatabaseHelper(applicationContext)
        api = LidlApi(LidlApi.browserUserAgent(this))
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
        isLoggedIn = prefs.getBoolean("logged_in", false)
        reloadFromDatabase()
        if (isLoggedIn) sync(openLoginIfExpired = false)

        setContent {
            MaterialTheme {
                if (showLogin) {
                    BackHandler { showLogin = false; syncProgress = null }
                    LidlLoginScreen(
                        startUrl = LidlApi.LOGIN_URL,
                        onPageFinished = ::onLoginPageFinished,
                        onClose = { showLogin = false; syncProgress = null }
                    )
                } else {
                    LidlMainScaffold(
                        tickets = tickets,
                        summary = summary,
                        isLoggedIn = isLoggedIn,
                        syncProgress = syncProgress,
                        statusMessage = statusMessage,
                        errorMessage = errorMessage,
                        onSyncClick = {
                            if (isLoggedIn) sync(openLoginIfExpired = true) else openLogin()
                        },
                        onLogoutClick = ::logout
                    )
                }
            }
        }
    }

    private fun openLogin() {
        errorMessage = null
        statusMessage = null
        showLogin = true
    }

    private var checkingLogin = false

    /** Cuando el WebView vuelve a "Mis tickets" de lidl.es comprobamos si la API ya responde. */
    private fun onLoginPageFinished(url: String) {
        if (!url.startsWith("${LidlApi.BASE}/mre/") || checkingLogin) return
        checkingLogin = true
        lifecycleScope.launch {
            CookieManager.getInstance().flush()
            val ok = api.isLoggedIn()
            checkingLogin = false
            if (ok && showLogin) {
                saveLoggedIn(true)
                showLogin = false
                sync(openLoginIfExpired = false)
            }
        }
    }

    private fun saveLoggedIn(value: Boolean) {
        isLoggedIn = value
        prefs.edit().putBoolean("logged_in", value).apply()
    }

    private fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        saveLoggedIn(false)
        statusMessage = "Sesión de Lidl cerrada. Tus tickets siguen guardados en el móvil."
    }

    private fun reloadFromDatabase() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { dbHelper.getAllTickets() }
            tickets = loaded
            summary = StatsCalculator.calculate(loaded)
        }
    }

    private fun sync(openLoginIfExpired: Boolean) {
        if (syncProgress != null) return
        errorMessage = null
        statusMessage = null
        syncProgress = SyncProgress(0, 0, "Consultando tus tickets en Lidl...")

        lifecycleScope.launch {
            try {
                val all = api.listAllTickets()
                val known = withContext(Dispatchers.IO) { dbHelper.getKnownTicketIds() }
                val pending = all.filterNot { it.id in known }

                if (pending.isEmpty()) {
                    statusMessage = "Todo al día: tienes tus ${all.size} tickets guardados."
                    return@launch
                }

                var inserted = 0
                var failed = 0
                pending.forEachIndexed { index, summaryItem ->
                    syncProgress = SyncProgress(
                        index + 1, pending.size,
                        "Descargando ticket ${index + 1} de ${pending.size}..."
                    )
                    try {
                        val detail = api.getTicket(summaryItem.id)
                        val html = detail.html
                        if (html == null) {
                            failed++
                        } else {
                            val parsed = withContext(Dispatchers.Default) {
                                LidlReceiptParser.parse(
                                    ticketId = summaryItem.id,
                                    html = html,
                                    dateTimeIso = LidlReceiptParser.normalizeDate(summaryItem.date.ifBlank { detail.date }),
                                    storeName = summaryItem.store.ifBlank { detail.storeName },
                                    listTotal = summaryItem.totalAmount
                                )
                            }
                            if (withContext(Dispatchers.IO) { dbHelper.insertTicketIfNew(parsed) }) inserted++
                        }
                    } catch (e: LidlSessionExpiredException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("LidlSync", "Error con ticket ${summaryItem.id}: ${e.message}")
                        failed++
                    }
                }
                reloadFromDatabase()
                statusMessage = buildString {
                    append("Sincronización completada: $inserted ")
                    append(if (inserted == 1) "ticket nuevo" else "tickets nuevos")
                    if (failed > 0) append(" ($failed sin poder leer)")
                    append(".")
                }
            } catch (e: LidlSessionExpiredException) {
                saveLoggedIn(false)
                if (openLoginIfExpired) {
                    showLogin = true
                } else {
                    errorMessage = e.message
                }
            } catch (e: Exception) {
                errorMessage = "Error al sincronizar: ${e.message}"
            } finally {
                syncProgress = null
            }
        }
    }
}
