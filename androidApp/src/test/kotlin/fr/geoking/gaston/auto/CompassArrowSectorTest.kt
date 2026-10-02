package fr.geoking.gaston.auto

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassArrowSectorTest {

    @Test
    fun sector_cardinalsAndDiagonals() {
        assertEquals(0, compassArrowSector(0f))
        assertEquals(2, compassArrowSector(45f))
        assertEquals(4, compassArrowSector(90f))
        assertEquals(8, compassArrowSector(180f))
        assertEquals(12, compassArrowSector(270f))
    }

    @Test
    fun sector_roundsToNearest22_5() {
        assertEquals(0, compassArrowSector(11.24f))
        assertEquals(1, compassArrowSector(11.26f))
        assertEquals(1, compassArrowSector(22.5f))
        assertEquals(1, compassArrowSector(33.74f))
        assertEquals(2, compassArrowSector(33.76f))
    }

    @Test
    fun sector_wrapsAround360() {
        assertEquals(0, compassArrowSector(360f))
        assertEquals(0, compassArrowSector(359f))
        assertEquals(15, compassArrowSector(337.5f))
        assertEquals(15, compassArrowSector(-22.5f))
        assertEquals(0, compassArrowSector(-1f))
    }

    @Test
    fun drawableRes_mapsSector0And15() {
        assertEquals(fr.geoking.gaston.R.drawable.ic_compass_arrow_00, compassArrowDrawableRes(0f))
        assertEquals(fr.geoking.gaston.R.drawable.ic_compass_arrow_15, compassArrowDrawableRes(337.5f))
        assertEquals(fr.geoking.gaston.R.drawable.ic_compass_arrow_04, compassArrowDrawableRes(90f))
    }
}
