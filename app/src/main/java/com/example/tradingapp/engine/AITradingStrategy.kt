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
    val riskPerTradePct: Double = 0.015,
    val atrStopLossMultiplier: Double = 2.5,
    val atrTakeProfitMultiplier: Double = 4.5,
    val preferredOrderType: OrderType = OrderType.HYBRID_SMART_ROUTING,
    val mlOptimizer: MLTradeOptimizer = MLTradeOptimizer()
) {

    private var lastTradeDate: String = ""
    private var tradesTodayCount: Int = 0

    fun resetDailyLimit() {
        lastTradeDate = ""
        tradesTodayCount = 0
        mlOptimizer.reset()
    }

    /**
     * Evaluates current market bar, news sentiment, and multi-index breadth to produce a trade signal.
     */
    fun evaluateSignal(
        currentBar: MarketBar,
        accountBalance: Double,
        activePositionsCount: Int,
        hasOpenPosition: Boolean = false,
        newsSentiment: MacroNewsSentiment = MacroNewsSentiment()
    ): TradeSignal {
        if (currentBar.date != lastTradeDate) {
            lastTradeDate = currentBar.date
            tradesTodayCount = 0
        }

        val regime = classifyMarketRegime(currentBar)
        val multiIndex = currentBar.multiIndex

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
        val adaptedThreshold = mlOptimizer.getOptimizationMetrics().adaptedBuyThreshold

        val isConfirmedAbove200Ema = currentBar.close >= (currentBar.ema200 * 1.005)
        val isConfirmedBelow200Ema = currentBar.close <= (currentBar.ema200 * 0.995)

        val isExitCondition = hasOpenPosition && (
                isConfirmedBelow200Ema ||
                (currentBar.close < currentBar.ema50 && currentBar.rsi < 38.0) ||
                currentBar.rsi > 80.0 ||
                newsSentiment.sentimentScore < -0.40
        )

        return when {
            // BUY Signal (New Entry)
            aiScore >= adaptedThreshold && isConfirmedAbove200Ema && !hasOpenPosition && activePositionsCount < 2 && newsSentiment.sentimentScore >= -0.25 -> {
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
                    reasoning = "ML Score: ${String.format(Locale.US, "%.1f", aiScore * 100)}% (${preferredOrderType.label.take(22)}). Breadth: ${String.format(Locale.US, "%.0f", multiIndex.breadthScore * 100)}%. News: ${newsSentiment.fedPolicy.label}.",
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
                    confidenceScore = 1.0 - aiScore,
                    reasoning = "AI Exit Trigger: ${if (newsSentiment.sentimentScore < -0.40) "Macro News Risk Shock (${newsSentiment.headline.take(25)}...)" else if (isConfirmedBelow200Ema) "Confirmed Below 200-EMA" else "Trend Breakdown (< 50 EMA)"}.",
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
                    confidenceScore = aiScore,
                    reasoning = if (newsSentiment.sentimentScore < -0.25) "Macro News Risk Filter Active (${newsSentiment.headline.take(25)}...)" else if (currentBar.close < currentBar.ema200) "Secular Bear Regime (Below 200-EMA) - Cash Preserved." else "Scanning ML setup.",
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
