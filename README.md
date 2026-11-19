# LidlStats

Android app (Jetpack Compose) that downloads your **Lidl Spain** in-store receipts from your lidl.es account
and shows spending statistics. Everything is stored locally on the phone (SQLite); nothing is sent anywhere
except to lidl.es itself.

App para Android que descarga los tickets de compra en tienda de tu cuenta de **Lidl España** y muestra
estadísticas de gasto. Todo se guarda en el móvil.

![LidlStats screens](docs/screenshots/lidlstats_screens_horizontal.png)

## Features / Funciones
- **Tickets**: sign in to lidl.es in an embedded browser, download only the receipts you don't have yet,
  grouped by collapsible months; tap one to see the printed receipt.
  / Login en lidl.es, descarga solo los tickets nuevos, agrupados por meses desplegables; al pulsar se ve el ticket.
- **Estadísticas**: total spent, average ticket, number of tickets, VAT, monthly bar chart (5 months, swipe or
  arrows to go back in time).
- **Productos**: search, sort by quantity bought or by money spent; units/kg bought and number of purchases.

## How it works
After logging in, the session cookies of the WebView are reused to call the same JSON API the lidl.es
"Mis tickets" page uses:
- `GET https://www.lidl.es/mre/api/v1/tickets?country=ES&page=N` → list (id, date, total, store)
- `GET https://www.lidl.es/mre/api/v1/tickets/{id}?country=ES&languageCode=es-ES` → `ticket.htmlPrintedReceipt`

The printed receipt is HTML whose `<span>` elements carry the data in attributes (`data-art-description`,
`data-art-quantity`, `data-unit-price`, `data-tax-percentage`, …). See
[`LidlReceiptParser`](app/src/main/java/io/github/neoruben/lidlstats/parser/LidlReceiptParser.kt).

## Build
```
./gradlew assembleDebug
```
Release signing reads `keystore.properties` (project root or `~/Documents/claves/`), never committed.

Not affiliated with Lidl.
