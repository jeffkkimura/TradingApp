package com.example.tradingapp.engine

import com.example.tradingapp.model.DownturnSafetyLevel
import com.example.tradingapp.model.MacroNewsSentiment
import com.example.tradingapp.model.MarketBar
import com.example.tradingapp.model.MarketRegime
import com.example.tradingapp.model.OrderType
import com.example.tradingapp.model.TradeAction
import com.example.tradingapp.model.TradeSignal
import java.util.Locale
import kotlin.math.max

class AITradingStrategy(
    val maxDailyTrades: Int = 2,
    var buyConfidenceThreshold: Double = 0.48, // Configured for active 1-2 trades/day
    val baseRiskPerTradePct: Double = 0.012, // 1.2% risk per position (up to 2 active)
    val atrStopLossMultiplier: Double = 2.5,
    val atrTakeProfitMultiplier: Double = 4.5,
    val preferredOrderType: OrderType = OrderType.HYBRID_SMART_ROUTING,
    val mlOptimizer: MLTradeOptimizer = MLTradeOptimizer()
) {

    private var lastTradeDate: String = ""
    private var tradesTodayCount: Int = 0
    private var consecutiveLossesCount: Int = 0
    private var cooldownDaysRemaining: Int = 0

    fun resetDailyLimit() {
        lastTradeDate = ""
        tradesTodayCount = 0
        consecutiveLossesCount = 0
        cooldownDaysRemaining = 0
        mlOptimizer.reset()
    }

    fun notifyTradeOutcome(isWin: Boolean) {
        if (isWin) {
            consecutiveLossesCount = 0
        } else {
            consecutiveLossesCount++
            if (consecutiveLossesCount >= 4) {
                cooldownDaysRemaining = 2
            }
        }
    }

    fun evaluateDownturnSafety(
        bar: MarketBar,
        news: MacroNewsSentiment,
        drawdownPct: Double
    ): DownturnSafetyLevel {
        val volatilityRatio = bar.atr / bar.close
        val isBelow200Ema = bar.close < (bar.ema200 * 0.995)

        return when {
            volatilityRatio > 0.040 || news.sentimentScore < -0.50 -> DownturnSafetyLevel.LEVEL_3_CRASH_LIQUIDATION
            isBelow200Ema -> DownturnSafetyLevel.LEVEL_2_SECULAR_BEAR
            bar.close < bar.ema50 || drawdownPct > 12.0 -> DownturnSafetyLevel.LEVEL_1_MILD_CHOP
            else -> DownturnSafetyLevel.LEVEL_0_NORMAL
        }
    }

    /**
     * Evaluates market bar, TradingView SuperTrend PineScript confluence, news, and multi-index breadth.
     * Configured to actively place 1 to 2 trades per day (max 2 active positions).
     */
    fun evaluateSignal(
        currentBar: MarketBar,
        accountBalance: Double,
        activePositionsCount: Int,
        hasOpenPosition: Boolean = false,
        newsSentiment: MacroNewsSentiment = MacroNewsSentiment(),
        currentDrawdownPct: Double = 0.0,
        prevBar: MarketBar? = null
    ): TradeSignal {
        if (currentBar.date != lastTradeDate) {
            lastTradeDate = currentBar.date
            tradesTodayCount = 0
            if (cooldownDaysRemaining > 0) cooldownDaysRemaining--
        }

        val regime = classifyMarketRegime(currentBar)
        val multiIndex = currentBar.multiIndex
        val tvAnalysis = TradingViewConfluenceEngine.analyzeBar(currentBar, prevBar)
        val downturnLevel = evaluateDownturnSafety(currentBar, newsSentiment, currentDrawdownPct)

        if (cooldownDaysRemaining > 0) {
            return TradeSignal(
                action = TradeAction.HOLD,
                symbol = "SPY_AI",
                entryPrice = currentBar.close,
                stopLoss = 0.0,
                takeProfit = 0.0,
                positionSize = 0.0,
                confidenceScore = 0.0,
                reasoning = "Capital Preservation Cooldown ($cooldownDaysRemaining days remaining after $consecutiveLossesCount losses).",
                date = currentBar.date,
                timestamp = currentBar.timestamp,
                regime = regime,
                macroSentiment = newsSentiment,
                multiIndex = multiIndex,
                orderType = preferredOrderType,
                downturnSafety = downturnLevel
            )
        }

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
                regime = regime,
                macroSentiment = newsSentiment,
                multiIndex = multiIndex,
                orderType = preferredOrderType,
                downturnSafety = downturnLevel
            )
        }

        val aiScore = mlOptimizer.predictConfidenceScore(currentBar, newsSentiment, multiIndex)
        val mlMetrics = mlOptimizer.getOptimizationMetrics()
        val adaptedThreshold = minOf(0.48, mlMetrics.adaptedBuyThreshold)

        val combinedScore = (aiScore * 0.60) + (tvAnalysis.confluenceScore * 0.40)

        // Fractional Kelly Sizing
        val winRateFrac = (mlMetrics.recentWinRatePct / 100.0).coerceIn(0.40, 0.75)
        val winLossRatio = atrTakeProfitMultiplier / atrStopLossMultiplier
        val fullKelly = if (winLossRatio > 0) (winRateFrac * winLossRatio - (1.0 - winRateFrac)) / winLossRatio else 0.10
        var optimalRiskPct = (baseRiskPerTradePct * (0.85 + (fullKelly * 0.30))).coerceIn(0.008, 0.02)

        if (downturnLevel == DownturnSafetyLevel.LEVEL_1_MILD_CHOP) {
            optimalRiskPct *= 0.50
        }

        val isConfirmedAbove200Ema = currentBar.close >= (currentBar.ema200 * 1.005)

        // Exit condition for open position
        val isExitCondition = hasOpenPosition && (
                downturnLevel == DownturnSafetyLevel.LEVEL_3_CRASH_LIQUIDATION ||
                downturnLevel == DownturnSafetyLevel.LEVEL_2_SECULAR_BEAR ||
                (currentBar.close < currentBar.ema50 && currentBar.rsi < 38.0) ||
                currentBar.rsi > 80.0 ||
                tvAnalysis.signal == TradingViewSignal.BEARISH_CONFLUENCE
        )

        return when {
            // BUY Signal (New Entry): Allows up to 2 active positions simultaneously (activePositionsCount < 2)
            combinedScore >= adaptedThreshold && isConfirmedAbove200Ema && downturnLevel <= DownturnSafetyLevel.LEVEL_1_MILD_CHOP && activePositionsCount < maxDailyTrades && newsSentiment.sentimentScore >= -0.25 -> {
                tradesTodayCount++
                val stopLoss = currentBar.close - (currentBar.atr * atrStopLossMultiplier)
                val takeProfit = currentBar.close + (currentBar.atr * atrTakeProfitMultiplier)
                val riskAmount = accountBalance * optimalRiskPct
                val riskPerShare = max(0.5, currentBar.close - stopLoss)
                val shares = (riskAmount / riskPerShare)

                TradeSignal(
                    action = TradeAction.BUY,
                    symbol = "SPY_AI",
                    entryPrice = currentBar.close,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    positionSize = shares,
                    confidenceScore = combinedScore,
                    reasoning = "Trade #${tradesTodayCount}/2 Today. Kelly Risk (${String.format(Locale.US, "%.2f", optimalRiskPct * 100)}%). ML Score: ${String.format(Locale.US, "%.0f", combinedScore * 100)}%.",
                    date = currentBar.date,
                    timestamp = currentBar.timestamp,
                    regime = regime,
                    macroSentiment = newsSentiment,
                    multiIndex = multiIndex,
                    orderType = preferredOrderType,
                    downturnSafety = downturnLevel
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
                    confidenceScore = 1.0 - combinedScore,
                    reasoning = "Downturn Protection Exit: ${if (downturnLevel == DownturnSafetyLevel.LEVEL_3_CRASH_LIQUIDATION) "Emergency Panic Circuit Breaker" else if (downturnLevel == DownturnSafetyLevel.LEVEL_2_SECULAR_BEAR) "Secular Bear Shield (< 200-EMA)" else "SuperTrend Reversal"}.",
                    date = currentBar.date,
                    timestamp = currentBar.timestamp,
                    regime = regime,
                    macroSentiment = newsSentiment,
                    multiIndex = multiIndex,
                    orderType = preferredOrderType,
                    downturnSafety = downturnLevel
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
                    confidenceScore = combinedScore,
                    reasoning = if (downturnLevel == DownturnSafetyLevel.LEVEL_2_SECULAR_BEAR) "Level 2 Secular Bear Shield Active (Below 200-EMA) - 100% Cash Allocation." else if (downturnLevel == DownturnSafetyLevel.LEVEL_3_CRASH_LIQUIDATION) "Level 3 Emergency Crash Circuit Breaker Active - Cash Preserved." else "Scanning setup (1-2 trades/day max).",
                    date = currentBar.date,
                    timestamp = currentBar.timestamp,
                    regime = regime,
                    macroSentiment = newsSentiment,
                    multiIndex = multiIndex,
                    orderType = preferredOrderType,
                    downturnSafety = downturnLevel
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
}
