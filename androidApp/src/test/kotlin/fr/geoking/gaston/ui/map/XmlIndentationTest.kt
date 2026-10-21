package fr.geoking.gaston.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlIndentationTest {

    @Test
    fun formatXmlIndented_formatsNestedXmlNode() {
        val raw = """<ns2:situationRecord id="1"><ns2:validity><ns2:overallEndTime>2026-12-01</ns2:overallEndTime></ns2:validity></ns2:situationRecord>"""
        val formatted = formatXmlIndented(raw)
        val lines = formatted.lines()
        assertEquals(5, lines.size)
        assertEquals("""<ns2:situationRecord id="1">""", lines[0])
        assertEquals("""  <ns2:validity>""", lines[1])
        assertEquals("""    <ns2:overallEndTime>2026-12-01</ns2:overallEndTime>""", lines[2])
        assertEquals("""  </ns2:validity>""", lines[3])
        assertEquals("""</ns2:situationRecord>""", lines[4])
    }

    @Test
    fun formatXmlIndented_handlesNonXmlGracefully() {
        val plain = "Not xml"
        assertEquals("Not xml", formatXmlIndented(plain))
    }
}
