package com.example.tradingapp.engine

import com.example.tradingapp.model.MarketBar
import com.example.tradingapp.model.MarketRegime
import com.example.tradingapp.model.TradeAction
import com.example.tradingapp.model.TradeSignal
import java.util.Locale
import kotlin.math.max

class AITradingStrategy(
    val maxDailyTrades: Int = 2,
    val buyConfidenceThreshold: Double = 0.50,
    val riskPerTradePct: Double = 0.015, // 1.5% portfolio risk per trade
    val atrStopLossMultiplier: Double = 2.5,
    val atrTakeProfitMultiplier: Double = 4.5
) {

    private var lastTradeDate: String = ""
    private var tradesTodayCount: Int = 0

    fun resetDailyLimit() {
        lastTradeDate = ""
        tradesTodayCount = 0
    }

    /**
     * Evaluates current market bar and portfolio state to produce a trade signal (BUY, SELL, HOLD).
     */
    fun evaluateSignal(
        currentBar: MarketBar,
        accountBalance: Double,
        activePositionsCount: Int,
        hasOpenPosition: Boolean = false
    ): TradeSignal {
        if (currentBar.date != lastTradeDate) {
            lastTradeDate = currentBar.date
            tradesTodayCount = 0
        }

        val regime = classifyMarketRegime(currentBar)

        if (tradesTodayCount >= maxDailyTrades) {
            return TradeSignal(
                action = TradeAction.HOLD,
                symbol = "SPY_AI",
                entryPrice = currentBar.close,
                stopLoss = currentBar.close - (currentBar.atr * atrStopLossMultiplier),
                takeProfit = currentBar.close + (currentBar.atr * atrTakeProfitMultiplier),
                positionSize = 0.0,
                confidenceScore = 0.0,
                reasoning = "Daily trade quota reached ($tradesTodayCount/$maxDailyTrades trades for ${currentBar.date}).",
                date = currentBar.date,
                timestamp = currentBar.timestamp,
                regime = regime
            )
        }

        val aiScore = computeMultiFactorScore(currentBar, regime)

        // 200-EMA Hysteresis Band (0.5% threshold)
        val isConfirmedAbove200Ema = currentBar.close >= (currentBar.ema200 * 1.005)
        val isConfirmedBelow200Ema = currentBar.close <= (currentBar.ema200 * 0.995)

        // Exit condition for open position
        val isExitCondition = hasOpenPosition && (
                isConfirmedBelow200Ema ||
                (currentBar.close < currentBar.ema50 && currentBar.rsi < 38.0) ||
                currentBar.rsi > 80.0
        )

        return when {
            // BUY Signal (New Entry)
            aiScore >= buyConfidenceThreshold && isConfirmedAbove200Ema && !hasOpenPosition && activePositionsCount < 2 -> {
                tradesTodayCount++
                val stopLoss = currentBar.close - (currentBar.atr * atrStopLossMultiplier)
                val takeProfit = currentBar.close + (currentBar.atr * atrTakeProfitMultiplier)
                val riskAmount = accountBalance * riskPerTradePct
                val riskPerShare = max(0.5, currentBar.close - stopLoss)
                val shares = (riskAmount / riskPerShare)

                TradeSignal(
                    action = TradeAction.BUY,
                    symbol = "SPY_AI",
                    entryPrice = currentBar.close,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    positionSize = shares,
                    confidenceScore = aiScore,
                    reasoning = "AI Bullish Entry (${String.format(Locale.US, "%.1f", aiScore * 100)}%). Above 200-EMA. RSI: ${String.format(Locale.US, "%.1f", currentBar.rsi)}.",
                    date = currentBar.date,
                    timestamp = currentBar.timestamp,
                    regime = regime
                )
            }

            // SELL Signal (Position Exit)
            isExitCondition -> {
                tradesTodayCount++
                TradeSignal(
                    action = TradeAction.SELL,
                    symbol = "SPY_AI",
                    entryPrice = currentBar.close,
                    stopLoss = 0.0,
                    takeProfit = 0.0,
                    positionSize = 0.0,
                    confidenceScore = 1.0 - aiScore,
                    reasoning = "AI Exit Trigger: ${if (isConfirmedBelow200Ema) "Confirmed Below 200-EMA" else if (currentBar.rsi > 80.0) "Overbought Peak (RSI > 80)" else "Trend Breakdown (< 50 EMA)"}.",
                    date = currentBar.date,
                    timestamp = currentBar.timestamp,
                    regime = regime
                )
            }

            else -> {
                TradeSignal(
                    action = TradeAction.HOLD,
                    symbol = "SPY_AI",
                    entryPrice = currentBar.close,
                    stopLoss = 0.0,
                    takeProfit = 0.0,
                    positionSize = 0.0,
                    confidenceScore = aiScore,
                    reasoning = if (currentBar.close < currentBar.ema200) "Secular Bear Regime (Below 200-EMA) - Preserving Cash." else "Holding position / Scanning setup.",
                    date = currentBar.date,
                    timestamp = currentBar.timestamp,
                    regime = regime
                )
            }
        }
    }

    fun classifyMarketRegime(bar: MarketBar): MarketRegime {
        val volatilityRatio = bar.atr / bar.close
        val isTrendBear = bar.close < bar.ema200 * 0.995
        val isTrendBull = bar.close > bar.ema50 && bar.ema20 > bar.ema50

        return when {
            isTrendBear -> MarketRegime.BEAR_TREND
            volatilityRatio > 0.025 -> MarketRegime.HIGH_VOLATILITY
            isTrendBull -> MarketRegime.BULL_TREND
            else -> MarketRegime.RANGE_BOUND
        }
    }

    private fun computeMultiFactorScore(bar: MarketBar, regime: MarketRegime): Double {
        var score = 0.50

        // 1. Trend Factor
        if (bar.close > bar.ema20 && bar.ema20 > bar.ema50) score += 0.22
        if (bar.ema50 > bar.ema200) score += 0.15

        if (bar.close < bar.ema20 && bar.ema20 < bar.ema50) score -= 0.22
        if (bar.ema50 < bar.ema200) score -= 0.15

        // 2. High-Probability Pullback Entry in Bull Regime
        if (bar.rsi in 35.0..60.0 && bar.close > bar.ema50) score += 0.22
        else if (bar.rsi < 32.0) score += 0.15

        // 3. MACD Momentum
        if (bar.macdHist > 0.0 && bar.macd > bar.macdSignal) score += 0.12

        // 4. Bollinger Bounce
        if (bar.close <= bar.bollingerLower) score += 0.12

        // 5. Regime Adjustment
        if (regime == MarketRegime.BEAR_TREND) score -= 0.25
        if (regime == MarketRegime.HIGH_VOLATILITY) score *= 0.80

        return score.coerceIn(0.0, 1.0)
    }
}
