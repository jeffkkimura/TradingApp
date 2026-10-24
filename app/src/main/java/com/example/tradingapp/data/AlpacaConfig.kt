package com.example.tradingapp.data

data class AlpacaConfig(
    val apiKey: String = "PKZBRZTKZ5A6IA4FPZBIKMXHOS",
    val apiSecret: String = "3ZkzAZWQzTMgiviBDLtT6vmrwCNbt1LxmdF4TeorQ3Ae",
    val isPaperTrading: Boolean = true,
    val isConnected: Boolean = true,
    val accountEquity: Double = 100_000.0,
    val buyingPower: Double = 400_000.0,
    val paperEndpoint: String = "https://paper-api.alpaca.markets",
    val liveEndpoint: String = "https://api.alpaca.markets",
    val webSocketEndpoint: String = "wss://stream.data.alpaca.markets/v2/iex"
)
