package fr.geoking.gaston.ui

import fr.geoking.gaston.R
import fr.geoking.gaston.VehicleType

val OVERPASS_AMENITY_OPTIONS = listOf(
    "toilets" to R.string.amenity_toilets,
    "drinking_water" to R.string.amenity_drinking_water,
    "camp_site" to R.string.amenity_camp_site,
    "caravan_site" to R.string.amenity_caravan_site,
    "picnic_site" to R.string.amenity_picnic_site,
    "truck_stop" to R.string.amenity_truck_stop,
    "rest_area" to R.string.amenity_rest_area,
    "restaurant" to R.string.amenity_restaurant,
    "fast_food" to R.string.amenity_fast_food,
    "speed_camera" to R.string.amenity_speed_camera,
    "parking" to R.string.amenity_parking,
    "viewpoint" to R.string.amenity_viewpoint
)

val VEHICLE_TYPE_OPTIONS = listOf(
    VehicleType.Car to "Car",
    VehicleType.Truck to "Truck",
    VehicleType.Motorcycle to "Moto",
    VehicleType.Motorhome to "CC"
)

val MAP_ENERGY_OPTIONS = listOf(
    "electric" to "Elec",
    "gazole" to "Gazole",
    "sp98" to "SP98",
    "sp95" to "SP95",
    "gplc" to "GPLc",
    "e85" to "E85"
)

val MAP_ENSEIGNE_OPTIONS = listOf(
    "all" to "Toutes",
    "major" to "Major",
    "gms" to "GMS",
    "independant" to "Indépendant"
)

val MAP_IRVE_OPERATOR_OPTIONS = listOf(
    "all" to "Tous",
    "atlante" to "Atlante",
    "avia" to "Avia",
    "zunder" to "Zunder",
    "ionity" to "Ionity",
    "fastned" to "Fastned",
    "tesla" to "Tesla"
)

val MAP_CONNECTOR_OPTIONS = listOf(
    "type_2" to "Type 2",
    "combo_ccs" to "CCS",
    "chademo" to "CHAdeMO",
    "ef" to "E/F",
    "autre" to "Autre"
)

val MAP_IRVE_POWER_OPTIONS = listOf(
    0 to "0kW",
    20 to "20+",
    50 to "50+",
    100 to "100+",
    200 to "200+",
    300 to "300+"
)

val MAP_SERVICES_OPTIONS = listOf(
    "aire_camping_cars" to "Aire CC",
    "automate_cb" to "CB 24/24",
    "bornes_electriques" to "Elec",
    "boutique_alimentaire" to "Boutique",
    "dab" to "DAB",
    "lavage_auto" to "Lavage Auto",
    "lavage_manuel" to "Lavage Manuel",
    "restauration_place" to "Resto",
    "restauration_emporter" to "Takeaway",
    "toilettes" to "Toilettes",
    "station_gonflage" to "Gonflage",
    "wifi" to "Wifi"
)
