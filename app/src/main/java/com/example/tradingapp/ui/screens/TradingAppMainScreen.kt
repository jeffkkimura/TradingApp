package com.example.tradingapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradingapp.model.EpochPerformance
import com.example.tradingapp.model.MarketRegime
import com.example.tradingapp.model.TradeAction
import com.example.tradingapp.model.TrialResult
import com.example.tradingapp.viewmodel.TradingViewModel
import java.util.Locale

@Composable
fun TradingAppMainScreen(viewModel: TradingViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val tabs = listOf(
        "AI Strategy",
        "100-Trial Test Suite",
        "Trading Terminal",
        "2000-2025 Regimes"
    )

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(top = 36.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "AI Trading Agent (r/ai_trading)",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    text = "Historical Backtester (2000-2025) • 1-2 Trades/Day • Monte Carlo 100 Trials",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                PrimaryTabRow(selectedTabIndex = uiState.selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = uiState.selectedTab == index,
                            onClick = { viewModel.selectTab(index) },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (uiState.selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
        ) {
            when (uiState.selectedTab) {
                0 -> StrategyOverviewScreen()
                1 -> MonteCarloTestSuiteScreen(
                    viewModel = viewModel,
                    uiState = uiState
                )
                2 -> TradingTerminalScreen(
                    viewModel = viewModel,
                    uiState = uiState
                )
                3 -> HistoricalRegimesScreen(
                    epochSummaries = uiState.epochSummaries
                )
            }
        }
    }
}

@Composable
fun StrategyOverviewScreen() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "r/ai_trading Core Architecture",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "This AI trading agent implements quantitative methodologies vetted by r/ai_trading, designed specifically for swing/position trading executing 1 to 2 trades per day max to eliminate overtrading and transaction cost drag.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Key System Pillars",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PillarItem("1-2 Trades/Day Quota", "Strict rate-limiting prevents high-frequency noise and overtrading.")
                    PillarItem("Market Regime Detection", "Identifies Bull, Bear, Range-Bound, or High-Volatility crisis regimes since 2000.")
                    PillarItem("Multi-Factor AI Ensemble", "Combines EMA (20/50/200), RSI (14), MACD, and Bollinger Bands with confidence scoring (>62% threshold).")
                    PillarItem("ATR Risk Management", "Stop loss fixed at 1.5x ATR and Take Profit at 2.5x ATR (1.67 Risk-Reward ratio).")
                    PillarItem("100-Trial Monte Carlo Gate", "Must pass 100 random multi-year historical trial simulations before deployment.")
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Deployment Validation Thresholds",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Pass Rate: ≥ 80% out of 100 Monte Carlo trials", style = MaterialTheme.typography.bodySmall)
                    Text("• Minimum Win Rate: ≥ 50%", style = MaterialTheme.typography.bodySmall)
                    Text("• Maximum Drawdown Limit: ≤ 22%", style = MaterialTheme.typography.bodySmall)
                    Text("• Target Sharpe Ratio: ≥ 0.8", style = MaterialTheme.typography.bodySmall)
                    Text("• Daily Trade Frequency: 0.15 - 2.0 trades/day", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun PillarItem(title: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = "• $title", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
    }
}

