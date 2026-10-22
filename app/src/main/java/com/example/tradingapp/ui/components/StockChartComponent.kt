package com.example.tradingapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tradingapp.model.MarketBar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun StockCandleChart(
    bars: List<MarketBar>,
    modifier: Modifier = Modifier.fillMaxWidth().height(200.dp)
) {
    if (bars.isEmpty()) return

    val displayBars = bars.takeLast(40)
    val maxPrice = displayBars.maxOf { max(it.high, it.bollingerUpper) }
    val minPrice = displayBars.minOf { min(it.low, it.bollingerLower) }
    val priceRange = max(1.0, maxPrice - minPrice)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Candlestick & Technical Indicator Chart (Last 40 Days)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Canvas(modifier = modifier) {
                val width = size.width
                val height = size.height
                val candleWidth = width / displayBars.size
                val bodyWidth = candleWidth * 0.6f

                // Draw Bollinger Band shaded region
                val bUpperPath = Path()
                val bLowerPath = Path()

                displayBars.forEachIndexed { i, bar ->
                    val x = (i * candleWidth) + (candleWidth / 2f)
                    val upperY = height - ((bar.bollingerUpper - minPrice) / priceRange * height).toFloat()
                    val lowerY = height - ((bar.bollingerLower - minPrice) / priceRange * height).toFloat()

                    if (i == 0) {
                        bUpperPath.moveTo(x, upperY)
                        bLowerPath.moveTo(x, lowerY)
                    } else {
                        bUpperPath.lineTo(x, upperY)
                        bLowerPath.lineTo(x, lowerY)
                    }
                }

                drawPath(
                    path = bUpperPath,
                    color = Color.Gray.copy(alpha = 0.3f),
                    style = Stroke(width = 2f)
                )

                // Draw EMA lines
                val ema20Path = Path()
                val ema50Path = Path()

                displayBars.forEachIndexed { i, bar ->
                    val x = (i * candleWidth) + (candleWidth / 2f)
                    val y20 = height - ((bar.ema20 - minPrice) / priceRange * height).toFloat()
                    val y50 = height - ((bar.ema50 - minPrice) / priceRange * height).toFloat()

                    if (i == 0) {
                        ema20Path.moveTo(x, y20)
                        ema50Path.moveTo(x, y50)
                    } else {
                        ema20Path.lineTo(x, y20)
                        ema50Path.lineTo(x, y50)
                    }
                }

                drawPath(path = ema20Path, color = Color(0xFF1565C0), style = Stroke(width = 3f))
                drawPath(path = ema50Path, color = Color(0xFFE65100), style = Stroke(width = 3f))

                // Draw Candlesticks
                displayBars.forEachIndexed { i, bar ->
                    val xCenter = (i * candleWidth) + (candleWidth / 2f)
                    val isGreen = bar.close >= bar.open
                    val color = if (isGreen) Color(0xFF2E7D32) else Color(0xFFC62828)

                    val highY = height - ((bar.high - minPrice) / priceRange * height).toFloat()
                    val lowY = height - ((bar.low - minPrice) / priceRange * height).toFloat()
                    val openY = height - ((bar.open - minPrice) / priceRange * height).toFloat()
                    val closeY = height - ((bar.close - minPrice) / priceRange * height).toFloat()

                    // Draw Wick
                    drawLine(
                        color = color,
                        start = Offset(xCenter, highY),
                        end = Offset(xCenter, lowY),
                        strokeWidth = 2f
                    )

                    // Draw Candle Body
                    val topY = min(openY, closeY)
                    val bodyHeight = max(2f, kotlin.math.abs(openY - closeY))

                    drawRect(
                        color = color,
                        topLeft = Offset(xCenter - (bodyWidth / 2f), topY),
                        size = Size(bodyWidth, bodyHeight)
                    )
                }
            }
        }
    }
}
