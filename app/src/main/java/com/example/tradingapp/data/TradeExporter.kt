package com.example.tradingapp.data

import com.example.tradingapp.model.BacktestSummary
import com.example.tradingapp.model.ClosedTrade
import java.util.Locale

object TradeExporter {

    fun generateTradeLogCsv(trades: List<ClosedTrade>): String {
        val sb = StringBuilder()
        sb.append("Trade ID,Symbol,Type,Entry Date,Exit Date,Entry Price,Exit Price,P&L ($),Return (%),ML Score,Exit Reason\n")

        for (t in trades) {
            sb.append("${t.id},")
                .append("${t.symbol},")
                .append("${t.type},")
                .append("${t.entryDate},")
                .append("${t.exitDate},")
                .append("${String.format(Locale.US, "%.2f", t.entryPrice)},")
                .append("${String.format(Locale.US, "%.2f", t.exitPrice)},")
                .append("${String.format(Locale.US, "%.2f", t.pnl)},")
                .append("${String.format(Locale.US, "%.2f", t.returnPct)},")
                .append("${String.format(Locale.US, "%.0f", t.mlScoreAtEntry * 100)}%,")
                .append("\"${t.exitReason}\"\n")
        }

        return sb.toString()
    }

    fun generateBacktestSummaryReport(summary: BacktestSummary): String {
        val sb = StringBuilder()
        sb.append("100-TRIAL MONTE CARLO BACKTEST REPORT\n")
            .append("=====================================\n")
            .append("Total Trials: ${summary.totalTrials}\n")
            .append("Passed Trials: ${summary.passedTrials}\n")
            .append("Pass Rate: ${String.format(Locale.US, "%.1f", summary.passRatePct)}%\n")
            .append("Average Return: +${String.format(Locale.US, "%.1f", summary.avgReturnPct)}%\n")
            .append("Average Win Rate: ${String.format(Locale.US, "%.1f", summary.avgWinRatePct)}%\n")
            .append("Average Max Drawdown: ${String.format(Locale.US, "%.1f", summary.avgMaxDrawdownPct)}%\n")
            .append("Average Sharpe Ratio: ${String.format(Locale.US, "%.2f", summary.avgSharpeRatio)}\n")
            .append("Average Trades/Day: ${String.format(Locale.US, "%.2f", summary.avgTradesPerDay)}\n")
            .append("Overall Status: ${if (summary.overallPassed) "PASSED & DEPLOYMENT APPROVED" else "TESTING"}\n")

        return sb.toString()
    }
}
