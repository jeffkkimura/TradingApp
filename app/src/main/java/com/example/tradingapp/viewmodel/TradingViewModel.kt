package com.example.tradingapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tradingapp.data.HistoricalDataRepository
import com.example.tradingapp.data.NewsRepository
import com.example.tradingapp.engine.AITradingStrategy
import com.example.tradingapp.engine.BacktestEngine
import com.example.tradingapp.model.BacktestSummary
import com.example.tradingapp.model.ClosedTrade
import com.example.tradingapp.model.EpochPerformance
import com.example.tradingapp.model.MLModelWeights
import com.example.tradingapp.model.MLOptimizationMetrics
import com.example.tradingapp.model.MacroNewsSentiment
import com.example.tradingapp.model.MarketBar
import com.example.tradingapp.model.TradeAction
import com.example.tradingapp.model.TradePosition
import com.example.tradingapp.model.TradeSignal
import com.example.tradingapp.model.TrialResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TradingUiState(
    val selectedTab: Int = 0,
    val isTestRunning: Boolean = false,
    val testProgress: Int = 0,
    val latestTrialResult: TrialResult? = null,
    val backtestSummary: BacktestSummary = BacktestSummary(),

    // Interactive Terminal State
    val currentBarIndex: Int = 0,
    val currentBar: MarketBar? = null,
    val portfolioCash: Double = 100_000.0,
    val activePositions: List<TradePosition> = emptyList(),
    val closedTrades: List<ClosedTrade> = emptyList(),
    val todaySignal: TradeSignal? = null,
    val equityHistory: List<Pair<String, Double>> = emptyList(),

    // Macro News & ML State
    val activeNewsSentiment: MacroNewsSentiment = NewsRepository.getDefaultMacroNews(),
    val newsPresets: List<MacroNewsSentiment> = NewsRepository.getMacroNewsPresets(),
    val mlModelWeights: MLModelWeights = MLModelWeights(),
    val mlMetrics: MLOptimizationMetrics = MLOptimizationMetrics(),

    // Strategy & Historical Regimes
    val epochSummaries: List<EpochPerformance> = emptyList()
)

class TradingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TradingUiState())
    val uiState: StateFlow<TradingUiState> = _uiState.asStateFlow()

    private val allBars: List<MarketBar> = HistoricalDataRepository.getAllHistoricalBars()
    private val aiStrategy = AITradingStrategy(maxDailyTrades = 2)

    init {
        val startIdx = if (allBars.size > 500) allBars.size - 300 else 0
        val initialBar = if (allBars.isNotEmpty()) allBars[startIdx] else null

        _uiState.update {
            it.copy(
                currentBarIndex = startIdx,
                currentBar = initialBar,
                equityHistory = if (initialBar != null) listOf(Pair(initialBar.date, 100_000.0)) else emptyList(),
                epochSummaries = HistoricalDataRepository.getHistoricalEpochSummaries(),
                mlModelWeights = aiStrategy.mlOptimizer.weights,
                mlMetrics = aiStrategy.mlOptimizer.getOptimizationMetrics()
            )
        }

        viewModelScope.launch(Dispatchers.Default) {
            run100TrialTest()
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun getRecentBarsForChart(): List<MarketBar> {
        val idx = _uiState.value.currentBarIndex
        if (allBars.isEmpty() || idx < 0) return emptyList()
        val start = maxOf(0, idx - 40)
        return allBars.subList(start, minOf(allBars.size, idx + 1))
    }

    fun selectNewsPreset(news: MacroNewsSentiment) {
        _uiState.update { it.copy(activeNewsSentiment = news) }
        evaluateTodaySignal()
    }

    private fun evaluateTodaySignal() {
        val state = _uiState.value
        val bar = state.currentBar ?: return
        val signal = aiStrategy.evaluateSignal(
            currentBar = bar,
            accountBalance = state.portfolioCash + state.activePositions.sumOf { it.unrealizedPnl },
            activePositionsCount = state.activePositions.size,
            hasOpenPosition = state.activePositions.isNotEmpty(),
            newsSentiment = state.activeNewsSentiment
        )
        _uiState.update { it.copy(todaySignal = signal) }
    }

    fun run100TrialTest() {
        if (_uiState.value.isTestRunning) return

        _uiState.update {
            it.copy(
                isTestRunning = true,
                testProgress = 0,
                latestTrialResult = null
            )
        }

        viewModelScope.launch(Dispatchers.Default) {
            val news = _uiState.value.activeNewsSentiment
            val summary = BacktestEngine.run100TrialMonteCarloBacktest { completed, total, currentTrial ->
                val progressPct = ((completed.toDouble() / total) * 100).toInt()
                _uiState.update { state ->
                    state.copy(
                        testProgress = progressPct,
                        latestTrialResult = currentTrial
                    )
                }
            }

            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    state.copy(
                        isTestRunning = false,
                        testProgress = 100,
                        backtestSummary = summary,
                        mlModelWeights = aiStrategy.mlOptimizer.weights,
                        mlMetrics = aiStrategy.mlOptimizer.getOptimizationMetrics()
                    )
                }
            }
        }
    }

    fun stepNextDay() {
        val state = _uiState.value
        if (allBars.isEmpty() || state.currentBarIndex >= allBars.size - 1) return

        val nextIndex = state.currentBarIndex + 1
        val bar = allBars[nextIndex]

        var cash = state.portfolioCash
        val activePosList = state.activePositions.toMutableList()
        val closedTradeList = state.closedTrades.toMutableList()

        // 1. Check existing position stop loss or take profit
        val iterator = activePosList.iterator()
        while (iterator.hasNext()) {
            val pos = iterator.next()
            pos.currentPrice = bar.close

            var exit = false
            var exitPrice = bar.close
            var reason = ""

            if (bar.low <= pos.stopLoss) {
                exit = true
                exitPrice = pos.stopLoss * 0.9998
                reason = "Stop Loss Triggered"
            } else if (bar.high >= pos.takeProfit) {
                exit = true
                exitPrice = pos.takeProfit * 0.9998
                reason = "Take Profit Target Hit"
            }

            if (exit) {
                val proceeds = pos.quantity * exitPrice
                val pnl = proceeds - (pos.quantity * pos.entryPrice) - 0.50
                cash += proceeds - 0.50

                val closed = ClosedTrade(
                    id = pos.id,
                    symbol = pos.symbol,
                    type = pos.type,
                    entryPrice = pos.entryPrice,
                    exitPrice = exitPrice,
                    quantity = pos.quantity,
                    pnl = pnl,
                    returnPct = (pnl / (pos.quantity * pos.entryPrice)) * 100.0,
                    entryDate = pos.entryDate,
                    exitDate = bar.date,
                    exitReason = reason,
                    mlScoreAtEntry = pos.mlScoreAtEntry
                )

                closedTradeList.add(0, closed)
                aiStrategy.mlOptimizer.onTradeClosed(closed, bar, state.activeNewsSentiment, bar.multiIndex)
                iterator.remove()
            }
        }

        // 2. Evaluate daily signal with active news sentiment
        val signal = aiStrategy.evaluateSignal(
            currentBar = bar,
            accountBalance = cash + activePosList.sumOf { it.unrealizedPnl },
            activePositionsCount = activePosList.size,
            hasOpenPosition = activePosList.isNotEmpty(),
            newsSentiment = state.activeNewsSentiment
        )

        // 3. Execute BUY or SELL signal
        if (signal.action == TradeAction.BUY && activePosList.isEmpty() && signal.positionSize > 0) {
            val cost = (signal.positionSize * bar.close * 1.0002) + 0.50
            if (cash >= cost) {
                cash -= cost
                activePosList.add(
                    TradePosition(
                        id = "POS_${bar.timestamp}",
                        symbol = signal.symbol,
                        type = TradeAction.BUY,
                        entryPrice = bar.close * 1.0002,
                        quantity = signal.positionSize,
                        stopLoss = signal.stopLoss,
                        takeProfit = signal.takeProfit,
                        entryDate = bar.date,
                        currentPrice = bar.close,
                        mlScoreAtEntry = signal.confidenceScore
                    )
                )
            }
        } else if (signal.action == TradeAction.SELL && activePosList.isNotEmpty()) {
            val pos = activePosList.removeAt(0)
            val proceeds = pos.quantity * bar.close * 0.9998
            val pnl = proceeds - (pos.quantity * pos.entryPrice) - 0.50
            cash += proceeds - 0.50

            val closed = ClosedTrade(
                id = pos.id,
                symbol = pos.symbol,
                type = pos.type,
                entryPrice = pos.entryPrice,
                exitPrice = bar.close * 0.9998,
                quantity = pos.quantity,
                pnl = pnl,
                returnPct = (pnl / (pos.quantity * pos.entryPrice)) * 100.0,
                entryDate = pos.entryDate,
                exitDate = bar.date,
                exitReason = "AI Sell Reversal",
                mlScoreAtEntry = pos.mlScoreAtEntry
            )
            closedTradeList.add(0, closed)
            aiStrategy.mlOptimizer.onTradeClosed(closed, bar, state.activeNewsSentiment, bar.multiIndex)
        }

        val totalPortfolioValue = cash + activePosList.sumOf { it.quantity * it.currentPrice }
        val updatedEquityHistory = (state.equityHistory + Pair(bar.date, totalPortfolioValue)).takeLast(60)

        _uiState.update {
            it.copy(
                currentBarIndex = nextIndex,
                currentBar = bar,
                portfolioCash = cash,
                activePositions = activePosList,
                closedTrades = closedTradeList,
                todaySignal = signal,
                equityHistory = updatedEquityHistory,
                mlModelWeights = aiStrategy.mlOptimizer.weights,
                mlMetrics = aiStrategy.mlOptimizer.getOptimizationMetrics()
            )
        }
    }

    fun runAutoTrader(days: Int = 30) {
        viewModelScope.launch(Dispatchers.Default) {
            repeat(days) {
                stepNextDay()
            }
        }
    }

    fun resetSimulation() {
        val startIdx = if (allBars.size > 500) allBars.size - 300 else 0
        val initialBar = if (allBars.isNotEmpty()) allBars[startIdx] else null
        aiStrategy.resetDailyLimit()

        _uiState.update {
            it.copy(
                currentBarIndex = startIdx,
                currentBar = initialBar,
                portfolioCash = 100_000.0,
                activePositions = emptyList(),
                closedTrades = emptyList(),
                todaySignal = null,
                equityHistory = if (initialBar != null) listOf(Pair(initialBar.date, 100_000.0)) else emptyList(),
                mlModelWeights = aiStrategy.mlOptimizer.weights,
                mlMetrics = aiStrategy.mlOptimizer.getOptimizationMetrics()
            )
        }
    }
}
