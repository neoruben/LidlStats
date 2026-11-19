# LidlStats — notas para Claude

App Android (Kotlin + Compose) que descarga los tickets de tienda de lidl.es y hace estadísticas.
Basada en MercaStats (github neoruben/mercadona-stats). Repo: github neoruben/LidlStats (público; está en la tabla del perfil neoruben/neoruben).
Paquete `io.github.neoruben.lidlstats`.

## API de Lidl (descubierta con el navegador)
- Login: WebView en `https://www.lidl.es/mre/purchase-history?...` → redirige a accounts.lidl.com.
  La sesión son cookies (`authToken`); `LidlApi` reenvía las del `CookieManager`. Sin cookies → 401.
- Listado: `GET /mre/api/v1/tickets?country=ES&page=N` → `{page,size,totalCount,items[id,date,totalAmount,store,...]}`.
  La fecha viene con `+00:00` pero es hora local.
- Detalle: `GET /mre/api/v1/tickets/{id}?country=ES&languageCode=es-ES` → `ticket.htmlPrintedReceipt`
  (su `ticket.totalAmount` vale 0: usar el del listado).
- HTML del ticket: `span[id^=purchase_list_line]` con clase `article` (data-art-description, data-art-quantity
  — con coma si es al peso —, data-unit-price) o `discount`. Hay descuentos **sin clase** («Desc.» de cupones):
  cualquier línea que acabe en importe negativo se resta al artículo anterior.
  IVA: `span[data-tax-percentage]`; **`data-tax-base-amount` es el PVP con IVA**, por eso se lee el texto
  (cuota, base, PVP).

## Trampas
- El login de Lidl usa `height: 100dvh`: el WebView debe tener `layoutParams` MATCH_PARENT o sale en blanco.
- Si se cambia el parser, subir la versión de `TicketDatabaseHelper`: `onUpgrade` borra todo y se resincroniza.

## Compilar y probar
- Compilar: igual que OneRate (TMP/TEMP en `C:\gtmp`, JAVA_HOME del jbr de Android Studio, fuera del sandbox),
  `./gradlew.bat assembleDebug` / `assembleRelease` (firma con `Documents/claves/keystore.properties`).
- Los tests de Gradle **no arrancan** por la «é» de `C:\Users\Rubén\.gradle`. Truco: copiar el proyecto a
  `C:\gtmp\LidlStats`, `gradlew compileDebugUnitTestKotlin` y lanzar JUnit a mano:
  `java -cp "<build>\intermediates\built_in_kotlinc\debug\compileDebugKotlin\classes;<...>\debugUnitTest\compileDebugUnitTestKotlin\classes;C:\gtmp\cp\*" org.junit.runner.JUnitCore io.github.neoruben.lidlstats.parser.LidlReceiptParserTest`
  (`C:\gtmp\cp` = jsoup, junit, hamcrest-core, kotlin-stdlib de `~/.gradle/caches/modules-2`).
- El emulador `Medium_Phone_API_37.0` se cerró al arrancar (exit 139, GPU) el 19/11/2026; se probó en el Pixel.
- Comprobar datos: `adb exec-out run-as io.github.neoruben.lidlstats cat databases/lidl_tickets.db > l.db`
  (nunca subir esa base de datos ni tickets: son datos personales).
- WebView depurable en debug: `adb forward tcp:9333 localabstract:webview_devtools_remote_<pid>`.
