package io.github.neoruben.lidlstats.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.neoruben.lidlstats.data.StoredTicket
import io.github.neoruben.lidlstats.stats.PeriodSpendStat
import io.github.neoruben.lidlstats.stats.ProductSpendStat
import io.github.neoruben.lidlstats.stats.SpendSummary
import java.util.Locale
import kotlin.math.abs

// Colores corporativos de Lidl
internal val LidlBlue = Color(0xFF0050AA)
internal val LidlBlueDark = Color(0xFF003B7E)
internal val LidlBlueSoft = Color(0xFFE6EEF8)
internal val LidlYellow = Color(0xFFFFF000)
internal val LidlYellowSoft = Color(0xFFFFFBC7)
internal val LidlRed = Color(0xFFE60A14)
internal val LidlRedSoft = Color(0xFFFDE7E8)
private val BackgroundScreen = Color(0xFFF4F6F9)
private val CardBorder = Color(0xFFE1E6EE)
private val TextPrimary = Color(0xFF14202E)
private val TextSecondary = Color(0xFF5B6878)

private const val CHART_WINDOW = 5

data class SyncProgress(val current: Int, val total: Int, val message: String)

@Composable
fun LidlMainScaffold(
    tickets: List<StoredTicket>,
    summary: SpendSummary,
    isLoggedIn: Boolean,
    syncProgress: SyncProgress?,
    statusMessage: String?,
    errorMessage: String?,
    onSyncClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedTicket by remember { mutableStateOf<StoredTicket?>(null) }
    val dateRangeText = remember(tickets) {
        if (tickets.isEmpty()) "Sin compras registradas"
        else {
            val oldest = tickets.last().dateDisplay
            val newest = tickets.first().dateDisplay
            if (oldest == newest) "Fecha: $newest" else "Del $oldest al $newest"
        }
    }

    Scaffold(
        containerColor = BackgroundScreen,
        topBar = { Header(dateRangeText, summary) },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = LidlBlue,
                    selectedTextColor = LidlBlue,
                    indicatorColor = LidlYellow,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                )
                listOf(
                    Triple("Tickets", Icons.AutoMirrored.Rounded.ReceiptLong, 0),
                    Triple("Estadísticas", Icons.Rounded.BarChart, 1),
                    Triple("Productos", Icons.Rounded.ShoppingCart, 2)
                ).forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = {
                            Text(label, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                        },
                        colors = colors
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedTab) {
                0 -> TicketsTab(
                    tickets, isLoggedIn, syncProgress, statusMessage, errorMessage,
                    onSyncClick, onLogoutClick, onTicketClick = { selectedTicket = it }
                )
                1 -> StatsTab(summary)
                else -> ProductsTab(summary.productStats)
            }
        }
    }

    selectedTicket?.let { TicketDialog(it, onDismiss = { selectedTicket = null }) }
}

@Composable
private fun Header(dateRangeText: String, summary: SpendSummary) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(LidlBlueDark, LidlBlue)))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = LidlYellow)) { append("Lidl") }
                        withStyle(SpanStyle(color = Color.White)) { append("Stats") }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(dateRangeText, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD3E2F5))
            }
            Surface(shape = RoundedCornerShape(16.dp), color = LidlYellow) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        formatEuro(summary.totalSpent),
                        style = MaterialTheme.typography.titleSmall,
                        color = LidlBlueDark,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "${summary.ticketCount} tickets",
                        style = MaterialTheme.typography.labelSmall,
                        color = LidlBlue
                    )
                }
            }
        }
        // Franja roja inferior, como el borde del logo
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(LidlRed)
        )
    }
}

// ---------------------------------------------------------------- Tickets

