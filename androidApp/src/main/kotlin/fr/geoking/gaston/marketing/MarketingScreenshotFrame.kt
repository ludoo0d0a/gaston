package fr.geoking.gaston.marketing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Play Store / website export chrome: brand backdrop + rounded phone chassis (boîtier),
 * not a flat white 9:16 rectangle.
 */
@Composable
fun MarketingScreenshotFrame(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2D1B4E),
                        Color(0xFF160A30),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.86f)
                .aspectRatio(9f / 19.5f)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF3A2860),
                            Color(0xFF1F1235),
                        ),
                    ),
                )
                .padding(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF0F0A18)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp)),
                ) {
                    content()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .size(width = 72.dp, height = 18.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A0F2E)),
                )
            }
        }
    }
}
