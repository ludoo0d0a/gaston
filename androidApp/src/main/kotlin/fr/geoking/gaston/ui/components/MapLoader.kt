package fr.geoking.gaston.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.geoking.gaston.ui.anim.AnimationPalette

private val MapLoaderHeight = 2.dp

/** Thin shimmer bar overlaid at the top of the map; does not affect map layout size. */
@Composable
fun BoxScope.MapLoadingOverlay(
    isLoading: Boolean,
    palette: AnimationPalette,
    modifier: Modifier = Modifier
) {
    if (!isLoading) return
    MapLoader(
        palette = palette,
        modifier = modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .zIndex(3f)
    )
}

@Composable
fun MapLoader(
    palette: AnimationPalette,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mapLoader")

    val xOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "xOffset"
    )

    val colors = listOf(
        Color.Transparent,
        Color(palette.primary).copy(alpha = 0.8f),
        Color(palette.secondary).copy(alpha = 0.9f),
        Color(palette.primary).copy(alpha = 0.8f),
        Color.Transparent
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MapLoaderHeight)
            .clipToBounds()
            .background(
                brush = Brush.linearGradient(
                    colors = colors,
                    start = androidx.compose.ui.geometry.Offset(xOffset * 1000f - 500f, 0f),
                    end = androidx.compose.ui.geometry.Offset(xOffset * 1000f, 0f)
                )
            )
    )
}
