#!/usr/bin/env python3
"""Generate strings.xml (EN default + FR) and apply Kotlin replacements."""
import re
import xml.sax.saxutils as xml_escape
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KOTLIN_ROOT = ROOT / "androidApp/src/main/kotlin"
VALUES = ROOT / "androidApp/src/main/res/values/strings.xml"
VALUES_FR = ROOT / "androidApp/src/main/res/values-fr/strings.xml"

# Existing keys to reuse (English text -> existing key)
EXISTING = {
    "Fuel": "search_mode_fuel",
    "EV": "search_mode_ev",
    "My car": "search_mode_my_car",
    "Other": "search_mode_other",
    "Routes": "dashboard_routes",
    "Network": "dashboard_network",
    "Navigate": "navigate",
    "Gas station": "gas_station",
    "Filters": "filters",
    "For my car": "for_my_car",
    "Gas Stations": "map_title_gas_stations",
}

# (key, en, fr) — new strings only
STRINGS: list[tuple[str, str, str]] = [
    # Common actions & chrome
    ("action_back", "Back", "Retour"),
    ("action_cancel", "Cancel", "Annuler"),
    ("action_ok", "OK", "OK"),
    ("action_done", "Done", "Terminé"),
    ("action_save", "Save", "Enregistrer"),
    ("action_close", "Close", "Fermer"),
    ("action_clear", "Clear", "Effacer"),
    ("action_retry", "Retry", "Réessayer"),
    ("action_edit", "Edit", "Modifier"),
    ("action_start", "Start", "Démarrer"),
    ("action_share", "Share", "Partager"),
    ("action_refresh", "Refresh", "Actualiser"),
    ("action_copy", "Copy", "Copier"),
    ("action_copy_all", "Copy All", "Tout copier"),
    ("action_ignore", "Ignore", "Ignorer"),
    ("action_accept", "Accept", "Accepter"),
    ("action_decline", "Decline", "Refuser"),
    ("action_update", "Update", "Mettre à jour"),
    ("action_sign_in", "Sign in", "Se connecter"),
    ("action_sign_out", "Sign out", "Se déconnecter"),
    ("action_home", "Home", "Accueil"),
    ("action_recenter", "Recenter", "Recentrer"),
    ("action_zoom_in", "Zoom in", "Zoom avant"),
    ("action_zoom_out", "Zoom out", "Zoom arrière"),
    ("action_exit", "Exit", "Quitter"),
    ("action_remove", "Remove", "Supprimer"),
    ("action_add_poi", "+ POI", "+ POI"),
    ("action_show_on_map", "Show on Map", "Afficher sur la carte"),
    ("action_open_map", "Open Map", "Ouvrir la carte"),
    ("action_open_routes", "Open routes", "Ouvrir les itinéraires"),
    ("action_open_website", "Open website", "Ouvrir le site web"),
    ("action_open_external_map", "Open in External Map", "Ouvrir dans une carte externe"),
    ("action_open_in_maps", "Open in maps", "Ouvrir dans Plans"),
    ("action_copy_location", "Copy location", "Copier la position"),
    ("action_use_current_location", "Use current location", "Utiliser la position actuelle"),
    ("action_start_nav", "Start nav", "Lancer la navigation"),
    ("action_suggest_correction", "Suggest correction", "Suggérer une correction"),
    ("action_maybe_later", "Maybe later", "Plus tard"),
    ("action_upgrade_premium", "Upgrade to Premium", "Passer à Premium"),
    ("action_hide_on_map", "Hide on map", "Masquer sur la carte"),
    ("action_show_cheapest", "Show Cheapest", "Afficher les moins chers"),
    ("action_show_logs", "Show Logs", "Afficher les journaux"),
    ("action_fullscreen", "Fullscreen", "Plein écran"),
    ("action_request_details", "Request Details", "Détails de la requête"),
    ("action_body_viewer", "Body Viewer", "Corps de la réponse"),
    ("action_all", "All", "Tout"),
    ("action_manual", "Manual", "Manuel"),
    ("action_auto_by_country", "Auto (by country)", "Auto (par pays)"),

    # Screens & titles
    ("screen_settings", "Settings", "Paramètres"),
    ("screen_about", "About", "À propos"),
    ("screen_favorites", "Favorites", "Favoris"),
    ("screen_emergency", "Emergency", "Urgence"),
    ("screen_route", "Route", "Itinéraire"),
    ("screen_routes", "Routes", "Itinéraires"),
    ("screen_plan_route", "Plan route", "Planifier un itinéraire"),
    ("screen_network_location", "Network & location", "Réseau et position"),
    ("screen_network_location_info", "Network & Location Info", "Réseau et position"),
    ("screen_price_estimation", "Price Estimation", "Estimation des prix"),
    ("screen_highway_toll", "Highway toll", "Péages autoroutiers"),
    ("screen_error_log", "Error log", "Journal des erreurs"),
    ("screen_vehicle", "Vehicle", "Véhicule"),
    ("screen_map", "Map", "Carte"),
    ("screen_sources", "Sources", "Sources"),
    ("screen_theme", "Theme", "Thème"),
    ("screen_app", "App", "Application"),
    ("screen_more_options", "More Options", "Plus d'options"),
    ("screen_more", "More", "Plus"),
    ("screen_template_lab", "Template lab", "Labo de modèles"),
    ("screen_map_settings", "Map Settings", "Paramètres carte"),
    ("screen_general_filters", "General Filters", "Filtres généraux"),
    ("screen_advanced_filters", "Advanced Filters", "Filtres avancés"),
    ("screen_brands", "Brands", "Marques"),
    ("screen_enseigne", "Enseigne", "Enseigne"),
    ("screen_services", "Services", "Services"),
    ("screen_energy_types", "Energy Types", "Types d'énergie"),
    ("screen_operators", "Operators", "Opérateurs"),
    ("screen_operator", "Opérateur", "Opérateur"),
    ("screen_power_range", "Power Range", "Plage de puissance"),
    ("screen_connectors", "Connectors", "Connecteurs"),
    ("screen_poi_types", "POI Types", "Types de POI"),
    ("screen_electric_settings", "Electric Settings", "Paramètres électriques"),
    ("screen_min_power", "Min. Power", "Puiss. min."),
    ("screen_data_source", "Data Source", "Source de données"),
    ("screen_vehicle_and_range", "Vehicle & Range", "Véhicule et autonomie"),
    ("screen_brand_model", "Brand & Model", "Marque et modèle"),
    ("screen_vehicle_type", "Vehicle Type", "Type de véhicule"),
    ("screen_tank_capacity", "Tank Capacity", "Capacité du réservoir"),
    ("screen_fuel_consumption", "Fuel Consumption", "Consommation carburant"),
    ("screen_battery_capacity", "Battery Capacity", "Capacité batterie"),
    ("screen_electric_range", "Electric Range", "Autonomie électrique"),
    ("screen_electric_consumption", "Electric Consumption", "Consommation électrique"),
    ("screen_consumption", "Consumption", "Consommation"),
    ("screen_range", "Range", "Autonomie"),
    ("screen_app_theme", "App theme", "Thème de l'application"),
    ("screen_add_poi", "Add POI", "Ajouter un POI"),
    ("screen_fuel_outlook", "Fuel outlook", "Perspectives carburant"),
    ("screen_fuel_price_outlook", "Fuel Price Outlook", "Perspectives prix carburant"),
    ("screen_fuel_price_outlook_short", "Fuel price outlook", "Perspectives carburant"),
    ("screen_toll_data_opentolldata", "Toll data (OpenTollData)", "Données péages (OpenTollData)"),
    ("screen_download_toll_data", "Download toll data (OpenTollData)", "Télécharger les données péages (OpenTollData)"),
    ("screen_opentolldata", "OpenTollData", "OpenTollData"),
    ("screen_navigate_to", "Navigate to", "Naviguer vers"),
    ("screen_route_preview", "Route Preview", "Aperçu d'itinéraire"),
    ("screen_route_pois", "Route POIs", "POI de l'itinéraire"),
    ("screen_route_planning", "Route planning", "Planification d'itinéraire"),
    ("screen_your_current_location", "Your Current Location", "Votre position actuelle"),
    ("screen_gaston_error", "gaston Error", "Erreur Gaston"),
    ("screen_api_errors", "API Errors", "Erreurs API"),
    ("map_title_gas_stations", "Gas Stations", "Stations-service"),
    ("map_title_gas_stations_beta", "Gas Stations (Beta)", "Stations-service (bêta)"),
    ("map_title_navigation_preview", "Navigation Preview", "Aperçu navigation"),
    ("map_libre_lab", "MapLibre (lab)", "MapLibre (labo)"),
    ("map_open_maplibre_phone", "Open MapLibre on phone", "Ouvrir MapLibre sur le téléphone"),
    ("map_vector_openfreemap", "Vector map (OpenFreeMap)", "Carte vectorielle (OpenFreeMap)"),
    ("map_level", "Level %1$d", "Niveau %1$d"),
    ("map_template_osm", "MapTemplate (OSM)", "Modèle carte (OSM)"),
    ("map_template_custom_osm", "MapTemplate (Custom OSM)", "Modèle carte (OSM perso)"),
    ("map_custom_pan", "Custom map (pan)", "Carte personnalisée (pan)"),
    ("map_native_poi", "Native map POI", "POI carte native"),
    ("map_fastest_route", "Fastest Route", "Itinéraire le plus rapide"),
    ("map_shortest_route", "Shortest Route", "Itinéraire le plus court"),
    ("dashboard_emergency", "Emergency", "Urgence"),
    ("dashboard_price_estimation", "Price estimation", "Estimation des prix"),
    ("dashboard_price_estimation_subtitle", "Local estimate from market + nearby pumps", "Estimation locale à partir du marché et des stations proches"),
    ("route_origin", "Origin", "Départ"),
    ("route_destination", "Destination", "Destination"),
    ("route_origin_placeholder", "Origin address or city", "Adresse ou ville de départ"),
    ("route_destination_placeholder", "Destination address or city", "Adresse ou ville d'arrivée"),
    ("route_where_to", "Where to?", "Où allez-vous ?"),
    ("route_recommended_stops", "Recommended stops", "Arrêts recommandés"),
    ("route_locate_place", "Locate a place", "Localiser un lieu"),
    ("route_to_direction", "Route to a direction", "Itinéraire vers une direction"),
    ("route_plan_menu", "Plan route", "Planifier un itinéraire"),
    ("route_search_at_destination", "Search at destination", "Rechercher à destination"),
    ("route_add_favorite", "Add to favorites", "Ajouter aux favoris"),
    ("route_remove_favorite", "Remove from favorites", "Retirer des favoris"),
    ("route_poi_fallback", "POI", "POI"),
    ("route_destination_fallback", "Destination", "Destination"),
    ("route_error", "Error", "Erreur"),
    ("poi_name", "Name", "Nom"),
    ("poi_address", "Address", "Adresse"),
    ("poi_type", "Type", "Type"),
    ("poi_gas", "Gas", "Essence"),
    ("poi_irve", "IRVE", "IRVE"),
    ("poi_irve_charging", "IRVE (charging)", "IRVE (recharge)"),
    ("poi_power_kw", "Power (kW)", "Puissance (kW)"),
    ("energy_electric", "Electric", "Électrique"),
    ("energy_hybrid", "Hybrid", "Hybride"),
    ("energy_label", "Energy", "Énergie"),
    ("filter_show_traffic", "Show traffic", "Afficher le trafic"),
    ("filter_google_traffic", "Google traffic layer", "Couche trafic Google"),
    ("filter_debug_logging", "Debug logging", "Journalisation debug"),
    ("filter_capture_network_logs", "Capture network logs on map", "Capturer les journaux réseau sur la carte"),
    ("filter_simulate_premium", "Simulate premium", "Simuler Premium"),
    ("filter_simulate_premium_subtitle", "Unlock favorites, price estimation, and no ads", "Débloquer favoris, estimation des prix et sans pub"),
    ("filter_itinerary", "Itinerary", "Itinéraire"),
    ("filter_search_radius", "Search radius: %1$d m", "Rayon de recherche : %1$d m"),
    ("filter_only_highway", "Only highway stations", "Stations autoroute uniquement"),
    ("filter_only_highway_subtitle", "Filter results to stations on highways", "Filtrer les résultats aux stations d'autoroute"),
    ("filter_selection_mode", "Selection mode", "Mode de sélection"),
    ("filter_auto_sources_hint", "Auto selects sources based on your current country. Your manual selection below remains as a fallback.", "Le mode auto choisit les sources selon votre pays. Votre sélection manuelle ci-dessous reste en secours."),
    ("filter_data_sources", "Data sources", "Sources de données"),
    ("filter_by_country", "Filter by country or region", "Filtrer par pays ou région"),
    ("filter_country_placeholder", "France, DE, global…", "France, DE, global…"),
    ("filter_no_country_match", "No country matches this filter.", "Aucun pays ne correspond à ce filtre."),
    ("filter_sources_on", "%1$d sources on — tap to turn all off", "%1$d sources actives — appuyer pour tout désactiver"),
    ("filter_sources_off", "%1$d sources off — tap to enable all", "%1$d sources inactives — appuyer pour tout activer"),
    ("filter_sources_partial", "%1$d / %2$d on — tap to enable all", "%1$d / %2$d actives — appuyer pour tout activer"),
    ("settings_map_engine", "Map engine", "Moteur de carte"),
    ("settings_map_theme", "Map theme", "Thème de carte"),
    ("settings_theme_night_maps", "Also applies to maps for night driving", "S'applique aussi aux cartes pour la conduite de nuit"),
    ("settings_api_keys_optional", "API keys (optional)", "Clés API (facultatif)"),
    ("settings_opencm_key", "OpenChargeMap API key", "Clé API OpenChargeMap"),
    ("settings_ecomovement_key", "Eco-Movement API key", "Clé API Eco-Movement"),
    ("settings_fuelprices_dk_key", "Fuelprices.dk API key", "Clé API Fuelprices.dk"),
    ("settings_nsw_key", "NSW FuelCheck API key", "Clé API NSW FuelCheck"),
    ("settings_nsw_secret", "NSW FuelCheck API secret", "Secret API NSW FuelCheck"),
    ("settings_clear_cache_title", "Clear Cache", "Vider le cache"),
    ("settings_clear_cache_message", "This will clear map markers, image caches, and debug logs. Continue?", "Cela effacera les marqueurs, le cache d'images et les journaux debug. Continuer ?"),
    ("settings_clear_error_log_title", "Clear Error Log", "Vider le journal d'erreurs"),
    ("settings_clear_error_log_message", "Are you sure you want to clear all recorded errors?", "Voulez-vous vraiment effacer toutes les erreurs enregistrées ?"),
    ("settings_no_errors", "No errors recorded", "Aucune erreur enregistrée"),
    ("settings_clear_logs", "Clear Logs", "Effacer les journaux"),
    ("settings_auth_unavailable", "Auth unavailable", "Authentification indisponible"),
    ("settings_toll_french_highway", "French highway toll estimation", "Estimation péages autoroute français"),
    ("settings_version_sources", "Version & data sources", "Version et sources de données"),
    ("settings_current_theme", "Current: %1$s", "Actuel : %1$s"),
    ("settings_map_mode", "Map Mode", "Mode carte"),
    ("settings_show_traffic", "Show Traffic", "Afficher le trafic"),
    ("settings_vehicle_summary", "%1$s, %2$d km", "%1$s, %2$d km"),
    ("emergency_call", "Call Emergency: %1$s", "Appeler les urgences : %1$s"),
    ("emergency_universal_number", "Universal number for this region", "Numéro universel pour cette région"),
    ("emergency_locating", "Locating you…", "Localisation en cours…"),
    ("emergency_locating_short", "Locating...", "Localisation..."),
    ("emergency_location_unavailable", "Location unavailable", "Position indisponible"),
    ("emergency_contacts_count", "%1$d local emergency contacts", "%1$d contacts d'urgence locaux"),
    ("emergency_call_cd", "Call %1$s", "Appeler %1$s"),
    ("emergency_location_clip", "Emergency location", "Position d'urgence"),
    ("emergency_refresh_location", "Refresh location", "Actualiser la position"),
    ("network_loading_coords", "Loading coordinates…", "Chargement des coordonnées…"),
    ("network_status", "Network Status", "État du réseau"),
    ("network_signal", "Signal: %1$s", "Signal : %1$s"),
    ("forecast_loading", "Loading…", "Chargement…"),
    ("forecast_unavailable", "Forecast unavailable", "Prévision indisponible"),
    ("forecast_fetching", "Fetching local prices and market data", "Récupération des prix locaux et des données marché"),
    ("forecast_no_rows", "No forecast rows yet (needs market data). Pull to refresh from header.", "Pas encore de prévisions (données marché requises). Tirez pour actualiser depuis l'en-tête."),
    ("forecast_next_days", "Next days", "Prochains jours"),
    ("forecast_market_signal", "Market signal", "Signal marché"),
    ("forecast_7day_accuracy", "7-day accuracy", "Précision sur 7 jours"),
    ("forecast_last_scored", "Last scored prediction", "Dernière prédiction notée"),
    ("forecast_note", "Note", "Note"),
    ("forecast_est_price", "Est. %1$s €/L", "Est. %1$s €/L"),
    ("cd_settings", "Settings", "Paramètres"),
    ("cd_history", "History", "Historique"),
    ("cd_favorites", "Favorites", "Favoris"),
    ("cd_navigation", "Navigation", "Navigation"),
    ("cd_refresh_map", "Refresh map", "Actualiser la carte"),
    ("cd_data_sources", "Data sources", "Sources de données"),
    ("cd_map_settings", "Map settings", "Paramètres carte"),
    ("cd_copy_error", "Copy error", "Copier l'erreur"),
    ("cd_route", "Route", "Itinéraire"),
    ("country_global", "Global", "Mondial"),
    ("country_europe", "Europe", "Europe"),
    ("country_portugal_azores", "Portugal (Azores)", "Portugal (Açores)"),
    ("country_portugal_madeira", "Portugal (Madeira)", "Portugal (Madère)"),
    ("country_spain_canary", "Spain (Canary Islands)", "Espagne (Canaries)"),
    ("country_spain_balearic", "Spain (Balearic Islands)", "Espagne (Baléares)"),
    ("template_ui_templates", "UI Templates", "Modèles UI"),
    ("template_map_nav", "Map & Nav Templates", "Modèles carte et nav"),
    ("template_app_features", "App Feature Samples", "Exemples de fonctionnalités"),
    ("template_app_features_header", "App Features", "Fonctionnalités"),
    ("template_lab_subtitle_ui", "Message, Pane, Grid, Search, SignIn, Tabs...", "Message, Pane, Grid, Search, SignIn, onglets..."),
    ("template_lab_subtitle_map", "NavigationTemplate, RoutePreview, PlaceList...", "NavigationTemplate, RoutePreview, PlaceList..."),
    ("template_lab_subtitle_features", "Native POI, Custom Map, Route Planning...", "POI natif, carte perso, planification..."),
    ("template_lab_current_mode", "Current mode: %1$s", "Mode actuel : %1$s"),
    ("template_message", "MessageTemplate", "MessageTemplate"),
    ("template_pane", "PaneTemplate", "PaneTemplate"),
    ("template_grid", "GridTemplate", "GridTemplate"),
    ("template_long_message", "LongMessageTemplate", "LongMessageTemplate"),
    ("template_search", "SearchTemplate", "SearchTemplate"),
    ("template_sign_in", "SignInTemplate", "SignInTemplate"),
    ("template_navigation", "NavigationTemplate", "NavigationTemplate"),
    ("template_route_preview_nav", "RoutePreviewNavigationTemplate", "RoutePreviewNavigationTemplate"),
    ("template_place_list_map", "PlaceListMapTemplate", "PlaceListMapTemplate"),
    ("template_place_list_nav", "PlaceListNavigationTemplate", "PlaceListNavigationTemplate"),
    ("template_tab", "TabTemplate", "TabTemplate"),
    ("template_error", "[%1$s] Template error", "Erreur de modèle [%1$s]"),
    ("template_primary_action", "Primary Action", "Action principale"),
    ("template_secondary", "Secondary", "Secondaire"),
    ("template_pane_row_1", "Pane Row 1", "Ligne volet 1"),
    ("template_pane_row_2", "Pane Row 2", "Ligne volet 2"),
    ("template_pane_row_3", "Pane Row 3", "Ligne volet 3"),
    ("template_pane_sample", "PaneTemplate Sample", "Exemple PaneTemplate"),
    ("template_grid_sample", "GridTemplate Sample", "Exemple GridTemplate"),
    ("template_list_sample", "ListTemplate Sample", "Exemple ListTemplate"),
    ("template_list_item", "List Item %1$d", "Élément %1$d"),
    ("template_list_item_desc", "Description for item %1$d", "Description de l'élément %1$d"),
    ("template_grid_item", "Item %1$d", "Élément %1$d"),
    ("template_message_sample", "MessageTemplate", "MessageTemplate"),
    ("template_long_message_sample", "LongMessage Sample", "Exemple LongMessage"),
    ("template_sign_in_sample", "SignInTemplate Sample", "Exemple SignInTemplate"),
    ("template_search_result", "Result for '%1$s'", "Résultat pour « %1$s »"),
    ("template_pane_info_1", "Additional information about this item.", "Informations supplémentaires sur cet élément."),
    ("template_pane_info_2", "More details here.", "Plus de détails ici."),
    ("template_pane_info_3", "Third line of information.", "Troisième ligne d'information."),
    ("template_place_eiffel", "Eiffel Tower", "Tour Eiffel"),
    ("template_place_louvre", "Louvre Museum", "Musée du Louvre"),
    ("template_place_list", "Place List", "Liste de lieux"),
    ("template_place_list_map_title", "PlaceListMapTemplate", "PlaceListMapTemplate"),
    ("template_route_25min", "25 min", "25 min"),
    ("template_route_30min", "30 min", "30 min"),
    ("template_selected_tab", "Selected Tab: %1$s", "Onglet sélectionné : %1$s"),
    ("template_sample_address", "Sample address", "Adresse exemple"),
    ("template_sample_name", "Sample", "Exemple"),
    ("range_km", "%1$d km", "%1$d km"),
    ("coords_format", "%1$.4f, %1$.4f", "%1$.4f, %1$.4f"),
    ("price_format", "€%1$.2f", "€%1$.2f"),
    ("power_kw_format", "%1$d kW", "%1$d kW"),
]