@Composable
fun MonteCarloTestSuiteScreen(
    viewModel: TradingViewModel,
    uiState: com.example.tradingapp.viewmodel.TradingUiState
) {
    val summary = uiState.backtestSummary

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (summary.overallPassed) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (summary.overallPassed) "DEPLOYMENT STATUS: APPROVED" else "DEPLOYMENT STATUS: TESTING / PENDING",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (summary.overallPassed) Color(0xFF1B5E20) else Color(0xFFE65100)
                        )
                        Text(
                            text = "${summary.passedTrials} / ${summary.totalTrials} Trials Passed (${String.format(Locale.US, "%.1f", summary.passRatePct)}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray
                        )
                    }

                    Button(
                        onClick = { viewModel.run100TrialTest() },
                        enabled = !uiState.isTestRunning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(text = if (uiState.isTestRunning) "Running..." else "Run 100 Trials")
                    }
                }

                if (uiState.isTestRunning) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { uiState.testProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Simulating Trial ${uiState.latestTrialResult?.trialId ?: 0} / 100 across 2000-2025 sub-windows...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Metrics Summary Row
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                title = "Avg Return",
                value = "+${String.format(Locale.US, "%.1f", summary.avgReturnPct)}%",
                color = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            MetricCard(
                title = "Avg Win Rate",
                value = "${String.format(Locale.US, "%.1f", summary.avgWinRatePct)}%",
                color = Color(0xFF1565C0),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            MetricCard(
                title = "Avg Max DD",
                value = "${String.format(Locale.US, "%.1f", summary.avgMaxDrawdownPct)}%",
                color = Color(0xFFC62828),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            MetricCard(
                title = "Avg Sharpe",
                value = String.format(Locale.US, "%.2f", summary.avgSharpeRatio),
                color = Color(0xFF6A1B9A),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "100 Monte Carlo Trial Results (Random Windows 2000–2025)",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(summary.trials) { trial ->
                TrialRowItem(trial)
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, color: Color, modifier: Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = title, fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun TrialRowItem(trial: TrialResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (trial.passed) Color(0xFFF1F8E9) else Color(0xFFFFEBEE)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Trial #${trial.trialId} (${trial.startDate} to ${trial.endDate})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = "Return: +${String.format(Locale.US, "%.1f", trial.totalReturnPct)}% | Win Rate: ${String.format(Locale.US, "%.1f", trial.winRate)}% | Sharpe: ${String.format(Locale.US, "%.2f", trial.sharpeRatio)}",
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
                Text(
                    text = "Trades: ${trial.totalTrades} (${String.format(Locale.US, "%.2f", trial.avgTradesPerDay)}/day) | Max DD: ${String.format(Locale.US, "%.1f", trial.maxDrawdownPct)}%",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            Text(
                text = if (trial.passed) "PASSED" else "FAILED",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (trial.passed) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
    }
}

@Composable
fun TradingTerminalScreen(
    viewModel: TradingViewModel,
    uiState: com.example.tradingapp.viewmodel.TradingUiState
) {
    val bar = uiState.currentBar
    val signal = uiState.todaySignal

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Controls row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Button(
                    onClick = { viewModel.stepNextDay() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+1 Day")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.runAutoTrader(30) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Auto 30 Days")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { viewModel.resetSimulation() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset")
                }
            }
        }

        item {
            // Market Price & Regime Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Market Date: ${bar?.date ?: "N/A"}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Index Price: $${String.format(Locale.US, "%.2f", bar?.close ?: 0.0)}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    when (signal?.regime) {
                                        MarketRegime.BULL_TREND -> Color(0xFFC8E6C9)
                                        MarketRegime.BEAR_TREND -> Color(0xFFFFCDD2)
                                        MarketRegime.HIGH_VOLATILITY -> Color(0xFFFFE0B2)
                                        else -> Color(0xFFE0E0E0)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = signal?.regime?.label ?: "Regime N/A",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "RSI(14): ${String.format(Locale.US, "%.1f", bar?.rsi ?: 50.0)}",
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "ATR: ${String.format(Locale.US, "%.2f", bar?.atr ?: 0.0)}",
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "EMA 50: ${String.format(Locale.US, "%.1f", bar?.ema50 ?: 0.0)}",
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            // Today's AI Signal Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (signal?.action) {
                        TradeAction.BUY -> Color(0xFFE8F5E9)
                        TradeAction.SELL -> Color(0xFFFFEBEE)
                        else -> Color(0xFFF5F5F5)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Today's AI Trade Signal: ",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = signal?.action?.name ?: "HOLD",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = when (signal?.action) {
                                TradeAction.BUY -> Color(0xFF2E7D32)
                                TradeAction.SELL -> Color(0xFFC62828)
                                else -> Color.Gray
                            }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Confidence: ${String.format(Locale.US, "%.1f", (signal?.confidenceScore ?: 0.0) * 100)}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = signal?.reasoning ?: "Evaluating market conditions...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )

                    if (signal?.action == TradeAction.BUY) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Stop Loss: $${String.format(Locale.US, "%.2f", signal.stopLoss)}",
                                fontSize = 11.sp,
                                color = Color(0xFFC62828),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Take Profit: $${String.format(Locale.US, "%.2f", signal.takeProfit)}",
                                fontSize = 11.sp,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item {
            // Portfolio Summary Card
            val totalEquity = uiState.portfolioCash + uiState.activePositions.sumOf { it.quantity * it.currentPrice }
            val pnlPct = ((totalEquity - 100_000.0) / 100_000.0) * 100.0

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Simulated Account Balance",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", totalEquity)}",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${if (pnlPct >= 0) "+" else ""}${String.format(Locale.US, "%.2f", pnlPct)}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (pnlPct >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                    Text(
                        text = "Available Cash: $${String.format(Locale.US, "%,.2f", uiState.portfolioCash)}",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }

        item {
            Text(
                text = "Active Positions (${uiState.activePositions.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (uiState.activePositions.isEmpty()) {
            item {
                Text(
                    text = "No open positions. AI agent is scanning for setup (1-2 trades/day max).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
        } else {
            items(uiState.activePositions) { pos ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row {
                            Text(
                                text = "BUY ${pos.symbol} (${String.format(Locale.US, "%.1f", pos.quantity)} shares)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "P&L: $${String.format(Locale.US, "%.2f", pos.unrealizedPnl)} (${String.format(Locale.US, "%.1f", pos.unrealizedPnlPct)}%)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (pos.unrealizedPnl >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                        Text(
                            text = "Entry: $${String.format(Locale.US, "%.2f", pos.entryPrice)} | SL: $${String.format(Locale.US, "%.2f", pos.stopLoss)} | TP: $${String.format(Locale.US, "%.2f", pos.takeProfit)}",
                            fontSize = 10.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Recent Closed Trades (${uiState.closedTrades.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        items(uiState.closedTrades.take(15)) { trade ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (trade.pnl >= 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${trade.type} ${trade.symbol} (${trade.entryDate} -> ${trade.exitDate})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Reason: ${trade.exitReason} | Entry: $${String.format(Locale.US, "%.2f", trade.entryPrice)} -> Exit: $${String.format(Locale.US, "%.2f", trade.exitPrice)}",
                            fontSize = 10.sp,
                            color = Color.DarkGray
                        )
                    }

                    Text(
                        text = "${if (trade.pnl >= 0) "+" else ""}$${String.format(Locale.US, "%.2f", trade.pnl)} (${String.format(Locale.US, "%.1f", trade.returnPct)}%)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (trade.pnl >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }
        }
    }
}

@Composable
fun HistoricalRegimesScreen(epochSummaries: List<EpochPerformance>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Historical Regime Analysis (2000 – 2025)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Evaluating AI Bot performance vs S&P 500 across major economic crises, crashes, rate cycles, and tech bull runs over 25 years.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        items(epochSummaries) { epoch ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = epoch.epochName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = epoch.timePeriod,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(
                                    when (epoch.keyRegime) {
                                        MarketRegime.BULL_TREND -> Color(0xFFC8E6C9)
                                        MarketRegime.BEAR_TREND -> Color(0xFFFFCDD2)
                                        MarketRegime.HIGH_VOLATILITY -> Color(0xFFFFE0B2)
                                        else -> Color(0xFFE0E0E0)
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = epoch.keyRegime.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = epoch.description, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Market Return", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                text = "${if (epoch.marketReturnPct >= 0) "+" else ""}${String.format(Locale.US, "%.1f", epoch.marketReturnPct)}%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (epoch.marketReturnPct >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "AI Bot Return", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                text = "${if (epoch.botReturnPct >= 0) "+" else ""}${String.format(Locale.US, "%.1f", epoch.botReturnPct)}%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (epoch.botReturnPct >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Win Rate", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", epoch.winRatePct)}%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF1565C0)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Max DD", fontSize = 10.sp, color = Color.Gray)
                            Text(
                                text = "${String.format(Locale.US, "%.1f", epoch.maxDrawdownPct)}%",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFC62828)
                            )
                        }
                    }
                }
            }
        }
    }
}
