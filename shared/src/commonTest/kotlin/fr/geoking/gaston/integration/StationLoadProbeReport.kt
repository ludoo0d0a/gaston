package fr.geoking.gaston.integration

import fr.geoking.gaston.poi.CountryStationProbe
import fr.geoking.gaston.shared.network.NetworkException
import java.io.File

enum class StationLoadProbeStatus {
    OK,
    SKIPPED,
    FAILED,
    MISSING_ENV_KEY,
    AUTH_ERROR,
}

data class StationLoadProbeResult(
    val iso: String,
    val cityLabel: String,
    val fuelProvider: String,
    val status: StationLoadProbeStatus,
    val stationCount: Int? = null,
    val validCount: Int? = null,
    val message: String? = null,
    val envKeys: List<String> = emptyList(),
)

object StationLoadProbeReport {

    private const val DEFAULT_REPORT_FILE = "station-load-probe-report.json"

    fun reportFile(): File {
        val path = System.getenv("STATION_LOAD_PROBE_REPORT")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_REPORT_FILE
        return File(path)
    }

    fun write(results: List<StationLoadProbeResult>, target: File = reportFile()) {
        target.parentFile?.mkdirs()
        target.writeText(toJson(results))
        println("Wrote station load probe report to ${target.absolutePath}")
    }

    fun classifyFailure(exception: Throwable?, message: String): StationLoadProbeStatus {
        if (exception is MissingIntegrationTestEnvException) {
            return StationLoadProbeStatus.MISSING_ENV_KEY
        }
        if (exception is NetworkException && exception.httpCode in AUTH_HTTP_CODES) {
            return StationLoadProbeStatus.AUTH_ERROR
        }
        val lower = message.lowercase()
        if (AUTH_HTTP_CODES.any { code -> lower.contains(code.toString()) }) {
            return StationLoadProbeStatus.AUTH_ERROR
        }
        if (
            lower.contains("unauthorized") ||
            lower.contains("forbidden") ||
            lower.contains("invalid api key") ||
            lower.contains("invalid apikey") ||
            lower.contains("api key") ||
            lower.contains("apikey") ||
            lower.contains("authentication failed") ||
            lower.contains("access denied")
        ) {
            return StationLoadProbeStatus.AUTH_ERROR
        }
        return StationLoadProbeStatus.FAILED
    }

    internal fun fromProbe(
        probe: CountryStationProbe,
        outcome: ProbeOutcome,
    ): StationLoadProbeResult = when (outcome) {
        is ProbeOutcome.Success ->
            StationLoadProbeResult(
                iso = probe.iso,
                cityLabel = probe.cityLabel,
                fuelProvider = probe.fuelProvider.name,
                status = StationLoadProbeStatus.OK,
                stationCount = outcome.total,
                validCount = outcome.valid,
            )
        is ProbeOutcome.Skipped ->
            StationLoadProbeResult(
                iso = probe.iso,
                cityLabel = probe.cityLabel,
                fuelProvider = probe.fuelProvider.name,
                status = StationLoadProbeStatus.SKIPPED,
                message = outcome.reason,
            )
        is ProbeOutcome.Failed ->
            StationLoadProbeResult(
                iso = probe.iso,
                cityLabel = probe.cityLabel,
                fuelProvider = probe.fuelProvider.name,
                status = outcome.status,
                message = outcome.message,
                envKeys = outcome.envKeys,
            )
    }

    private val AUTH_HTTP_CODES = setOf(401, 403)

    private fun toJson(results: List<StationLoadProbeResult>): String = buildString {
        append("{\n  \"probes\": [\n")
        results.forEachIndexed { index, result ->
            if (index > 0) append(",\n")
            append("    {\n")
            val fields = buildList {
                add("\"iso\": ${jsonString(result.iso)}")
                add("\"cityLabel\": ${jsonString(result.cityLabel)}")
                add("\"fuelProvider\": ${jsonString(result.fuelProvider)}")
                add("\"status\": ${jsonString(result.status.name.lowercase())}")
                result.stationCount?.let { add("\"stationCount\": $it") }
                result.validCount?.let { add("\"validCount\": $it") }
                result.message?.let { add("\"message\": ${jsonString(it)}") }
                if (result.envKeys.isNotEmpty()) {
                    add(
                        "\"envKeys\": [${
                            result.envKeys.joinToString(", ") { jsonString(it) }
                        }]",
                    )
                }
            }
            fields.forEachIndexed { fieldIndex, field ->
                append("      ")
                append(field)
                if (fieldIndex < fields.lastIndex) append(",")
                append("\n")
            }
            append("    }")
        }
        append("\n  ]\n}\n")
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

}

internal sealed interface ProbeOutcome {
    data class Success(val total: Int, val valid: Int) : ProbeOutcome
    data class Skipped(val reason: String) : ProbeOutcome
    data class Failed(
        val message: String,
        val status: StationLoadProbeStatus = StationLoadProbeStatus.FAILED,
        val envKeys: List<String> = emptyList(),
    ) : ProbeOutcome
}
