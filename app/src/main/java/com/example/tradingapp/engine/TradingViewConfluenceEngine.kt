package com.example.tradingapp.engine

import com.example.tradingapp.model.MarketBar
import kotlin.math.max

enum class TradingViewSignal(val label: String) {
    BULLISH_CONFLUENCE("SuperTrend Bullish Confluence"),
    BEARISH_CONFLUENCE("SuperTrend Bearish Reversal"),
    NEUTRAL("Consolidation Range")
}

data class TradingViewAnalysis(
    val signal: TradingViewSignal,
    val superTrendValue: Double,
    val isSuperTrendBullish: Boolean,
    val rsiEmaCrossBullish: Boolean,
    val confluenceScore: Double // 0.0 to 1.0
)

object TradingViewConfluenceEngine {

    /**
     * Evaluates TradingView PineScript SuperTrend & RSI-EMA Confluence Model over current price bars.
     */
    fun analyzeBar(bar: MarketBar, prevBar: MarketBar?): TradingViewAnalysis {
        val atr = max(0.5, bar.atr)
        val hl2 = (bar.high + bar.low) / 2.0
        val superTrendUpper = hl2 + (2.5 * atr)
        val superTrendLower = hl2 - (2.5 * atr)

        val isSuperTrendBull = bar.close > superTrendLower && (prevBar == null || prevBar.close > superTrendLower)
        val superTrendVal = if (isSuperTrendBull) superTrendLower else superTrendUpper

        val rsiEmaCrossBull = bar.rsi > 50.0 && (prevBar == null || prevBar.rsi <= 50.0 || bar.rsi > prevBar.rsi)
        val macdBull = bar.macdHist > 0.0 && bar.macd > bar.macdSignal

        var score = 0.50
        if (isSuperTrendBull) score += 0.25 else score -= 0.20
        if (rsiEmaCrossBull) score += 0.15
        if (macdBull) score += 0.10

        val finalScore = score.coerceIn(0.0, 1.0)

        val signal = when {
            finalScore >= 0.60 && isSuperTrendBull -> TradingViewSignal.BULLISH_CONFLUENCE
            finalScore <= 0.40 -> TradingViewSignal.BEARISH_CONFLUENCE
            else -> TradingViewSignal.NEUTRAL
        }

        return TradingViewAnalysis(
            signal = signal,
            superTrendValue = superTrendVal,
            isSuperTrendBullish = isSuperTrendBull,
            rsiEmaCrossBullish = rsiEmaCrossBull,
            confluenceScore = finalScore
        )
    }
}
