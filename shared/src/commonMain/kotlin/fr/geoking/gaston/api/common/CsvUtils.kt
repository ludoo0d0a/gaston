package fr.geoking.gaston.api.common

/**
 * Minimal CSV/DSV parsing helpers for open data exports.
 *
 * Supports:
 * - Custom delimiter (e.g. '|')
 * - Double-quoted fields with escaped quotes ("")
 *
 * This is intentionally small and dependency-free for KMP shared code.
 */
object CsvUtils {
    fun parseLine(line: String, delimiter: Char = ','): List<String> {
        if (line.isEmpty()) return listOf("")
        val out = ArrayList<String>(16)
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    out.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}

