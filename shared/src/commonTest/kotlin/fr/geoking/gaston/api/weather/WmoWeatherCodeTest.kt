package fr.geoking.gaston.api.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WmoWeatherCodeTest {

    @Test
    fun allDocumentedOpenMeteoCodesHaveDistinctStyles() {
        val codes = listOf(
            0, 1, 2, 3, 45, 48,
            51, 53, 55, 56, 57,
            61, 63, 65, 66, 67,
            71, 73, 75, 77,
            80, 81, 82, 85, 86,
            95, 96, 99,
        )
        val styles = codes.map { wmoWeatherStyle(it) }
        assertEquals(codes.size, styles.size)
        styles.forEach { style ->
            assertTrue(style.label.isNotBlank())
            assertTrue(style.family != WmoWeatherFamily.Unknown)
        }
        assertEquals(WmoWeatherFamily.Clear, wmoWeatherStyle(0).family)
        assertEquals(WmoWeatherFamily.Fog, wmoWeatherStyle(45).family)
        assertEquals(WmoWeatherFamily.Rain, wmoWeatherStyle(63).family)
        assertEquals(WmoWeatherFamily.Snow, wmoWeatherStyle(75).family)
        assertEquals(WmoWeatherFamily.Storm, wmoWeatherStyle(95).family)
        assertEquals(WmoWeatherFamily.Unknown, wmoWeatherStyle(42).family)
    }

    @Test
    fun wmoCodeLabelMatchesStyleLabel() {
        assertEquals(wmoWeatherStyle(61).label, wmoCodeLabel(61))
    }
}
