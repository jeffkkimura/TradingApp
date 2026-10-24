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
    var apiKey: String = "PKZBRZTKZ5A6IA4FPZBIKMXHOS"
    var apiSecret: String = "3ZkzAZWQzTMgiviBDLtT6vmrwCNbt1LxmdF4TeorQ3Ae"

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
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val orderId = extractJsonValue(responseText, "id")
                val status = extractJsonValue(responseText, "status")

                AlpacaOrderResult(
                    success = true,
                    orderId = orderId.ifEmpty { "ALPACA_${System.currentTimeMillis()}" },
                    message = "Order $status by Alpaca (ID: ${orderId.take(8)}...). ${if (status == "accepted") "Market is currently closed/after-hours; order queued for market open." else "Order filled on exchange."}"
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

    private fun extractJsonValue(json: String, key: String): String {
        val pattern = "\"$key\":\"([^\"]+)\"".toRegex()
        return pattern.find(json)?.groupValues?.get(1) ?: ""
    }
}

data class AlpacaOrderResult(
    val success: Boolean,
    val orderId: String,
    val message: String
)
