package fr.geoking.gaston.api.argentina

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

data class ArgentinaCSVRow(
    val cuit: String,
    val empresa: String,
    val direccion: String,
    val localidad: String,
    val provincia: String,
    val producto: String,
    val tipohorario: String,
    val precio: Double,
    val latitude: Double,
    val longitude: Double,
    val empresabandera: String
)

class ArgentinaEnergiaClient(private val client: HttpClient) {
    private val csvUrl = "https://datos.energia.gob.ar/dataset/1c181390-5045-475e-94dc-410429be4b17/resource/80ac25de-a44a-4445-9215-090cf55cfda5/download/precios-en-surtidor-resolucin-3142016.csv"

    suspend fun fetchAllData(): List<ArgentinaCSVRow> {
        val response = client.get(csvUrl) {
            header("User-Agent", "Mozilla/5.0 (compatible; Julius/1.0)")
        }
        val text = response.bodyAsText()
        return parseCSV(text)
    }

    private fun parseCSV(text: String): List<ArgentinaCSVRow> {
        val lines = text.split("\n")
        if (lines.size < 2) return emptyList()

        val header = parseCSVLine(lines[0])
        val colIdx = { name: String -> header.indexOf(name) }

        val rows = mutableListOf<ArgentinaCSVRow>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val fields = parseCSVLine(line)
            try {
                val precio = fields[colIdx("precio")].toDoubleOrNull() ?: 0.0
                val lat = fields[colIdx("latitud")].toDoubleOrNull() ?: 0.0
                val lon = fields[colIdx("longitud")].toDoubleOrNull() ?: 0.0

                rows.add(ArgentinaCSVRow(
                    cuit = fields[colIdx("cuit")],
                    empresa = fields[colIdx("empresa")],
                    direccion = fields[colIdx("direccion")],
                    localidad = fields[colIdx("localidad")],
                    provincia = fields[colIdx("provincia")],
                    producto = fields[colIdx("producto")],
                    tipohorario = fields[colIdx("tipohorario")],
                    precio = precio,
                    latitude = lat,
                    longitude = lon,
                    empresabandera = fields[colIdx("empresabandera")]
                ))
            } catch (e: Exception) { }
        }
        return rows
    }

    private fun parseCSVLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        var current = ""
        var inQuotes = false

        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current += '"'
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == ',' && !inQuotes) {
                fields.add(current.trim())
                current = ""
            } else {
                current += ch
            }
            i++
        }
        fields.add(current.trim())
        return fields
    }
}
