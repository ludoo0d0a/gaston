package fr.geoking.gaston.feature.emergency

/**
 * A single emergency contact (police, medical, fire, roadside, etc.) the user can dial.
 *
 * Numbers are stored exactly as the user should dial them (digits, spaces and the optional
 * leading `+` are kept as-is; the dialer intent strips spaces). Toll-free / 3-digit short
 * codes are kept short on purpose so they remain recognisable from highway signage.
 */
data class EmergencyContact(
    val label: String,
    val number: String,
    val description: String? = null,
    val category: EmergencyCategory
)

enum class EmergencyCategory { GENERAL, POLICE, MEDICAL, FIRE, ROADSIDE, OTHER }

/**
 * Country-aware registry of useful emergency numbers.
 *
 * The list is intentionally short and curated (most-useful first). When the country is
 * unknown we fall back to the European/global universal number 112 plus generic guidance.
 */
object EmergencyContactRegistry {

    fun contactsFor(countryCode: String?): List<EmergencyContact> {
        val cc = countryCode?.uppercase()?.trim()
        return when (cc) {
            "FR" -> france()
            "BE" -> belgium()
            "DE" -> germany()
            "ES" -> spain()
            "IT" -> italy()
            "PT" -> portugal()
            "NL" -> netherlands()
            "LU" -> luxembourg()
            "CH" -> switzerland()
            "AT" -> austria()
            "GB", "UK" -> unitedKingdom()
            "IE" -> ireland()
            "US" -> unitedStates()
            "CA" -> canada()
            "MA" -> morocco()
            else -> defaultEurope()
        }
    }

    /** Pretty country label for a 2-letter ISO code, falling back to the code itself. */
    fun countryDisplayName(countryCode: String?): String? {
        val cc = countryCode?.uppercase()?.trim() ?: return null
        return when (cc) {
            "FR" -> "France"
            "BE" -> "Belgium"
            "DE" -> "Germany"
            "ES" -> "Spain"
            "IT" -> "Italy"
            "PT" -> "Portugal"
            "NL" -> "Netherlands"
            "LU" -> "Luxembourg"
            "CH" -> "Switzerland"
            "AT" -> "Austria"
            "GB", "UK" -> "United Kingdom"
            "IE" -> "Ireland"
            "US" -> "United States"
            "CA" -> "Canada"
            "MA" -> "Morocco"
            else -> cc
        }
    }

    private fun france() = listOf(
        EmergencyContact("European emergency", "112", "Works everywhere in the EU", EmergencyCategory.GENERAL),
        EmergencyContact("Police / Gendarmerie", "17", "Police-Secours", EmergencyCategory.POLICE),
        EmergencyContact("SAMU", "15", "Medical emergency", EmergencyCategory.MEDICAL),
        EmergencyContact("Pompiers", "18", "Fire & rescue", EmergencyCategory.FIRE),
        EmergencyContact("Emergency SMS", "114", "Deaf / hard-of-hearing or silent", EmergencyCategory.GENERAL),
        EmergencyContact("Maritime", "196", "Sea & coastal rescue (CROSS)", EmergencyCategory.OTHER),
        EmergencyContact("Highway SOS", "112", "Use the orange terminals every 2 km on autoroutes", EmergencyCategory.ROADSIDE),
        EmergencyContact("Road info radio", "107.7", "Live traffic on autoroutes (FM)", EmergencyCategory.OTHER),
        EmergencyContact("Missing children", "116000", "European hotline", EmergencyCategory.OTHER)
    )

    private fun belgium() = listOf(
        EmergencyContact("European emergency", "112", "Police, ambulance, fire", EmergencyCategory.GENERAL),
        EmergencyContact("Police", "101", "Local police", EmergencyCategory.POLICE),
        EmergencyContact("Medical / Fire", "112", "Ambulance & pompiers", EmergencyCategory.MEDICAL),
        EmergencyContact("Roadside (Touring)", "070344777", "Touring breakdown service", EmergencyCategory.ROADSIDE),
        EmergencyContact("Roadside (VAB)", "032536363", "VAB breakdown service", EmergencyCategory.ROADSIDE),
        EmergencyContact("Anti-poison", "070245245", "Centre Antipoisons", EmergencyCategory.OTHER)
    )

    private fun germany() = listOf(
        EmergencyContact("European emergency", "112", "Ambulance, fire, rescue", EmergencyCategory.GENERAL),
        EmergencyContact("Police", "110", "Polizei-Notruf", EmergencyCategory.POLICE),
        EmergencyContact("Medical on-call", "116117", "Ärztlicher Bereitschaftsdienst", EmergencyCategory.MEDICAL),
        EmergencyContact("ADAC roadside", "+4922222222222", "Pannenhilfe", EmergencyCategory.ROADSIDE),
        EmergencyContact("Highway SOS", "112", "Use yellow Notrufsäulen along Autobahn", EmergencyCategory.ROADSIDE)
    )

    private fun spain() = listOf(
        EmergencyContact("Emergencias", "112", "All services", EmergencyCategory.GENERAL),
        EmergencyContact("Policía Nacional", "091", "National police", EmergencyCategory.POLICE),
        EmergencyContact("Guardia Civil", "062", "Highways & rural areas", EmergencyCategory.POLICE),
        EmergencyContact("SAMU / Sanitarias", "061", "Medical emergencies", EmergencyCategory.MEDICAL),
        EmergencyContact("Bomberos", "080", "Fire department", EmergencyCategory.FIRE),
        EmergencyContact("Highway info (DGT)", "011", "Traffic info / autopistas", EmergencyCategory.ROADSIDE)
    )

