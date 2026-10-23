package com.example.tradingapp.data

import com.example.tradingapp.model.OrderType
import com.example.tradingapp.model.TradeAction
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AlpacaBrokerageService {

    private const val PAPER_BASE_URL = "https://paper-api.alpaca.markets/v2"
    private const val LIVE_BASE_URL = "https://api.alpaca.markets/v2"

    var isPaperTrading: Boolean = true
    var apiKey: String = "PK_SANDBOX_DEMO_KEY"
    var apiSecret: String = "SECRET_SANDBOX_DEMO_KEY"

    private val activeBaseUrl: String
        get() = if (isPaperTrading) PAPER_BASE_URL else LIVE_BASE_URL

    /**
     * Submits an actual live or paper bracket order directly to Alpaca Brokerage REST API.
     */
    suspend fun submitAlpacaOrder(
        symbol: String,
        qty: Double,
        action: TradeAction,
        orderType: OrderType,
        entryPrice: Double,
        stopLossPrice: Double,
        takeProfitPrice: Double
    ): AlpacaOrderResult = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$activeBaseUrl/orders")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("APCA-API-KEY-ID", apiKey)
            connection.setRequestProperty("APCA-API-SECRET-KEY", apiSecret)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val side = if (action == TradeAction.BUY) "buy" else "sell"
            val orderClass = if (takeProfitPrice > 0 && stopLossPrice > 0) "bracket" else "simple"

            val jsonBody = buildString {
                append("{")
                append("\"symbol\":\"$symbol\",")
                append("\"qty\":${String.format(Locale.US, "%.2f", qty)},")
                append("\"side\":\"$side\",")
                append("\"type\":\"market\",")
                append("\"time_in_force\":\"day\",")
                if (orderClass == "bracket") {
                    append("\"order_class\":\"bracket\",")
                    append("\"take_profit\":{\"limit_price\":${String.format(Locale.US, "%.2f", takeProfitPrice)}},")
                    append("\"stop_loss\":{\"stop_price\":${String.format(Locale.US, "%.2f", stopLossPrice)}}")
                } else {
                    append("\"order_class\":\"simple\"")
                }
                append("}")
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                AlpacaOrderResult(
                    success = true,
                    orderId = "ALPACA_${System.currentTimeMillis()}",
                    message = "Successfully submitted $action order for $qty shares of $symbol to Alpaca (${if (isPaperTrading) "Paper" else "Live"} Account)."
                )
            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                AlpacaOrderResult(
                    success = false,
                    orderId = "",
                    message = "Alpaca API Error ($responseCode): $errorMsg"
                )
            }
        } catch (e: Exception) {
            AlpacaOrderResult(
                success = false,
                orderId = "",
                message = "Alpaca Connection Error: ${e.message ?: "Failed to reach Alpaca servers."}"
            )
        }
    }
}

data class AlpacaOrderResult(
    val success: Boolean,
    val orderId: String,
    val message: String
)
