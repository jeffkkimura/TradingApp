package com.example.tradingapp.engine

import com.example.tradingapp.data.HistoricalDataRepository
import com.example.tradingapp.model.BacktestSummary
import com.example.tradingapp.model.ClosedTrade
import com.example.tradingapp.model.MacroNewsSentiment
import com.example.tradingapp.model.MarketBar
import com.example.tradingapp.model.OrderType
import com.example.tradingapp.model.TradeAction
import com.example.tradingapp.model.TradePosition
import com.example.tradingapp.model.TrialResult
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

object BacktestEngine {

    private const val SLIPPAGE_PCT = 0.0002 // 0.02% market slippage
    private const val COMMISSION_PER_TRADE = 0.50 // $0.50 flat fee

    fun runSingleBacktest(
        bars: List<MarketBar>,
        initialCapital: Double = 100_000.0,
        strategy: AITradingStrategy = AITradingStrategy(),
        trialId: Int = 1,
        startDate: String = bars.firstOrNull()?.date ?: "2000-01-03",
        endDate: String = bars.lastOrNull()?.date ?: "2025-01-01",
        newsSentiment: MacroNewsSentiment = MacroNewsSentiment()
    ): TrialResult {
        if (bars.isEmpty()) {
            return TrialResult(
                trialId = trialId,
                startDate = startDate,
                endDate = endDate,
                startingCapital = initialCapital,
                finalCapital = initialCapital,
                totalReturnPct = 0.0,
                winRate = 0.0,
                totalTrades = 0,
                avgTradesPerDay = 0.0,
                maxDrawdownPct = 0.0,
                sharpeRatio = 0.0,
                profitFactor = 0.0,
                passed = false,
                failureReason = "No price bars provided"
            )
        }

        var cash = initialCapital
        var currentPosition: TradePosition? = null
        val closedTrades = mutableListOf<ClosedTrade>()
        val equityCurve = mutableListOf<Double>()
        var cumulativeSlippageSaved = 0.0

        var peakEquity = initialCapital
        var maxDrawdownAmount = 0.0

        val dailyReturns = mutableListOf<Double>()
        var previousEquity = initialCapital

        strategy.resetDailyLimit()

        for (bar in bars) {
            // 1. Update open position valuation & trailing stop loss / take profit
            if (currentPosition != null) {
                val pos = currentPosition
                pos.currentPrice = bar.close

                if (bar.close > pos.entryPrice + (bar.atr * 1.5)) {
                    val calculatedTrailingStop = bar.close - (bar.atr * 2.5)
                    if (calculatedTrailingStop > pos.stopLoss) {
                        pos.stopLoss = calculatedTrailingStop
                    }
                }

                var exitTrade = false
                var exitPrice = bar.close
                var exitReason = ""
                var slippageSavedThisTrade = 0.0

                if (bar.low <= pos.stopLoss) {
                    exitTrade = true
                    val activeSlippage = if (pos.orderType == OrderType.LIMIT_ORDER_STRICT) 0.0 else SLIPPAGE_PCT
                    exitPrice = pos.stopLoss * (1.0 - activeSlippage)
                    exitReason = if (pos.stopLoss > pos.entryPrice) "Trailing Stop Loss (Profit Locked)" else "Stop Loss Hit"
                } else if (bar.high >= pos.takeProfit) {
                    exitTrade = true
                    // Limit Order Take Profit execution achieves ZERO negative slippage!
                    val activeSlippage = if (pos.orderType == OrderType.HYBRID_SMART_ROUTING || pos.orderType == OrderType.LIMIT_ORDER_STRICT) 0.0 else SLIPPAGE_PCT
                    exitPrice = pos.takeProfit * (1.0 - activeSlippage)
                    exitReason = "Take Profit Limit Hit (Zero Slippage)"
                    if (activeSlippage == 0.0) {
                        slippageSavedThisTrade = pos.quantity * pos.takeProfit * SLIPPAGE_PCT
                        cumulativeSlippageSaved += slippageSavedThisTrade
                    }
                }

                if (exitTrade) {
                    val grossProceeds = pos.quantity * exitPrice
                    val pnl = grossProceeds - (pos.quantity * pos.entryPrice) - COMMISSION_PER_TRADE
                    val returnPct = (pnl / (pos.quantity * pos.entryPrice)) * 100.0

                    cash += grossProceeds - COMMISSION_PER_TRADE

                    val closed = ClosedTrade(
                        id = pos.id,
                        symbol = pos.symbol,
                        type = pos.type,
                        entryPrice = pos.entryPrice,
                        exitPrice = exitPrice,
                        quantity = pos.quantity,
                        pnl = pnl,
                        returnPct = returnPct,
                        entryDate = pos.entryDate,
                        exitDate = bar.date,
                        exitReason = exitReason,
                        mlScoreAtEntry = pos.mlScoreAtEntry,
                        orderType = pos.orderType,
                        slippageSavedDollars = slippageSavedThisTrade
                    )
                    closedTrades.add(closed)
                    strategy.mlOptimizer.onTradeClosed(closed, bar, newsSentiment, bar.multiIndex)
                    strategy.notifyTradeOutcome(closed.pnl > 0)
                    currentPosition = null
                }
            }

            // 2. Evaluate Strategy Signal
            val openPosCount = if (currentPosition != null) 1 else 0
            val signal = strategy.evaluateSignal(
                currentBar = bar,
                accountBalance = cash + (currentPosition?.unrealizedPnl ?: 0.0),
                activePositionsCount = openPosCount,
                hasOpenPosition = (currentPosition != null),
                newsSentiment = newsSentiment
            )

            // 3. Process BUY / SELL Signal Executions
            if (signal.action == TradeAction.BUY && currentPosition == null && signal.positionSize > 0) {
                val entrySlippage = if (signal.orderType == OrderType.LIMIT_ORDER_STRICT) 0.0 else SLIPPAGE_PCT
                val entryPriceWithSlippage = bar.close * (1.0 + entrySlippage)
                val totalCost = (signal.positionSize * entryPriceWithSlippage) + COMMISSION_PER_TRADE

                if (cash >= totalCost) {
                    cash -= totalCost
                    currentPosition = TradePosition(
                        id = "TRADE_${trialId}_${bar.timestamp}",
                        symbol = signal.symbol,
                        type = TradeAction.BUY,
                        entryPrice = entryPriceWithSlippage,
                        quantity = signal.positionSize,
                        stopLoss = signal.stopLoss,
                        takeProfit = signal.takeProfit,
                        entryDate = bar.date,
                        currentPrice = bar.close,
                        mlScoreAtEntry = signal.confidenceScore,
                        orderType = signal.orderType
                    )
                }
            } else if (signal.action == TradeAction.SELL && currentPosition != null) {
                val pos = currentPosition
                val exitSlippage = if (pos.orderType == OrderType.LIMIT_ORDER_STRICT) 0.0 else SLIPPAGE_PCT
                val exitPriceWithSlippage = bar.close * (1.0 - exitSlippage)
                val grossProceeds = pos.quantity * exitPriceWithSlippage
                val pnl = grossProceeds - (pos.quantity * pos.entryPrice) - COMMISSION_PER_TRADE
                val returnPct = (pnl / (pos.quantity * pos.entryPrice)) * 100.0

                cash += grossProceeds - COMMISSION_PER_TRADE

                val closed = ClosedTrade(
                    id = pos.id,
                    symbol = pos.symbol,
                    type = pos.type,
                    entryPrice = pos.entryPrice,
                    exitPrice = exitPriceWithSlippage,
                    quantity = pos.quantity,
                    pnl = pnl,
                    returnPct = returnPct,
                    entryDate = pos.entryDate,
                    exitDate = bar.date,
                    exitReason = "AI Reversal Signal",
                    mlScoreAtEntry = pos.mlScoreAtEntry,
                    orderType = pos.orderType,
                    slippageSavedDollars = 0.0
                )
                closedTrades.add(closed)
                strategy.mlOptimizer.onTradeClosed(closed, bar, newsSentiment, bar.multiIndex)
                strategy.notifyTradeOutcome(closed.pnl > 0)
                currentPosition = null
            }

            // 4. Record Equity Curve & Metrics
            val currentEquity = cash + (currentPosition?.currentMarketValue ?: 0.0)
            equityCurve.add(currentEquity)

            if (currentEquity > peakEquity) {
                peakEquity = currentEquity
            }
            val drawdown = peakEquity - currentEquity
            if (drawdown > maxDrawdownAmount) {
                maxDrawdownAmount = drawdown
            }

            val dailyReturn = (currentEquity - previousEquity) / previousEquity
            dailyReturns.add(dailyReturn)
            previousEquity = currentEquity
        }

        // Close any remaining position at end of backtest window
        if (currentPosition != null) {
            val pos = currentPosition
            val lastBar = bars.last()
            val grossProceeds = pos.quantity * lastBar.close
            val pnl = grossProceeds - (pos.quantity * pos.entryPrice) - COMMISSION_PER_TRADE
            cash += grossProceeds - COMMISSION_PER_TRADE

            val closed = ClosedTrade(
                id = pos.id,
                symbol = pos.symbol,
                type = pos.type,
                entryPrice = pos.entryPrice,
                exitPrice = lastBar.close,
                quantity = pos.quantity,
                pnl = pnl,
                returnPct = (pnl / (pos.quantity * pos.entryPrice)) * 100.0,
                entryDate = pos.entryDate,
                exitDate = lastBar.date,
                exitReason = "Backtest Window Close",
                mlScoreAtEntry = pos.mlScoreAtEntry,
                orderType = pos.orderType
            )
            closedTrades.add(closed)
            strategy.mlOptimizer.onTradeClosed(closed, lastBar, newsSentiment, lastBar.multiIndex)
        }

        val finalEquity = cash
        val totalReturnPct = ((finalEquity - initialCapital) / initialCapital) * 100.0
        val maxDrawdownPct = if (peakEquity > 0) (maxDrawdownAmount / peakEquity) * 100.0 else 0.0

        val totalTrades = closedTrades.size
        val winningTrades = closedTrades.count { it.pnl > 0 }
        val winRatePct = if (totalTrades > 0) (winningTrades.toDouble() / totalTrades) * 100.0 else 0.0

        val totalGains = closedTrades.filter { it.pnl > 0 }.sumOf { it.pnl }
        val totalLosses = abs(closedTrades.filter { it.pnl < 0 }.sumOf { it.pnl })
        val profitFactor = if (totalLosses > 0) totalGains / totalLosses else if (totalGains > 0) 3.0 else 1.0

        val avgTradesPerDay = if (bars.isNotEmpty()) totalTrades.toDouble() / bars.size else 0.0

        val meanDailyReturn = if (dailyReturns.isNotEmpty()) dailyReturns.average() else 0.0
        val variance = if (dailyReturns.size > 1) dailyReturns.sumOf { (it - meanDailyReturn) * (it - meanDailyReturn) } / (dailyReturns.size - 1) else 0.0
        val stdDevDailyReturn = sqrt(variance)
        val sharpeRatio = if (stdDevDailyReturn > 0) (meanDailyReturn / stdDevDailyReturn) * sqrt(252.0) else 0.0

        val passed = totalReturnPct > 0.0 &&
                profitFactor >= 1.01 &&
                maxDrawdownPct <= 28.0 &&
                avgTradesPerDay <= 2.0

        val failureReason = when {
            totalReturnPct <= 0 -> "Negative total return (${String.format(Locale.US, "%.1f", totalReturnPct)}%)"
            profitFactor < 1.01 -> "Profit factor below target (${String.format(Locale.US, "%.2f", profitFactor)})"
            maxDrawdownPct > 28.0 -> "Max drawdown excessive (${String.format(Locale.US, "%.1f", maxDrawdownPct)}%)"
            avgTradesPerDay > 2.0 -> "Trade rate exceeded 2 trades/day (${String.format(Locale.US, "%.2f", avgTradesPerDay)})"
            else -> ""
        }

        return TrialResult(
            trialId = trialId,
            startDate = startDate,
            endDate = endDate,
            startingCapital = initialCapital,
            finalCapital = finalEquity,
            totalReturnPct = totalReturnPct,
            winRate = winRatePct,
            totalTrades = totalTrades,
            avgTradesPerDay = avgTradesPerDay,
            maxDrawdownPct = maxDrawdownPct,
            sharpeRatio = sharpeRatio,
            profitFactor = profitFactor,
            passed = passed,
            failureReason = failureReason,
            totalSlippageSavedDollars = cumulativeSlippageSaved
        )
    }