    private fun italy() = listOf(
        EmergencyContact("European emergency", "112", "Operator dispatches the right service", EmergencyCategory.GENERAL),
        EmergencyContact("Carabinieri", "112", "Military police", EmergencyCategory.POLICE),
        EmergencyContact("Polizia di Stato", "113", "State police", EmergencyCategory.POLICE),
        EmergencyContact("Vigili del Fuoco", "115", "Fire brigade", EmergencyCategory.FIRE),
        EmergencyContact("Emergenza sanitaria", "118", "Ambulance", EmergencyCategory.MEDICAL),
        EmergencyContact("Soccorso ACI", "803116", "Highway breakdown / autostrada", EmergencyCategory.ROADSIDE)
    )

    private fun portugal() = listOf(
        EmergencyContact("Emergência", "112", "All services", EmergencyCategory.GENERAL),
        EmergencyContact("INEM (medical)", "112", "Medical emergency line", EmergencyCategory.MEDICAL),
        EmergencyContact("Saúde 24", "808242424", "Health advice line", EmergencyCategory.MEDICAL),
        EmergencyContact("Roadside (ACP)", "707509510", "ACP breakdown", EmergencyCategory.ROADSIDE)
    )

    private fun netherlands() = listOf(
        EmergencyContact("Emergency", "112", "Police, ambulance, fire", EmergencyCategory.GENERAL),
        EmergencyContact("Police (non-urgent)", "09008844", "Non-emergency", EmergencyCategory.POLICE),
        EmergencyContact("ANWB Wegenwacht", "088269288", "Roadside assistance", EmergencyCategory.ROADSIDE),
        EmergencyContact("Highway SOS", "112", "Use the praatpaal call boxes on snelwegen", EmergencyCategory.ROADSIDE)
    )

    private fun luxembourg() = listOf(
        EmergencyContact("Urgence", "112", "Ambulance, pompiers", EmergencyCategory.GENERAL),
        EmergencyContact("Police", "113", "Police grand-ducale", EmergencyCategory.POLICE),
        EmergencyContact("ACL roadside", "26000", "Automobile Club", EmergencyCategory.ROADSIDE)
    )

    private fun switzerland() = listOf(
        EmergencyContact("European emergency", "112", "Universal", EmergencyCategory.GENERAL),
        EmergencyContact("Police", "117", "Police-Secours", EmergencyCategory.POLICE),
        EmergencyContact("Ambulance", "144", "Medical emergency", EmergencyCategory.MEDICAL),
        EmergencyContact("Pompiers", "118", "Fire department", EmergencyCategory.FIRE),
        EmergencyContact("REGA air rescue", "1414", "Helicopter rescue", EmergencyCategory.OTHER),
        EmergencyContact("TCS roadside", "140", "Highway breakdown", EmergencyCategory.ROADSIDE)
    )

    private fun austria() = listOf(
        EmergencyContact("European emergency", "112", "All services", EmergencyCategory.GENERAL),
        EmergencyContact("Police", "133", "Polizei", EmergencyCategory.POLICE),
        EmergencyContact("Ambulance", "144", "Rettung", EmergencyCategory.MEDICAL),
        EmergencyContact("Fire", "122", "Feuerwehr", EmergencyCategory.FIRE),
        EmergencyContact("ÖAMTC roadside", "120", "Pannenhilfe", EmergencyCategory.ROADSIDE)
    )

    private fun unitedKingdom() = listOf(
        EmergencyContact("Emergency", "999", "Police, ambulance, fire, coastguard", EmergencyCategory.GENERAL),
        EmergencyContact("European emergency", "112", "Also works in the UK", EmergencyCategory.GENERAL),
        EmergencyContact("NHS non-urgent", "111", "Health advice", EmergencyCategory.MEDICAL),
        EmergencyContact("Police non-urgent", "101", "Non-emergency police", EmergencyCategory.POLICE),
        EmergencyContact("Highways England", "08607660371", "Motorway issues (England)", EmergencyCategory.ROADSIDE)
    )

    private fun ireland() = listOf(
        EmergencyContact("Emergency", "112", "All services", EmergencyCategory.GENERAL),
        EmergencyContact("Emergency", "999", "Also works in Ireland", EmergencyCategory.GENERAL),
        EmergencyContact("AA roadside", "0818667788", "Breakdown", EmergencyCategory.ROADSIDE)
    )

    private fun unitedStates() = listOf(
        EmergencyContact("Emergency", "911", "Police, ambulance, fire", EmergencyCategory.GENERAL),
        EmergencyContact("Poison Control", "18002221222", "National poison line", EmergencyCategory.OTHER),
        EmergencyContact("Highway patrol (CA)", "*CHP", "Mobile shortcut, varies by state", EmergencyCategory.ROADSIDE)
    )

    private fun canada() = listOf(
        EmergencyContact("Emergency", "911", "Police, ambulance, fire", EmergencyCategory.GENERAL),
        EmergencyContact("Highway patrol", "*OPP", "Ontario Provincial Police (mobile)", EmergencyCategory.ROADSIDE)
    )

    private fun morocco() = listOf(
        EmergencyContact("Police", "19", "City police", EmergencyCategory.POLICE),
        EmergencyContact("Gendarmerie royale", "177", "Highways & countryside", EmergencyCategory.POLICE),
        EmergencyContact("SAMU", "141", "Medical emergency", EmergencyCategory.MEDICAL),
        EmergencyContact("Pompiers", "15", "Fire & rescue", EmergencyCategory.FIRE)
    )

    private fun defaultEurope() = listOf(
        EmergencyContact("European emergency", "112", "Works in all EU countries", EmergencyCategory.GENERAL),
        EmergencyContact("Universal emergency", "911", "Works in many countries (US/CA/etc.)", EmergencyCategory.GENERAL)
    )
}