# Build reverse map: English literal -> key
EN_TO_KEY: dict[str, str] = {v: k for k, v, _ in STRINGS}
EN_TO_KEY.update({en: key for en, key in EXISTING.items()})

# Also map existing strings.xml entries
EXISTING_XML_KEYS = [
    "app_name", "hi_how_can_i_help", "searching_address", "planning_route",
    "search_mode_fuel", "search_mode_ev", "search_mode_my_car", "search_mode_other",
    "dashboard_routes", "dashboard_network", "amenity_toilets", "amenity_drinking_water",
    "amenity_camp_site", "amenity_caravan_site", "amenity_picnic_site", "amenity_truck_stop",
    "amenity_rest_area", "amenity_restaurant", "amenity_fast_food", "amenity_speed_camera",
    "amenity_parking", "amenity_viewpoint", "filters", "filters_with_count", "search_filters",
    "for_my_car", "navigate", "connectors_label", "no_fuel_price_details", "gas_station",
    "charging_station", "highway", "amenity_24h", "amenity_shop", "amenity_car_wash",
    "amenity_showers", "filter_section_favorites", "filter_chip_my_favorites",
    "filter_section_fuel_types", "filter_section_power_range", "filter_section_connectors",
    "filter_section_amenities", "vehicle_filters_active", "vehicle_energy_electric",
    "vehicle_energy_hybrid", "vehicle_energy_fuel", "no_vehicle_profile", "tap_to_configure",
    "disclaimer_title", "disclaimer_content", "disclaimer_accept", "about_view_disclaimer",
]


