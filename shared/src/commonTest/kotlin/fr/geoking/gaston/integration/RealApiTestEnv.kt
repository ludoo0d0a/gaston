package fr.geoking.gaston.integration

import fr.geoking.gaston.shared.platform.getEnv

/**
 * Thrown when a live integration test needs an API key from the environment and it is missing.
 *
 * @param envKeys One or more environment variable names (e.g. `FUELPRICES_DK_KEY`).
 */
class MissingIntegrationTestEnvException(
    val envKeys: List<String>,
    message: String,
) : IllegalStateException(message)

internal fun requireIntegrationEnv(envKey: String, usage: String): String =
    requireIntegrationEnvs(envKey to usage).getValue(envKey)

internal fun requireIntegrationEnvs(vararg required: Pair<String, String>): Map<String, String> {
    val missing = required
        .map { it.first }
        .filter { key -> getEnv(key).isNullOrBlank() }
    if (missing.isNotEmpty()) {
        val catalog = required.joinToString("; ") { (key, usage) -> "$key ($usage)" }
        throw MissingIntegrationTestEnvException(
            envKeys = missing,
            message = buildString {
                append("Integration test missing required environment variable(s): ")
                append(missing.joinToString(", "))
                append(". Required keys: ")
                append(catalog)
                append(". Set in CI secrets or export before running with -PrunRealApiTests=true.")
            },
        )
    }
    return required.associate { (key, _) -> key to getEnv(key)!!.trim() }
}
