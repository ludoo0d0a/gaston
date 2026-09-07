package fr.geoking.gaston.api.evpricesfr

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.headers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Fetches **free/public** EV charging "day price" baselines for France by scraping the operator pages
 * identified earlier (Fastned, Allego, Electra, TotalEnergies, IONITY), plus a Tesla community dataset.
 *
 * This is intentionally coarse: it is NOT a per-station pricing engine.
 */
class EvPricesFrClient(
    private val http: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchBaselines(): List<EvPriceBaseline> {
        val out = mutableListOf<EvPriceBaseline>()
        out += fetchFastned()
        out += fetchAllego()
        out += fetchElectra()
        out += fetchTotalEnergies()
        out += fetchIonity()
        out += fetchTeslaCommunity()
        return out
    }

    private suspend fun getText(url: String): String {
        val resp = http.get(url) {
            headers {
                append(HttpHeaders.UserAgent, "gaston-android/ev-prices-fr (Ktor)")
                append(HttpHeaders.AcceptLanguage, "fr-FR,fr;q=0.9,en;q=0.8")
            }
        }
        val body = resp.bodyAsText()
        if (resp.status.value !in 200..299) {
            throw NetworkException(resp.status.value, "EV price fetch failed ($url): ${body.take(500)}")
        }
        return body
    }

    private suspend fun getGitHubFileLastCommitDateIso(
        owner: String,
        repo: String,
        path: String
    ): String? {
        // Public GitHub API (no auth). Rate-limited but fine for occasional app use.
        val url = "https://api.github.com/repos/$owner/$repo/commits?path=$path&per_page=1&page=1"
        val body = getText(url)
        val el = json.parseToJsonElement(body)
        val arr = el as? JsonArray ?: return null
        val first = arr.firstOrNull() as? JsonObject ?: return null
        val commit = first["commit"]?.jsonObject ?: return null
        val committer = commit["committer"]?.jsonObject ?: return null
        return committer["date"]?.jsonPrimitive?.content
    }

    private fun parseDecimal(text: String): Double {
        val cleaned = text
            .replace("\u00A0", " ")
            .replace("€", "")
            .replace("/kWh", "", ignoreCase = true)
            .replace("kWh", "", ignoreCase = true)
            .trim()
            .replace(",", ".")
        val num = cleaned.replace(Regex("[^0-9.]"), "")
        return num.toDoubleOrNull() ?: error("Could not parse decimal from '$text' (cleaned='$num')")
    }

    private fun stripTags(html: String): String =
        html.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun requireGroup(m: MatchResult?, idx: Int): String =
        m?.groupValues?.getOrNull(idx)?.takeIf { it.isNotBlank() }
            ?: error("Missing regex group $idx")

    private suspend fun fetchFastned(): EvPriceBaseline {
        val url = "https://fastned.nl/fr/recharge/tarifs"
        val html = getText(url)
        val sectionMatch = Regex(
            "Nouveaux prix au kWh en France([\\s\\S]{0,1500})",
            setOf(RegexOption.IGNORE_CASE)
        ).find(html) ?: error("Fastned: section not found")

        val section = sectionMatch.groupValues[1]
        val prices = Regex("(\\d{1,2},\\d{2})\\s*€")
            .findAll(section)
            .map { it.groupValues[1] }
            .toList()
        if (prices.size < 3) error("Fastned: expected 3 prices, got $prices")

        return EvPriceBaseline(
            provider = EvPriceProvider.Fastned,
            country = "FR",
            currency = "EUR",
            sourceUrl = url,
            lastUpdateDate = null,
            priceModel = "kwh_fixed_by_offer",
            values = mapOf(
                "standard_eur_per_kwh" to parseDecimal(prices[0]),
                "app_eur_per_kwh" to parseDecimal(prices[1]),
                "subscription_eur_per_kwh" to parseDecimal(prices[2]),
            ),
            notes = "Tariffs published as fixed EUR/kWh for France; roaming providers may add fees."
        )
    }

    private suspend fun fetchAllego(): EvPriceBaseline {
        val url = "https://www.allego.eu/fr/tarifs/"
        val html = getText(url)
        val m = Regex(
            "France[\\s\\S]*?Chargement ultra-rapide[\\s\\S]*?€\\s*0,(\\d{3})/kWh" +
                "[\\s\\S]*?Chargement rapide[\\s\\S]*?€\\s*0,(\\d{3})/kWh" +
                "[\\s\\S]*?Chargement régulier[\\s\\S]*?€\\s*0,(\\d{3})/kWh",
            setOf(RegexOption.IGNORE_CASE)
        ).find(html) ?: error("Allego: could not match France ultra/fast/regular block")

        val ultra = parseDecimal("0,${requireGroup(m, 1)}")
        val fast = parseDecimal("0,${requireGroup(m, 2)}")
        val regular = parseDecimal("0,${requireGroup(m, 3)}")

        return EvPriceBaseline(
            provider = EvPriceProvider.Allego,
            country = "FR",
            currency = "EUR",
            sourceUrl = url,
            lastUpdateDate = null,
            priceModel = "kwh_fixed_by_power_category",
            values = mapOf(
                "regular_eur_per_kwh" to regular,
                "fast_eur_per_kwh" to fast,
                "ultra_fast_eur_per_kwh" to ultra,
            ),
            notes = "Published as default Allego direct-pay tariffs for France; eMSP/roaming may differ."
        )
    }

    private suspend fun fetchElectra(): EvPriceBaseline {
        val url = "https://www.go-electra.com/en/price/"
        val html = getText(url)
        val anchorIdx = html.indexOf("Price varies with demand", ignoreCase = true)
        val window = if (anchorIdx >= 0) html.substring(anchorIdx, (anchorIdx + 5000).coerceAtMost(html.length)) else html
        val plain = stripTags(window)

        // Example: "Price varies with demand between 0.39-0.61€ / kWh incl. VAT"
        val m = Regex("between\\s*([0-9]+(?:\\.[0-9]+)?)\\s*-\\s*([0-9]+(?:\\.[0-9]+)?)\\s*€\\s*/\\s*kWh", RegexOption.IGNORE_CASE)
            .find(plain)
            ?: Regex("([0-9]+(?:\\.[0-9]+)?)\\s*-\\s*([0-9]+(?:\\.[0-9]+)?)\\s*€\\s*/\\s*kWh", RegexOption.IGNORE_CASE)
                .find(plain)
            ?: error("Electra: could not find FR pricing range near 'Price varies with demand'")

        val lo = parseDecimal(requireGroup(m, 1))
        val hi = parseDecimal(requireGroup(m, 2))

        return EvPriceBaseline(
            provider = EvPriceProvider.Electra,
            country = "FR",
            currency = "EUR",
            sourceUrl = url,
            lastUpdateDate = null,
            priceModel = "kwh_range_dynamic",
            values = mapOf(
                "dynamic_min_eur_per_kwh" to lo,
                "dynamic_max_eur_per_kwh" to hi,
            ),
            notes = "Electra mentions dynamic pricing in France/Belgium; exact price is displayed/locked in the app at session start."
        )
    }

    private suspend fun fetchTotalEnergies(): EvPriceBaseline {
        val url = "https://chargeplus.totalenergies.com/fr/conseils-recharge-electrique/cout-recharge-voiture-electrique/"
        val html = getText(url)

        val matches = Regex("0,(\\d{2})\\s*€\\s*TTC/kWh").findAll(html).map { it.groupValues[0] }.toList()
        if (matches.size < 2) error("TotalEnergies: expected 2 TTC/kWh prices, got ${matches.size}")

        val lte50 = parseDecimal(matches[0])
        val gt50 = parseDecimal(matches[1])

        return EvPriceBaseline(
            provider = EvPriceProvider.TotalEnergies,
            country = "FR",
            currency = "EUR",
            sourceUrl = url,
            lastUpdateDate = null,
            priceModel = "kwh_fixed_by_power_threshold",
            values = mapOf(
                "lte_50kw_eur_per_kwh" to lte50,
                "gt_50kw_eur_per_kwh" to gt50,
            ),
            notes = "Published station-service charging prices in France (article). Some stations may also have time/occupancy fees."
        )
    }

    private suspend fun fetchIonity(): EvPriceBaseline {
        val url = "https://www.ionity.eu/fr/abonnements"
        val html = getText(url)

        val m = Regex("À partir de\\s*([0-9]+,[0-9]{2})\\s*€/kWh", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("([0-9]+,[0-9]{2})\\s*€/kWh", RegexOption.IGNORE_CASE).find(html)
            ?: error("IONITY: could not find any €/kWh on FR subscriptions page")

        val from = parseDecimal(requireGroup(m, 1))
        return EvPriceBaseline(
            provider = EvPriceProvider.Ionity,
            country = "FR",
            currency = "EUR",
            sourceUrl = url,
            lastUpdateDate = null,
            priceModel = "kwh_from_minimum",
            values = mapOf("from_eur_per_kwh" to from),
            notes = "IONITY states charging prices vary by charging point; this is a published minimum, not per-station pricing."
        )
    }

    private suspend fun fetchTeslaCommunity(): EvPriceBaseline {
        val owner = "Niek"
        val repo = "tesla-superchargers"
        val path = "superchargers-with-pricing.json"
        val url = "https://raw.githubusercontent.com/$owner/$repo/main/$path"
        val body = getText(url)
        val element = json.parseToJsonElement(body)
        val obj = element as? JsonObject ?: error("Tesla community JSON: expected object")
        val lastUpdate = getGitHubFileLastCommitDateIso(owner, repo, path)

        // The upstream schema has changed over time; currently many entries do NOT include explicit prices.
        // Filter by name containing ", France" as a coarse FR selection (free + no auth).
        var total = 0
        var france = 0
        var withPrices = 0

        for ((_, v) in obj) {
            val it = v.jsonObject
            total++
            val name = it["name"]?.jsonPrimitive?.content ?: continue
            if (!name.contains("France")) continue
            france++
            if (it["prices"] != null || it["pricing"] != null) withPrices++
        }

        return EvPriceBaseline(
            provider = EvPriceProvider.Tesla,
            country = "FR",
            currency = "EUR",
            sourceUrl = url,
            lastUpdateDate = lastUpdate,
            priceModel = "community_dataset_subset",
            values = mapOf(
                "sites_total" to total.toDouble(),
                "sites_name_contains_france" to france.toDouble(),
                "sites_with_prices_fields" to withPrices.toDouble(),
            ),
            notes = "Free community dataset for Superchargers open-to-all; upstream may omit pricing fields (currently often missing)."
        )
    }
}

