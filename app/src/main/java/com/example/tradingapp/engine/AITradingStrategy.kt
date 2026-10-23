package com.example.tradingapp.engine

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
    var buyConfidenceThreshold: Double = 0.50,
    val baseRiskPerTradePct: Double = 0.015,
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

    /**
     * Evaluates market bar, TradingView SuperTrend PineScript confluence, news, and multi-index breadth.
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
                orderType = preferredOrderType
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
                orderType = preferredOrderType
            )
        }

        val aiScore = mlOptimizer.predictConfidenceScore(currentBar, newsSentiment, multiIndex)
        val mlMetrics = mlOptimizer.getOptimizationMetrics()
        val adaptedThreshold = mlMetrics.adaptedBuyThreshold

        // Combine ML Model Score with TradingView SuperTrend Confluence Score
        val combinedScore = (aiScore * 0.60) + (tvAnalysis.confluenceScore * 0.40)

        // Fractional Kelly Sizing
        val winRateFrac = (mlMetrics.recentWinRatePct / 100.0).coerceIn(0.40, 0.75)
        val winLossRatio = atrTakeProfitMultiplier / atrStopLossMultiplier
        val fullKelly = if (winLossRatio > 0) (winRateFrac * winLossRatio - (1.0 - winRateFrac)) / winLossRatio else 0.10
        var optimalRiskPct = (baseRiskPerTradePct * (0.85 + (fullKelly * 0.30))).coerceIn(0.01, 0.025)

        if (currentDrawdownPct > 12.0) {
            optimalRiskPct *= 0.50
        }

        val isConfirmedAbove200Ema = currentBar.close >= (currentBar.ema200 * 1.005)
        val isConfirmedBelow200Ema = currentBar.close <= (currentBar.ema200 * 0.995)

        val isExitCondition = hasOpenPosition && (
                isConfirmedBelow200Ema ||
                (currentBar.close < currentBar.ema50 && currentBar.rsi < 38.0) ||
                currentBar.rsi > 80.0 ||
                newsSentiment.sentimentScore < -0.40 ||
                tvAnalysis.signal == TradingViewSignal.BEARISH_CONFLUENCE
        )

        return when {
            // BUY Signal (New Entry): Combines ML + TradingView SuperTrend Confluence
            combinedScore >= adaptedThreshold && isConfirmedAbove200Ema && !hasOpenPosition && activePositionsCount < 2 && newsSentiment.sentimentScore >= -0.25 -> {
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
                    reasoning = "TradingView SuperTrend + ML Score: ${String.format(Locale.US, "%.0f", combinedScore * 100)}%. Alpaca Order Routed. TV Confluence: ${tvAnalysis.signal.name}.",
                    date = currentBar.date,
                    timestamp = currentBar.timestamp,
                    regime = regime,
                    macroSentiment = newsSentiment,
                    multiIndex = multiIndex,
                    orderType = preferredOrderType
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
                    reasoning = "TradingView Exit: ${if (tvAnalysis.signal == TradingViewSignal.BEARISH_CONFLUENCE) "SuperTrend Bearish Reversal" else if (newsSentiment.sentimentScore < -0.40) "Macro News Risk Shock" else "Below 200-EMA"}.",
                    date = currentBar.date,
                    timestamp = currentBar.timestamp,
                    regime = regime,
                    macroSentiment = newsSentiment,
                    multiIndex = multiIndex,
                    orderType = preferredOrderType
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
                    reasoning = if (newsSentiment.sentimentScore < -0.25) "Macro News Risk Filter Active (${newsSentiment.headline.take(25)}...)" else if (currentBar.close < currentBar.ema200) "Secular Bear Regime (Below 200-EMA) - Cash Preserved." else "TradingView PineScript scanning setup.",
                    date = currentBar.date,
                    timestamp = currentBar.timestamp,
                    regime = regime,
                    macroSentiment = newsSentiment,
                    multiIndex = multiIndex,
                    orderType = preferredOrderType
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
