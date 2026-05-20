# Rapport : API pour les Stations d'Échange de Batterie (Swap Stations)

Ce rapport présente les options gratuites et publiques pour lister les stations d'échange de batterie (voitures et deux-roues) dans le cadre du projet Gaston.

## 1. OpenStreetMap (via Overpass API)
C'est la source **gratuite** la plus accessible et la mieux documentée pour une intégration immédiate.

*   **Type :** Open Data (ODbL).
*   **Couverture :** Mondiale (très forte à Taiwan pour Gogoro, en Europe pour NIO/Zeway).
*   **Accès technique :** Requête Overpass Turbo.
*   **Tags à utiliser :**
    *   `charging_station:battery_swapping=yes` (Le standard émergent).
    *   `amenity=charging_station` + `brand=NIO` (ou `Gogoro`, `Zeway`, `Ample`).
*   **Exemple de requête Overpass :**
    ```
    node["charging_station:battery_swapping"="yes"](around:5000, 48.8566, 2.3522);
    ```

## 2. OpenChargeMap (OCM)
Bien que principalement axé sur la recharge par câble, OCM commence à référencer certaines stations de swap.

*   **Type :** Open Data (CC BY 4.0).
*   **Couverture :** Mondiale.
*   **Accès technique :** API REST (`https://api.openchargemap.io/v3/poi`).
*   **Filtre recommandé :** Rechercher par nom d'opérateur ou de réseau (ex: "NIO", "Gogoro").

## 3. Protocoles Standards (OCPI 2.1 / 2.2.1)
Le protocole OCPI, utilisé par de nombreux agrégateurs de recharge, supporte officiellement le battery swapping depuis ses dernières versions.

*   **Potentiel :** Si Gaston intègre des flux OCPI (comme Eco-Movement ou DKV déjà présents), ces stations peuvent apparaître via l'attribut `facility` ou via des types de connecteurs spécifiques.
*   **Note technique :** Vérifier les champs `capabilities` dans les objets `Location` ou `EVSE` du flux OCPI.

## 4. Sources Propriétaires (Nécessitant une analyse de flux)
Il n'existe pas d'API "publique officielle" simple (sans clé) pour ces réseaux, mais leurs données sont accessibles via leurs propres cartes web :

*   **NIO (Voitures - Europe/Chine) :** Présent en Norvège, Allemagne, Pays-Bas. Les données peuvent être extraites de leur carte "Power Map".
*   **Gogoro (2-roues - Taiwan/Monde) :** Leader mondial. API disponible via leur application, souvent utilisée par des projets tiers sur GitHub.
*   **Zeway (2-roues - France/Espagne) :** Réseau en pleine expansion à Paris. Leurs stations sont souvent situées dans des stations-service (Esso, Total) et sont référencées sur leur site web.

## 5. Stratégie d'intégration technique dans Gaston

Pour intégrer ces données dans l'architecture actuelle de Gaston (KMP) :

### Étape A : Mise à jour des Modèles (`:shared`)
1.  **`PoiCategory`** : Ajouter `BatterySwap` à l'énumération dans `Poi.kt`.
2.  **`IrveDetails`** : Ajouter un champ booléen `isBatterySwap` ou étendre `connectorTypes` avec une valeur "swap".

### Étape B : Évolution du `OverpassProvider`
Le fournisseur Overpass est le candidat idéal car il est déjà configuré pour les requêtes multi-tags.
*   **Modification de `categoryToOsmAmenity`** : Associer `PoiCategory.BatterySwap` à une recherche générique sur `amenity=charging_station`.
*   **Ajout d'un filtre de tag personnalisé** : Dans `search`, si la catégorie est `BatterySwap`, ajouter dynamiquement le filtre `["charging_station:battery_swapping"="yes"]` ou `["battery_swap"="yes"]` à la requête Overpass.

### Étape C : Création d'un `BatterySwapProvider` dédié (Optionnel)
Si Gaston souhaite supporter des réseaux propriétaires (NIO, Zeway) de manière plus fiable que via OSM :
*   Créer un nouveau `PoiProvider` qui interroge directement les backends cartographiques de ces services (nécessite du reverse-engineering des requêtes web car les API officielles sont souvent fermées).

## Résumé des sources prêtes à l'emploi
| Source | URL / Point d'accès | Gratuité | Standard |
| :--- | :--- | :--- | :--- |
| **OSM (Overpass)** | `https://overpass-api.de/api/interpreter` | Oui | ODbL |
| **OpenChargeMap** | `https://api.openchargemap.io/v3/poi` | Oui | CC BY 4.0 |
| **NIO Map (Web)** | `https://www.nio.com/de_DE/power-map` | Non officielle | Propriétaire |
| **Zeway Map (Web)** | `https://www.zeway.com/fr/reseau` | Non officielle | Propriétaire |