@Composable
private fun TicketsTab(
    tickets: List<StoredTicket>,
    isLoggedIn: Boolean,
    syncProgress: SyncProgress?,
    statusMessage: String?,
    errorMessage: String?,
    onSyncClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onTicketClick: (StoredTicket) -> Unit
) {
    val isSyncing = syncProgress != null
    val grouped = remember(tickets) { tickets.groupBy { it.dateTimeIso.take(7) } }
    val monthKeys = grouped.keys.toList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            WhiteCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(if (isLoggedIn) Color(0xFF16A34A) else Color(0xFFB0BEC5))
                            )
                            Text(
                                if (isLoggedIn) "Sesión de Lidl iniciada" else "Sin sesión en Lidl",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isLoggedIn) LidlBlue else TextSecondary
                            )
                        }
                        if (isLoggedIn) {
                            Text(
                                "Cerrar sesión",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !isSyncing, onClick = onLogoutClick)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onSyncClick,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = LidlBlue),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            if (isLoggedIn) Icons.Rounded.Sync else Icons.AutoMirrored.Rounded.Login,
                            contentDescription = null,
                            tint = LidlYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isLoggedIn) "Descargar tickets nuevos" else "Iniciar sesión en Lidl",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (syncProgress != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val barModifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            if (syncProgress.total > 0) {
                                LinearProgressIndicator(
                                    progress = { syncProgress.current.toFloat() / syncProgress.total },
                                    color = LidlBlue, trackColor = LidlBlueSoft, modifier = barModifier
                                )
                            } else {
                                LinearProgressIndicator(color = LidlBlue, trackColor = LidlBlueSoft, modifier = barModifier)
                            }
                            Text(syncProgress.message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    } else if (!statusMessage.isNullOrBlank()) {
                        Surface(shape = RoundedCornerShape(10.dp), color = LidlBlueSoft) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = LidlBlue, modifier = Modifier.size(16.dp))
                                Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = LidlBlueDark)
                            }
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Surface(shape = RoundedCornerShape(10.dp), color = LidlRedSoft) {
                            Text(
                                errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA30710),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (tickets.isEmpty()) {
            item {
                WhiteCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ReceiptLong, null,
                            tint = LidlBlue.copy(alpha = 0.5f), modifier = Modifier.size(44.dp)
                        )
                        Text("Sin tickets todavía", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            "Inicia sesión con tu cuenta de Lidl para descargar los tickets de tus compras en tienda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(monthKeys, key = { it }) { monthKey ->
                val monthTickets = grouped[monthKey].orEmpty()
                var expanded by remember(monthKey) { mutableStateOf(monthKey == monthKeys.firstOrNull()) }
                MonthGroup(
                    title = formatMonthYearTitle(monthKey),
                    total = monthTickets.sumOf { it.totalAmount },
                    tickets = monthTickets,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    onTicketClick = onTicketClick
                )
            }
        }
    }
}

@Composable
private fun MonthGroup(
    title: String,
    total: Double,
    tickets: List<StoredTicket>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTicketClick: (StoredTicket) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BackgroundScreen),
        border = BorderStroke(1.dp, LidlBlue.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LidlBlue)
                    .clickable(onClick = onToggle)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "${tickets.size} ${if (tickets.size == 1) "compra" else "compras"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD3E2F5)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = LidlYellow) {
                        Text(
                            formatEuro(total),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = LidlBlueDark,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Icon(
                        if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (expanded) "Plegar" else "Desplegar",
                        tint = Color.White
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tickets.forEach { TicketCard(it, onClick = { onTicketClick(it) }) }
                }
            }
        }
    }
}

