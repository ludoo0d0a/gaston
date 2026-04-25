package fr.geoking.gaston.ui.anim

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AnimationPalette(
    val name: String,
    val colors: List<Int>
) {
    /** Cached Compose Color list to avoid repeated palette.colors.map { Color(it) } in effects. */
    val colorsAsComposeColor: List<Color> by lazy { colors.map { Color(it) } }

    val primary: Int get() = colorAt(0)
    val secondary: Int get() = colorAt(1)
    val tertiary: Int get() = colorAt(2)
    val quaternary: Int get() = colorAt(3)
    val quinary: Int get() = colorAt(4)

    fun colorAt(index: Int): Int {
        if (colors.isEmpty()) return 0
        val safeIndex = ((index % colors.size) + colors.size) % colors.size
        return colors[safeIndex]
    }
}

object AnimationPalettes {
    private val palettes = listOf(
        AnimationPalette(
            name = "Aurora",
            colors = listOf(
                0xFF6366F1.toInt(), // Indigo
                0xFF8B5CF6.toInt(), // Violet
                0xFFEC4899.toInt(), // Pink
                0xFF06B6D4.toInt(), // Cyan
                0xFF10B981.toInt()  // Emerald
            )
        ),
        AnimationPalette(
            name = "Sunset",
            colors = listOf(
                0xFFF97316.toInt(), // Orange
                0xFFF59E0B.toInt(), // Amber
                0xFFFB7185.toInt(), // Rose
                0xFFEF4444.toInt(), // Red
                0xFFA855F7.toInt()  // Purple
            )
        ),
        AnimationPalette(
            name = "Ocean",
            colors = listOf(
                0xFF0EA5E9.toInt(), // Sky
                0xFF38BDF8.toInt(), // Light Blue
                0xFF06B6D4.toInt(), // Cyan
                0xFF14B8A6.toInt(), // Teal
                0xFF22D3EE.toInt()  // Aqua
            )
        ),
        AnimationPalette(
            name = "Forest",
            colors = listOf(
                0xFF22C55E.toInt(), // Green
                0xFF16A34A.toInt(), // Emerald
                0xFF10B981.toInt(), // Teal
                0xFF84CC16.toInt(), // Lime
                0xFF4ADE80.toInt()  // Light Green
            )
        ),
        AnimationPalette(
            name = "Ember",
            colors = listOf(
                0xFFEF4444.toInt(), // Red
                0xFFF97316.toInt(), // Orange
                0xFFFBBF24.toInt(), // Gold
                0xFFDC2626.toInt(), // Deep Red
                0xFFFB7185.toInt()  // Rose
            )
        ),
        AnimationPalette(
            name = "Cosmic",
            colors = listOf(
                0xFF7C3AED.toInt(), // Purple
                0xFF6366F1.toInt(), // Indigo
                0xFF0EA5E9.toInt(), // Sky
                0xFFF472B6.toInt(), // Pink
                0xFFA855F7.toInt()  // Violet
            )
        ),
        AnimationPalette(
            name = "Micro",
            colors = listOf(
                0xFFA732FF.toInt(), // Vibrant lavender
                0xFFB388FF.toInt(), // Light lavender
                0xFF7C4DFF.toInt(), // Purple
                0xFFD1C4E9.toInt(), // Pale lavender
                0xFF9575CD.toInt()  // Medium purple
            )
        )
    )

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()

    val size: Int
        get() = palettes.size

    fun paletteFor(index: Int): AnimationPalette {
        if (palettes.isEmpty()) return AnimationPalette("Default", emptyList())
        val safeIndex = ((index % palettes.size) + palettes.size) % palettes.size
        return palettes[safeIndex]
    }

    fun currentPalette(): AnimationPalette = paletteFor(_index.value)

    fun step(delta: Int) {
        if (palettes.isEmpty()) return
        val next = _index.value + delta
        val safeIndex = ((next % palettes.size) + palettes.size) % palettes.size
        _index.value = safeIndex
    }

    fun setIndex(newIndex: Int) {
        if (palettes.isEmpty()) return
        val safeIndex = ((newIndex % palettes.size) + palettes.size) % palettes.size
        _index.value = safeIndex
    }

    /** Index of the Micro palette (last in list). */
    val microPaletteIndex: Int
        get() = (palettes.size - 1).coerceAtLeast(0)
}
