package com.example.tradingapp.model

enum class MarketRegime(val label: String, val description: String) {
    BULL_TREND("Bullish Trend", "Sustained upward momentum above 50/200 EMA"),
    BEAR_TREND("Bearish Trend", "Sustained downward momentum below 50/200 EMA"),
    RANGE_BOUND("Range Bound", "Sideways consolidation with oscillating momentum"),
    HIGH_VOLATILITY("High Volatility", "Elevated ATR & market uncertainty; risk off")
}

enum class TradeAction {
    BUY,
    SELL,
    HOLD
}

enum class FedPolicyStance(val label: String) {
    DOVISH_CUTTING("Dovish (Rate Cuts)"),
    NEUTRAL("Neutral / Data Dependent"),
    HAWKISH_HIKING("Hawkish (Rate Hikes)")
}

enum class InflationTrend(val label: String) {
    COOLING("Cooling Inflation"),
    STABLE("Stable Inflation"),
    REACCELERATING("Re-accelerating Inflation")
}

enum class MacroRiskEvent(val label: String) {
    NORMAL("Normal Operations"),
    EARNINGS_SEASON("Mega-Cap Earnings Season"),
    FED_FOMC_MEETING("FOMC Rate Decision"),
    GEOPOLITICAL_SPIKE("Geopolitical Volatility Spike")
}

data class MacroNewsSentiment(
    val headline: String = "Fed pauses rate trajectory as cooling CPI inflation aligns with steady labor market.",
    val sentimentScore: Double = 0.20, // -1.0 (Crisis) to +1.0 (Bullish Expansion)
    val fedPolicy: FedPolicyStance = FedPolicyStance.NEUTRAL,
    val inflation: InflationTrend = InflationTrend.COOLING,
    val riskEvent: MacroRiskEvent = MacroRiskEvent.NORMAL,
    val source: String = "Global Macro & Market Catalyst Scanner"
)

data class MarketBar(
    val date: String,
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val rsi: Double = 50.0,
    val ema20: Double = close,
    val ema50: Double = close,
    val ema200: Double = close,
    val atr: Double = (high - low),
    val bollingerUpper: Double = close * 1.02,
    val bollingerLower: Double = close * 0.98,
    val macd: Double = 0.0,
    val macdSignal: Double = 0.0,
    val macdHist: Double = 0.0
)

data class TradeSignal(
    val action: TradeAction,
    val symbol: String,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val positionSize: Double,
    val confidenceScore: Double,
    val reasoning: String,
    val date: String,
    val timestamp: Long = System.currentTimeMillis(),
    val regime: MarketRegime = MarketRegime.BULL_TREND,
    val macroSentiment: MacroNewsSentiment = MacroNewsSentiment()
)

data class TradePosition(
    val id: String,
    val symbol: String,
    val type: TradeAction,
    val entryPrice: Double,
    val quantity: Double,
    var stopLoss: Double,
    val takeProfit: Double,
    val entryDate: String,
    var currentPrice: Double = entryPrice
) {
    val currentMarketValue: Double
        get() = quantity * currentPrice

    val unrealizedPnl: Double
        get() = if (type == TradeAction.BUY) {
            (currentPrice - entryPrice) * quantity
        } else {
            (entryPrice - currentPrice) * quantity
        }

    val unrealizedPnlPct: Double
        get() = if (entryPrice > 0) (unrealizedPnl / (entryPrice * quantity)) * 100.0 else 0.0
}

data class ClosedTrade(
    val id: String,
    val symbol: String,
    val type: TradeAction,
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Double,
    val pnl: Double,
    val returnPct: Double,
    val entryDate: String,
    val exitDate: String,
    val exitReason: String
)

data class TrialResult(
    val trialId: Int,
    val startDate: String,
    val endDate: String,
    val startingCapital: Double,
    val finalCapital: Double,
    val totalReturnPct: Double,
    val winRate: Double,
    val totalTrades: Int,
    val avgTradesPerDay: Double,
    val maxDrawdownPct: Double,
    val sharpeRatio: Double,
    val profitFactor: Double,
    val passed: Boolean,
    val failureReason: String = ""
)

data class BacktestSummary(
    val totalTrials: Int = 100,
    val passedTrials: Int = 0,
    val passRatePct: Double = 0.0,
    val avgReturnPct: Double = 0.0,
    val avgWinRatePct: Double = 0.0,
    val avgMaxDrawdownPct: Double = 0.0,
    val avgSharpeRatio: Double = 0.0,
    val avgTradesPerDay: Double = 0.0,
    val bestTrialReturnPct: Double = 0.0,
    val worstTrialReturnPct: Double = 0.0,
    val overallPassed: Boolean = false,
    val trials: List<TrialResult> = emptyList()
)

data class EpochPerformance(
    val epochName: String,
    val timePeriod: String,
    val marketReturnPct: Double,
    val botReturnPct: Double,
    val totalTrades: Int,
    val winRatePct: Double,
    val maxDrawdownPct: Double,
    val keyRegime: MarketRegime,
    val description: String
)