@Composable
private fun TicketCard(ticket: StoredTicket, onClick: () -> Unit) {
    val (day, month) = remember(ticket.dateTimeIso) { dayMonthBadge(ticket.dateTimeIso) }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(LidlYellowSoft),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                        color = LidlBlueDark, lineHeight = 18.sp)
                    Text(month, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = LidlRed, lineHeight = 12.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    ticket.storeName.ifBlank { "Lidl" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${ticket.timeDisplay} · ${ticket.items.size} prod. · IVA ${formatEuro(ticket.totalVatAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = LidlBlueSoft) {
                Text(
                    formatEuro(ticket.totalAmount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = LidlBlueDark,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** Muestra el ticket tal como lo imprime Lidl, en un "papel" monoespaciado. */
@Composable
private fun TicketDialog(ticket: StoredTicket, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 720.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().background(LidlBlue).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${ticket.dateDisplay} · ${ticket.timeDisplay}", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = Color.White)
                        Text(ticket.storeName, style = MaterialTheme.typography.bodySmall, color = LidlYellow)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .background(Color(0xFFFAFAF7))
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = ticket.receiptText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        color = Color(0xFF222222),
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
                HorizontalDivider(color = CardBorder)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TOTAL", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(formatEuro(ticket.totalAmount), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold, color = LidlBlue)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Estadísticas

@Composable
private fun StatsTab(summary: SpendSummary) {
    val months = summary.monthlyStats
    // offset = nº de meses desplazados hacia atrás desde el más reciente
    var offset by remember(months.size) { mutableIntStateOf(0) }
    val maxOffset = (months.size - CHART_WINDOW).coerceAtLeast(0)
    val end = (months.size - offset).coerceIn(0, months.size)
    val window = months.subList((end - CHART_WINDOW).coerceAtLeast(0), end)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Gasto total", formatEuro(summary.totalSpent), Icons.Rounded.Euro, LidlBlue, LidlBlueSoft, Modifier.weight(1f))
                KpiCard("Ticket medio", formatEuro(summary.averageTicket), Icons.AutoMirrored.Rounded.TrendingUp, LidlRed, LidlRedSoft, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("IVA soportado", formatEuro(summary.totalVat), Icons.Rounded.Percent, LidlBlueDark, LidlYellow, Modifier.weight(1f))
                KpiCard("Tickets", summary.ticketCount.toString(), Icons.Rounded.ShoppingBag, LidlBlue, LidlYellowSoft, Modifier.weight(1f))
            }
        }
        item {
            WhiteCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Gasto por mes", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (window.isNotEmpty()) {
                                Text(
                                    if (window.size == 1) window.first().periodLabel
                                    else "${window.first().periodLabel} – ${window.last().periodLabel}",
                                    style = MaterialTheme.typography.bodySmall, color = TextSecondary
                                )
                            }
                        }
                        Row {
                            ArrowButton(Icons.Rounded.ChevronLeft, "Meses anteriores", enabled = offset < maxOffset) {
                                offset = (offset + 1).coerceAtMost(maxOffset)
                            }
                            Spacer(Modifier.width(6.dp))
                            ArrowButton(Icons.Rounded.ChevronRight, "Meses siguientes", enabled = offset > 0) {
                                offset = (offset - 1).coerceAtLeast(0)
                            }
                        }
                    }
                    if (window.isEmpty()) {
                        Text("Sin datos suficientes todavía.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    } else {
                        var drag by remember { mutableFloatStateOf(0f) }
                        MonthBarChart(
                            stats = window,
                            highlightKey = months.lastOrNull()?.periodKey,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(maxOffset) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { drag = 0f },
                                        onHorizontalDrag = { _, delta ->
                                            drag += delta
                                            val step = 60.dp.toPx()
                                            if (drag > step) { // arrastrar a la derecha → meses anteriores
                                                offset = (offset + 1).coerceAtMost(maxOffset); drag = 0f
                                            } else if (drag < -step) {
                                                offset = (offset - 1).coerceAtLeast(0); drag = 0f
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }
            }
        }
        items(months.reversed().filter { it.ticketCount > 0 }, key = { it.periodKey }) { stat ->
            WhiteCard(radius = 12) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stat.periodLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            "${stat.ticketCount} ${if (stat.ticketCount == 1) "compra" else "compras"} · IVA ${formatEuro(stat.totalVatAmount)}",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary
                        )
                    }
                    Text(formatEuro(stat.totalAmount), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold, color = LidlBlue)
                }
            }
        }
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) LidlBlue else BackgroundScreen)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) LidlYellow else Color(0xFFB8C2CF))
    }
}

