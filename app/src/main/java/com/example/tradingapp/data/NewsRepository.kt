package com.example.tradingapp.data

import com.example.tradingapp.model.FedPolicyStance
import com.example.tradingapp.model.InflationTrend
import com.example.tradingapp.model.MacroNewsSentiment
import com.example.tradingapp.model.MacroRiskEvent

object NewsRepository {

    fun getMacroNewsPresets(): List<MacroNewsSentiment> {
        return listOf(
            MacroNewsSentiment(
                headline = "Fed signals rate cuts as CPI inflation cools to 2.1%; labor market stabilizes.",
                sentimentScore = 0.45,
                fedPolicy = FedPolicyStance.DOVISH_CUTTING,
                inflation = InflationTrend.COOLING,
                riskEvent = MacroRiskEvent.NORMAL,
                source = "Federal Reserve Monetary Policy Scanner"
            ),
            MacroNewsSentiment(
                headline = "Generative AI capex surge propels tech earnings beat across QQQ & S&P 500 index.",
                sentimentScore = 0.50,
                fedPolicy = FedPolicyStance.NEUTRAL,
                inflation = InflationTrend.COOLING,
                riskEvent = MacroRiskEvent.EARNINGS_SEASON,
                source = "Wall Street Tech Earnings Catalyst"
            ),
            MacroNewsSentiment(
                headline = "FOMC holds benchmark rates steady; Fed chair stresses data dependence.",
                sentimentScore = 0.10,
                fedPolicy = FedPolicyStance.NEUTRAL,
                inflation = InflationTrend.STABLE,
                riskEvent = MacroRiskEvent.FED_FOMC_MEETING,
                source = "FOMC Statement & Press Conference"
            ),
            MacroNewsSentiment(
                headline = "Unexpected CPI inflation spike causes 10-year Treasury yields to surge; Rate hikes feared.",
                sentimentScore = -0.45,
                fedPolicy = FedPolicyStance.HAWKISH_HIKING,
                inflation = InflationTrend.REACCELERATING,
                riskEvent = MacroRiskEvent.NORMAL,
                source = "US Department of Labor CPI Report"
            ),
            MacroNewsSentiment(
                headline = "Middle East geopolitical oil shipping disruption triggers volatility spike across global markets.",
                sentimentScore = -0.55,
                fedPolicy = FedPolicyStance.HAWKISH_HIKING,
                inflation = InflationTrend.REACCELERATING,
                riskEvent = MacroRiskEvent.GEOPOLITICAL_SPIKE,
                source = "Global Geopolitical Volatility Monitor"
            )
        )
    }

    fun getDefaultMacroNews(): MacroNewsSentiment {
        return getMacroNewsPresets().first()
    }
}
