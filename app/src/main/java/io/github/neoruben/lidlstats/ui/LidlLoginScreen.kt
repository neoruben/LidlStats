package io.github.neoruben.lidlstats.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Pantalla con un WebView para iniciar sesión en lidl.es; las cookies quedan en el CookieManager. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LidlLoginScreen(
    startUrl: String,
    onPageFinished: (String) -> Unit,
    onClose: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LidlBlue)
                .statusBarsPadding()
                .padding(start = 18.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Inicia sesión en Lidl",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Al entrar en \"Mis tickets\" se descargarán automáticamente",
                    style = MaterialTheme.typography.bodySmall,
                    color = LidlYellow
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
            if (loading) {
                LinearProgressIndicator(
                    color = LidlRed,
                    trackColor = LidlYellow,
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
            }
        }
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
            factory = { ctx ->
                WebView(ctx).apply {
                    // Sin MATCH_PARENT el WebView calcula 100vh/100dvh = 0 y el login de Lidl sale en blanco
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.userAgentString = io.github.neoruben.lidlstats.lidl.LidlApi.browserUserAgent(ctx)
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            onPageFinished(url)
                        }
                    }
                    loadUrl(startUrl)
                }
            },
            onRelease = { it.destroy() }
        )
    }
}
