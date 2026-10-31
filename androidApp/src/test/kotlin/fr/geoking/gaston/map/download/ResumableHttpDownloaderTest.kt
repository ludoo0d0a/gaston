package fr.geoking.gaston.map.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumableHttpDownloaderTest {

    @Test
    fun parseContentRangeTotal_readsTotalSize() {
        assertEquals(1234L, ResumableHttpDownloader.parseContentRangeTotal("bytes 100-199/1234"))
        assertEquals(500L, ResumableHttpDownloader.parseContentRangeTotal("bytes 0-499/500"))
    }

    @Test
    fun parseContentRangeTotal_handlesUnknownOrMissing() {
        assertNull(ResumableHttpDownloader.parseContentRangeTotal(null))
        assertNull(ResumableHttpDownloader.parseContentRangeTotal(""))
        assertNull(ResumableHttpDownloader.parseContentRangeTotal("bytes 0-99/*"))
        assertNull(ResumableHttpDownloader.parseContentRangeTotal("invalid"))
    }
}
