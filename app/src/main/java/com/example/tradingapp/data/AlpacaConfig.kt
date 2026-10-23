package com.example.tradingapp.data

data class AlpacaConfig(
    val apiKey: String = "PK_PAPER_DEMO_KEY_2025",
    val apiSecret: String = "SK_PAPER_DEMO_SECRET_2025",
    val isPaperTrading: Boolean = true,
    val isConnected: Boolean = true,
    val accountEquity: Double = 100_000.0,
    val buyingPower: Double = 400_000.0,
    val paperEndpoint: String = "https://paper-api.alpaca.markets",
    val liveEndpoint: String = "https://api.alpaca.markets",
    val webSocketEndpoint: String = "wss://stream.data.alpaca.markets/v2/iex"
)
