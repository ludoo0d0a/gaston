package fr.geoking.gaston

import androidx.annotation.StringRes

/**
 * APIs and open-data services used by Gaston. Shown in Settings → About and Android Auto About.
 */
data class UsedApi(
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    @StringRes val attributionRes: Int? = null,
)

val UsedApisList: List<UsedApi> = listOf(
    // Routing & maps
    UsedApi("OSRM", "https://project-osrm.org", "https://project-osrm.org/favicon.ico"),
    UsedApi("TomTom Routing", "https://developer.tomtom.com/routing-api", null),
    UsedApi("OpenFreeMap", "https://openfreemap.org", null),
    UsedApi("OpenStreetMap", "https://www.openstreetmap.org", "https://www.openstreetmap.org/favicon.ico"),
    UsedApi("CARTO basemaps", "https://carto.com", null),
    UsedApi("Esri World Imagery / Topo", "https://www.esri.com", null),

    // Geocoding & place search
    UsedApi("Nominatim (OpenStreetMap)", "https://nominatim.org", null),
    UsedApi("adresse.data.gouv.fr", "https://adresse.data.gouv.fr", null),
    UsedApi("Open-Meteo Geocoding", "https://open-meteo.com", null),

    // Fuel — France
    UsedApi("prix-carburants.gouv.fr", "https://www.prix-carburants.gouv.fr", null),
    UsedApi("data.gouv.fr", "https://www.data.gouv.fr", "https://www.data.gouv.fr/favicon.ico"),
    UsedApi("data.economie.gouv.fr", "https://data.economie.gouv.fr", null),
    UsedApi("Gas API (prix carburants)", "https://gas-api.ovh", null),
    UsedApi("Routex / Wigeogis", "https://www.wigeogis.com", null),

    // Fuel — Europe & beyond (by provider)
    UsedApi("UK Fuel Finder (CMA)", "https://www.gov.uk/guidance/access-fuel-price-data", null),
    UsedApi("MIMIT (Italy official)", "https://www.mimit.gov.it", null),
    UsedApi("goriva.si (Slovenia official)", "https://goriva.si", null),
    UsedApi("DrivstoffAppen (Norway & Sweden)", "https://drivstoffappen.no", null),
    UsedApi("DGEG (Portugal official)", "https://precoscombustiveis.dgeg.gov.pt", null),
    UsedApi("ANWB", "https://www.anwb.nl", null),
    UsedApi("Fuelprices.dk (Denmark)", "https://fuelprices.dk", null),
    UsedApi("Fuelo.net", "https://fuelo.net", null),
    UsedApi("FuelCheck (NSW Australia)", "https://www.service.nsw.gov.au/transaction/fuel-price-check-scheme", null),
    UsedApi("FuelWatch (WA Australia)", "https://www.fuelwatch.wa.gov.au", null),
    UsedApi("PetrolSpy (Australia)", "https://petrolspy.com.au", null),
    UsedApi("Comparis (Switzerland)", "https://www.comparis.ch/benzin-preise", null),
    UsedApi("MZOE (Croatia official)", "https://mzoe-gor.hr", null),
    UsedApi("Polttoaine.net (Finland)", "https://www.polttoaine.net", null),
    UsedApi("FuelGR (Greece)", "https://fuelgr.gr", null),
    UsedApi("Pick A Pump (Ireland)", "https://pickapump.com", null),
    UsedApi("ANRE (Moldova)", "https://www.anre.md", null),
    UsedApi("Peco Online (Romania)", "https://pecoonline.ro", null),
    UsedApi("NIS (Serbia)", "https://www.nis.rs", null),
    UsedApi("cenagoriva.rs (Serbia)", "https://cenagoriva.rs", null),
    UsedApi("CRE (Mexico)", "https://www.gob.mx/cre", null),
    UsedApi("Secretaría de Energía (Argentina)", "https://datos.energia.gob.ar", null),
    UsedApi(
        name = "OpenVan.camp",
        url = "https://openvan.camp",
        logoUrl = null,
        attributionRes = R.string.openvan_attribution,
    ),
    UsedApi("Spain Minetur (official)", "https://sedeaplicaciones.minetur.gob.es", null),
    UsedApi("Tankerkönig (Germany)", "https://creativecommons.tankerkoenig.de", null),
    UsedApi("E-Control (Austria)", "https://www.e-control.at", null),
    UsedApi("Belgium official fuel prices", "https://petrolprices.economie.fgov.be", null),
    UsedApi("EIA (US official)", "https://www.eia.gov/opendata/browser/petroleum/pri", null),

    // EV charging
    UsedApi("Open Charge Map", "https://openchargemap.org", "https://openchargemap.org/favicon.ico"),
    UsedApi("ODRE (bornes IRVE)", "https://odre.opendatasoft.com", null),
    UsedApi("Chargy (Luxembourg)", "https://chargy.lu", null),
    UsedApi("char.gy (UK)", "https://char.gy", null),
    UsedApi("Eco-Movement (OCPI)", "https://eco-movement.com", null),
    UsedApi("Fastned (OCPI)", "https://fastnedcharging.com", null),
    UsedApi("DKV Mobility (OCPI)", "https://www.dkv-mobility.com", null),
    UsedApi("Belib (Paris EV)", "https://opendata.paris.fr", null),
    UsedApi("QualiCharge IRVE (dispo temps réel)", "https://transport.data.gouv.fr", null),

    // POIs & amenities
    UsedApi("Overpass API (OpenStreetMap)", "https://wiki.openstreetmap.org/wiki/Overpass_API", "https://www.openstreetmap.org/favicon.ico"),
    UsedApi("Hérault Data (camping-car)", "https://www.herault-data.fr", null),

    // Parking
    UsedApi("LiveParking", "https://liveparking.eu", null),
    UsedApi("ParkAPI (parkendd.de)", "https://api.parkendd.de", null),

    // Transit
    UsedApi("RATP API (Paris)", "https://api-ratp.pierre-grimaud.fr", null),
    UsedApi("STIB-MIVB (Brussels)", "https://data.stib-mivb.brussels", null),
    UsedApi("mobiliteit.lu / HAFAS", "https://www.mobiliteit.lu", null),

    // Weather
    UsedApi("Open-Meteo", "https://open-meteo.com", null),
    UsedApi("MET Norway", "https://api.met.no", null),

    // Traffic, toll & forecasts
    UsedApi("CITA (trafic Luxembourg)", "https://www.cita.lu", "https://www.cita.lu/favicon.ico"),
    UsedApi("TomTom Traffic", "https://developer.tomtom.com/traffic-api", null),
    UsedApi("OpenTollData", "https://github.com/louis2038/OpenTollData", null),
    UsedApi("Stooq (fuel forecast)", "https://stooq.com", null),
)
