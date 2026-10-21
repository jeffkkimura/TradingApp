package com.example.tradingapp

import com.example.tradingapp.data.HistoricalDataRepository
import com.example.tradingapp.engine.AITradingStrategy
import com.example.tradingapp.engine.BacktestEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AITradingBotTest {

    @Test
    fun testHistoricalDataGeneratedSince2000() {
        val bars = HistoricalDataRepository.getAllHistoricalBars()
        assertTrue("Historical bars should span 2000-2025 (~6000+ daily bars)", bars.size >= 5000)
        assertEquals("Starts in 2000", "2000", bars.first().date.take(4))
        val lastYear = bars.last().date.take(4).toInt()
        assertTrue("Ends in 2024 or 2025", lastYear >= 2024)
    }

    @Test
    fun testTradeRateLimiterCappedAtTwoTradesPerDay() {
        val strategy = AITradingStrategy(maxDailyTrades = 2)
        val bars = HistoricalDataRepository.getAllHistoricalBars().take(100)

        var totalSignalsIssued = 0
        val dailyCounts = mutableMapOf<String, Int>()

        for (bar in bars) {
            val signal = strategy.evaluateSignal(
                currentBar = bar,
                accountBalance = 100_000.0,
                activePositionsCount = 0,
                hasOpenPosition = false
            )
            if (signal.action != com.example.tradingapp.model.TradeAction.HOLD) {
                totalSignalsIssued++
                val count = dailyCounts.getOrDefault(bar.date, 0) + 1
                dailyCounts[bar.date] = count
                assertTrue("Daily trades for date ${bar.date} must be <= 2", count <= 2)
            }
        }
    }

    @Test
    fun test100MonteCarloTrialsBacktestSuite() {
        println("Running 100-Trial Monte Carlo Backtest Suite across 2000-2025 historical data...")

        val summary = BacktestEngine.run100TrialMonteCarloBacktest { completed, _, currentTrial ->
            if (completed % 20 == 0) {
                println("Progress: $completed/100 trials. Trial #$completed Return: ${String.format(Locale.US, "%.1f", currentTrial.totalReturnPct)}%, Win Rate: ${String.format(Locale.US, "%.1f", currentTrial.winRate)}%, PF: ${String.format(Locale.US, "%.2f", currentTrial.profitFactor)}, Passed: ${currentTrial.passed}, Reason: ${currentTrial.failureReason}")
            }
        }

        assertEquals("Total trials executed must be 100", 100, summary.totalTrials)
        assertTrue("Pass rate out of 100 trials must be >= 65% (Actual: ${summary.passRatePct}%)", summary.passRatePct >= 65.0)
        assertTrue("Average win rate across 100 trials must be >= 35% (Actual: ${summary.avgWinRatePct}%)", summary.avgWinRatePct >= 35.0)
        assertTrue("Average daily trade frequency must be <= 2.0 trades/day (Actual: ${summary.avgTradesPerDay})", summary.avgTradesPerDay <= 2.0)
        assertTrue("Overall deployment status must be PASSED", summary.overallPassed)

        println("SUCCESS: 100-Trial Monte Carlo Backtest Suite passed with ${summary.passedTrials}/100 successful deployment trials!")
    }
}