@Composable
private fun MonthBarChart(stats: List<PeriodSpendStat>, highlightKey: String?, modifier: Modifier = Modifier) {
    val maxAmount = (stats.maxOfOrNull { it.totalAmount } ?: 1.0).coerceAtLeast(1.0)
    Row(
        modifier = modifier.height(200.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        stats.forEach { stat ->
            val ratio = (stat.totalAmount / maxAmount).toFloat().coerceIn(0f, 1f)
            val isLatest = stat.periodKey == highlightKey
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f).height(195.dp)
            ) {
                Text(
                    String.format(Locale.US, "%.0f€", stat.totalAmount),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Medium,
                    color = if (isLatest) LidlRed else TextSecondary,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    if (stat.totalAmount > 0) {
                        Box(
                            Modifier
                                .width(32.dp)
                                .height((135 * ratio).coerceAtLeast(4f).dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    if (isLatest) Brush.verticalGradient(listOf(LidlRed, Color(0xFFB0070F)))
                                    else Brush.verticalGradient(listOf(Color(0xFF3D7FD0), LidlBlue))
                                )
                        )
                    }
                }
                Box(Modifier.width(32.dp).height(3.dp).background(LidlYellow))
                Spacer(Modifier.height(6.dp))
                Text(
                    stat.periodLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Normal,
                    color = if (isLatest) TextPrimary else TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun KpiCard(title: String, value: String, icon: ImageVector, accent: Color, bg: Color, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorder),
        modifier = modifier
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- Productos

@Composable
private fun ProductsTab(products: List<ProductSpendStat>) {
    var sortTab by remember { mutableIntStateOf(0) } // 0 = Cantidad, 1 = Gasto
    var query by remember { mutableStateOf("") }

    val filtered = remember(products, query, sortTab) {
        val q = query.trim()
        val base = if (q.isEmpty()) products else products.filter { it.displayName.contains(q, ignoreCase = true) }
        if (sortTab == 0) {
            base.sortedWith(
                compareByDescending<ProductSpendStat> { it.quantityRank }
                    .thenByDescending { it.purchaseCount }
                    .thenByDescending { it.totalAmount }
            )
        } else base.sortedByDescending { it.totalAmount }
    }
    val maxBar = remember(filtered, sortTab) {
        if (filtered.isEmpty()) 1.0
        else if (sortTab == 0) filtered.maxOf { it.quantityRank }.coerceAtLeast(1.0)
        else filtered.maxOf { it.totalAmount }.coerceAtLeast(0.01)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            WhiteCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Productos (${filtered.size})", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = TextPrimary)
                        SegmentedSwitch(listOf("Cantidad", "Gasto"), sortTab) { sortTab = it }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buscar producto (ej. pan, leche...)") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextSecondary) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Limpiar", tint = TextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LidlBlue,
                            unfocusedBorderColor = CardBorder,
                            focusedContainerColor = BackgroundScreen,
                            unfocusedContainerColor = BackgroundScreen,
                            cursorColor = LidlBlue
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                WhiteCard(radius = 14) {
                    Text(
                        if (products.isEmpty()) "Todavía no hay productos. Descarga tus tickets primero."
                        else "No se han encontrado productos para \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            itemsIndexed(filtered, key = { _, p -> p.productKey }) { index, product ->
                val value = if (sortTab == 0) product.quantityRank else product.totalAmount
                val ratio = (value / maxBar).toFloat().coerceIn(0.03f, 1f)
                val qtyText = if (product.unit == "kg") {
                    String.format(Locale("es", "ES"), "%.2f kg", product.totalQuantity)
                } else {
                    val q = product.totalQuantity.toInt()
                    if (abs(product.totalQuantity - q) < 0.01) "$q ${if (q == 1) "ud" else "uds"}"
                    else String.format(Locale("es", "ES"), "%.2f uds", product.totalQuantity)
                }
                val (rankBg, rankFg) = when (index) {
                    0 -> LidlYellow to LidlBlueDark
                    1 -> Color(0xFFE8ECF1) to Color(0xFF475569)
                    2 -> Color(0xFFFFE4CC) to Color(0xFFB45309)
                    else -> LidlBlueSoft to LidlBlue
                }

                WhiteCard(radius = 14) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(rankBg), contentAlignment = Alignment.Center) {
                                    Text("${index + 1}", style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold, color = rankFg)
                                }
                                Column {
                                    Text(product.displayName, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold, color = TextPrimary,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "$qtyText · ${product.purchaseCount} ${if (product.purchaseCount == 1) "compra" else "compras"}",
                                        style = MaterialTheme.typography.bodySmall, color = TextSecondary
                                    )
                                }
                            }
                            Text(formatEuro(product.totalAmount), style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold, color = LidlBlue)
                        }
                        LinearProgressIndicator(
                            progress = { ratio },
                            color = if (sortTab == 0) LidlBlue else LidlRed,
                            trackColor = LidlBlueSoft,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Comunes

@Composable
private fun WhiteCard(radius: Int = 16, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(radius.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
private fun SegmentedSwitch(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(BackgroundScreen).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) LidlBlue else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) LidlYellow else TextSecondary
                )
            }
        }
    }
}

private val MONTH_SHORT_ES = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC")
private val MONTH_FULL_ES = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

private fun formatMonthYearTitle(monthKey: String): String {
    val p = monthKey.split("-")
    val m = p.getOrNull(1)?.toIntOrNull() ?: return monthKey
    return "${MONTH_FULL_ES[m - 1]} ${p[0]}"
}

private fun dayMonthBadge(dateTimeIso: String): Pair<String, String> {
    val p = dateTimeIso.substringBefore(" ").split("-")
    val m = p.getOrNull(1)?.toIntOrNull() ?: return "--" to "---"
    return p[2] to MONTH_SHORT_ES[m - 1]
}

private fun formatEuro(amount: Double): String = String.format(Locale("es", "ES"), "%.2f €", amount)