def escape_xml(s: str) -> str:
    return xml_escape.escape(s).replace("'", "\\'")


def read_existing_keys(path: Path) -> set[str]:
    if not path.exists():
        return set()
    return set(re.findall(r'name="([^"]+)"', path.read_text()))


def merge_strings_xml():
    existing_keys = read_existing_keys(VALUES)
    # Read current default strings for EN values of existing keys
    existing_en = dict(re.findall(r'<string name="([^"]+)">([^<]*)</string>', VALUES.read_text()))
    lines_en = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    lines_fr = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']

    # Keep existing entries first (from current file)
    for key in sorted(existing_keys):
        if key in ("points_count", "available_count"):
            continue
        en = existing_en.get(key, "")
        lines_en.append(f'    <string name="{key}">{escape_xml(en)}</string>')

    fr_text = {}
    if VALUES_FR.exists():
        fr_text = dict(re.findall(r'<string name="([^"]+)">([^<]*)</string>', VALUES_FR.read_text()))

    new_keys = []
    for key, en, fr in STRINGS:
        if key in existing_keys:
            continue
        new_keys.append((key, en, fr))
        lines_en.append(f'    <string name="{key}">{escape_xml(en)}</string>')

    # plurals from original
    plurals = re.search(r'<plurals.*?</plurals>', VALUES.read_text(), re.DOTALL)
    if plurals:
        for block in re.findall(r'<plurals.*?</plurals>', VALUES.read_text(), re.DOTALL):
            lines_en.append('    ' + block.replace('\n', '\n    ').strip())

    lines_en.append('</resources>')

    # FR file: existing FR + new
    existing_fr_keys = read_existing_keys(VALUES_FR)
    fr_all = dict(fr_text)
    for key, en, fr in STRINGS:
        fr_all[key] = fr

    for key in sorted(set(existing_keys) | {k for k, _, _ in STRINGS}):
        if key in ("points_count", "available_count"):
            continue
        fr = fr_all.get(key, existing_en.get(key, ""))
        lines_fr.append(f'    <string name="{key}">{escape_xml(fr)}</string>')

    for block in re.findall(r'<plurals.*?</plurals>', VALUES_FR.read_text(), re.DOTALL):
        lines_fr.append('    ' + block.replace('\n', '\n    ').strip())

    lines_fr.append('</resources>')

    VALUES.write_text('\n'.join(lines_en) + '\n')
    VALUES_FR.write_text('\n'.join(lines_fr) + '\n')
    print(f'Wrote {len(new_keys)} new keys to strings.xml')


if __name__ == '__main__':
    merge_strings_xml()