    /**
     * Runs 100 Monte Carlo test trials across random sub-windows of historical market data since 2000.
     */
    fun run100TrialMonteCarloBacktest(
        onProgressUpdate: (completed: Int, total: Int, currentTrial: TrialResult) -> Unit = { _, _, _ -> }
    ): BacktestSummary {
        val trialsList = mutableListOf<TrialResult>()
        val totalTrialsCount = 100

        for (trialId in 1..totalTrialsCount) {
            val seed = trialId * 10007L + 2000L
            val daysWindow = (500 + (trialId * 7) % 750)
            val subBars = HistoricalDataRepository.getRandomSubWindow(daysWindow, seed)

            val strategy = AITradingStrategy(
                maxDailyTrades = 2,
                buyConfidenceThreshold = 0.50,
                preferredOrderType = OrderType.HYBRID_SMART_ROUTING
            )

            val trialResult = runSingleBacktest(
                bars = subBars,
                initialCapital = 100_000.0,
                strategy = strategy,
                trialId = trialId,
                startDate = subBars.firstOrNull()?.date ?: "2000-01-03",
                endDate = subBars.lastOrNull()?.date ?: "2025-01-01"
            )

            trialsList.add(trialResult)
            onProgressUpdate(trialId, totalTrialsCount, trialResult)
        }

        val passedTrialsCount = trialsList.count { it.passed }
        val passRate = (passedTrialsCount.toDouble() / totalTrialsCount) * 100.0

        val avgReturn = trialsList.map { it.totalReturnPct }.average()
        val avgWinRate = trialsList.map { it.winRate }.average()
        val avgMaxDrawdown = trialsList.map { it.maxDrawdownPct }.average()
        val avgSharpe = trialsList.map { it.sharpeRatio }.average()
        val avgTradesPerDay = trialsList.map { it.avgTradesPerDay }.average()
        val totalSlippageSaved = trialsList.sumOf { it.totalSlippageSavedDollars }

        val bestTrial = trialsList.maxOfOrNull { it.totalReturnPct } ?: 0.0
        val worstTrial = trialsList.minOfOrNull { it.totalReturnPct } ?: 0.0

        val overallPassed = passRate >= 65.0

        return BacktestSummary(
            totalTrials = totalTrialsCount,
            passedTrials = passedTrialsCount,
            passRatePct = passRate,
            avgReturnPct = avgReturn,
            avgWinRatePct = avgWinRate,
            avgMaxDrawdownPct = avgMaxDrawdown,
            avgSharpeRatio = avgSharpe,
            avgTradesPerDay = avgTradesPerDay,
            bestTrialReturnPct = bestTrial,
            worstTrialReturnPct = worstTrial,
            overallPassed = overallPassed,
            trials = trialsList,
            totalSlippageSavedDollars = totalSlippageSaved
        )
    }
}
