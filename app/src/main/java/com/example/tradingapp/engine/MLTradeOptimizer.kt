package com.example.tradingapp.engine

import com.example.tradingapp.model.ClosedTrade
import com.example.tradingapp.model.MLModelWeights
import com.example.tradingapp.model.MLOptimizationMetrics
import com.example.tradingapp.model.MacroNewsSentiment
import com.example.tradingapp.model.MarketBar
import com.example.tradingapp.model.MultiIndexData
import kotlin.math.max
import kotlin.math.min

class MLTradeOptimizer {

    val weights = MLModelWeights()
    private val closedHistory = mutableListOf<ClosedTrade>()

    var totalWeightUpdates: Int = 0
        private set

    fun reset() {
        weights.trendWeight = 0.25
        weights.rsiWeight = 0.20
        weights.macdWeight = 0.15
        weights.bollingerWeight = 0.15
        weights.newsWeight = 0.15
        weights.multiIndexWeight = 0.20
        weights.confidenceBias = 0.50
        closedHistory.clear()
        totalWeightUpdates = 0
    }

    /**
     * Calculates the AI Machine Learning score for a market bar using trained weights across technicals, news, and multi-index trends.
     */
    fun predictConfidenceScore(
        bar: MarketBar,
        newsSentiment: MacroNewsSentiment,
        multiIndex: MultiIndexData
    ): Double {
        val fTrend = extractTrendFeature(bar)
        val fRsi = extractRsiFeature(bar)
        val fMacd = extractMacdFeature(bar)
        val fBollinger = extractBollingerFeature(bar)
        val fNews = newsSentiment.sentimentScore.coerceIn(-1.0, 1.0)
        val fMultiIndex = (multiIndex.breadthScore * 2.0) - 1.0 // Map 0.0..1.0 to -1.0..+1.0

        val weightedSum = (weights.trendWeight * fTrend) +
                (weights.rsiWeight * fRsi) +
                (weights.macdWeight * fMacd) +
                (weights.bollingerWeight * fBollinger) +
                (weights.newsWeight * fNews) +
                (weights.multiIndexWeight * fMultiIndex)

        // Sigmoid activation around confidence bias
        val rawScore = weights.confidenceBias + (weightedSum * 0.25)
        return rawScore.coerceIn(0.0, 1.0)
    }

    /**
     * Online Reinforcement Learning / Stochastic Gradient Descent update based on trade outcome.
     */
    fun onTradeClosed(trade: ClosedTrade, entryBar: MarketBar, news: MacroNewsSentiment, multiIndex: MultiIndexData) {
        closedHistory.add(trade)
        totalWeightUpdates++

        val reward = if (trade.pnl > 0) {
            min(trade.returnPct / 3.0, 2.0) // Positive reward
        } else {
            max(trade.returnPct / 2.0, -2.0) // Negative penalty
        }

        val lr = weights.learningRate

        val fTrend = extractTrendFeature(entryBar)
        val fRsi = extractRsiFeature(entryBar)
        val fMacd = extractMacdFeature(entryBar)
        val fBollinger = extractBollingerFeature(entryBar)
        val fNews = news.sentimentScore.coerceIn(-1.0, 1.0)
        val fMultiIndex = (multiIndex.breadthScore * 2.0) - 1.0

        // SGD Weight updates
        weights.trendWeight = (weights.trendWeight + (lr * reward * fTrend)).coerceIn(0.05, 0.40)
        weights.rsiWeight = (weights.rsiWeight + (lr * reward * fRsi)).coerceIn(0.05, 0.35)
        weights.macdWeight = (weights.macdWeight + (lr * reward * fMacd)).coerceIn(0.05, 0.30)
        weights.bollingerWeight = (weights.bollingerWeight + (lr * reward * fBollinger)).coerceIn(0.05, 0.30)
        weights.newsWeight = (weights.newsWeight + (lr * reward * fNews)).coerceIn(0.05, 0.30)
        weights.multiIndexWeight = (weights.multiIndexWeight + (lr * reward * fMultiIndex)).coerceIn(0.05, 0.35)
    }

    fun getOptimizationMetrics(): MLOptimizationMetrics {
        val total = closedHistory.size
        if (total == 0) return MLOptimizationMetrics()

        val winningTrades = closedHistory.count { it.pnl > 0 }
        val winRate = (winningTrades.toDouble() / total) * 100.0
        val avgExpectancy = closedHistory.map { it.pnl }.average()

        val recent = closedHistory.takeLast(20)
        val recentWinRate = if (recent.isNotEmpty()) (recent.count { it.pnl > 0 }.toDouble() / recent.size) * 100.0 else winRate

        // Adaptive Buy Threshold adjustment based on recent performance
        val adaptedThreshold = when {
            recentWinRate >= 65.0 -> 0.48 // Relax threshold to capture strong momentum
            recentWinRate in 50.0..64.9 -> 0.50
            recentWinRate in 40.0..49.9 -> 0.54
            else -> 0.58 // Tighten threshold during choppy/low win rate periods
        }

        return MLOptimizationMetrics(
            totalFeedbackCycles = total,
            modelAccuracyPct = winRate,
            totalWeightUpdates = totalWeightUpdates,
            avgTradeExpectancy = avgExpectancy,
            adaptedBuyThreshold = adaptedThreshold,
            recentWinRatePct = recentWinRate
        )
    }

    private fun extractTrendFeature(bar: MarketBar): Double {
        return when {
            bar.close > bar.ema20 && bar.ema20 > bar.ema50 && bar.ema50 > bar.ema200 -> 1.0
            bar.close > bar.ema50 -> 0.5
            bar.close < bar.ema20 && bar.ema20 < bar.ema50 -> -1.0
            else -> -0.3
        }
    }

    private fun extractRsiFeature(bar: MarketBar): Double {
        return when {
            bar.rsi < 35.0 -> 1.0 // Oversold -> High buy potential
            bar.rsi in 35.0..55.0 -> 0.6 // Pullback zone
            bar.rsi > 75.0 -> -1.0 // Overbought
            else -> 0.0
        }
    }

    private fun extractMacdFeature(bar: MarketBar): Double {
        return when {
            bar.macdHist > 0.0 && bar.macd > bar.macdSignal -> 1.0
            bar.macdHist < 0.0 && bar.macd < bar.macdSignal -> -1.0
            else -> 0.0
        }
    }

    private fun extractBollingerFeature(bar: MarketBar): Double {
        return when {
            bar.close <= bar.bollingerLower -> 1.0
            bar.close >= bar.bollingerUpper -> -1.0
            else -> 0.0
        }
    }
}
