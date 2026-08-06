# APIs de Données Autoroutières en France et Europe (Aires, Bornes de Secours, Prix des Péages)

Ce document répertorie les sources de données, les APIs (publiques, gouvernementales et commerciales) ainsi que les requêtes OpenStreetMap (Overpass) permettant de récupérer les informations sur le réseau autoroutier : les aires de services et de repos, les bornes de secours/d'appel d'urgence, et les tarifs des péages/segments autoroutiers.

---

## 1. Aires de Services & Stations-Services (Aires de repos, Carburants, Bornes de recharge)

Les aires d'autoroute se divisent en deux types principaux : les **aires de repos** (simples parkings avec sanitaires, tables de pique-nique) et les **aires de services** (boutiques, restauration, stations-services, bornes de recharge électrique).

### A. OpenStreetMap & Overpass API (Gratuit et Open Source)
OpenStreetMap est la source ouverte la plus complète et la plus dynamique pour cartographier les aires et leurs équipements.

* **Tags clés :**
  - `highway=services` : Aire de service complète (carburant, restauration, etc.).
  - `highway=rest_area` : Aire de repos simple.
  - `amenity=fuel` : Station-service.
  - `amenity=charging_station` : Borne de recharge pour véhicules électriques.

* **Exemple de requête Overpass QL (pour la France) :**
  Vous pouvez exécuter cette requête sur [Overpass Turbo](https://overpass-turbo.eu/) ou via le point d'accès API `https://overpass-api.de/api/interpreter`.
  ```overpass
  [out:json][timeout:90];
  // Récupère les aires de services et de repos en France
  area["ISO3166-1"="FR"]->.searchArea;
  (
    node["highway"="services"](area.searchArea);
    way["highway"="services"](area.searchArea);
    relation["highway"="services"](area.searchArea);
    node["highway"="rest_area"](area.searchArea);
    way["highway"="rest_area"](area.searchArea);
    relation["highway"="rest_area"](area.searchArea);
  );
  out body;
  >;
  out skel qt;
  ```

### B. APIs Officielles & Plateformes Nationales
* **Bison Futé (Données publiques de l'État) :**
  - **Description :** Bison Futé fournit des informations en temps réel sur l'état du réseau routier national (RRN) et les aires de services/repos via ses flux.
  - **Accès :** [Bison Futé - Accès aux données](https://www.bison-fute.gouv.fr/acces-aux-donnees.html).
* **Data.gouv.fr (Données Ouvertes de l'État Français) :**
  - **Bornes de recharge (IRVE) :** Le jeu de données officiel consolidé de toutes les bornes de recharge électrique en France (y compris sur autoroute) est disponible via l'API ODRE (Open Data Réseaux Énergies).
    - **URL API :** `https://odre.opendatasoft.com/api/explore/v2.1/catalog/datasets/bornes-irve`
  - **Aires de Covoiturage :** Base nationale des aires de covoiturage (souvent situées aux entrées/sorties de péage d'autoroutes).
    - **URL API :** `https://transport.data.gouv.fr/datasets/aires-de-covoiturage-en-france`
  - **Prix des Carburants :** API gouvernementale temps réel des prix des carburants (Gaston l'intègre déjà). Permet de filtrer par type de voie (les stations d'autoroute ont des prix spécifiques et des ID identifiables).
    - **URL API :** `https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2`

---

## 2. Bornes de Secours / Bornes d'Appel d'Urgence (BAU)

Les bornes d'appel d'urgence (oranges) le long des autoroutes permettent de contacter directement le PC de sécurité de la société concessionnaire d'autoroute (SCA) en cas de panne ou d'accident.

### A. OpenStreetMap & Overpass API (Géolocalisation précise)
La communauté OSM répertorie ces bornes de manière très précise.
* **Tag clé :** `emergency=phone` (remplace l'ancien tag obsolète `amenity=emergency_phone`).
* **Tags associés recommandés :**
  - `operator=*` (ex: `APRR`, `SANEF`, `ASF`)
  - `ref=*` (identifiant unique de la borne inscrit sur le poteau)
* **Requête Overpass QL pour localiser les bornes de secours :**
  ```overpass
  [out:json][timeout:120];
  area["ISO3166-1"="FR"]->.searchArea;
  (
    node["emergency"="phone"](area.searchArea);
  );
  out body;
  >;
  out skel qt;
  ```

### B. Application Numérique & PC de Sécurité (SOS Autoroute)
* **SOS Autoroute (APRR, AREA, SANEF, SAPN, ATMB, SFTRF, ADELAC, A'LIENOR, ATLANDES, CEVM) :**
  - **Fonctionnement :** C'est l'application officielle qui remplace l'utilisation physique de la borne d'appel d'urgence orange. Elle embarque un système de géolocalisation et de transmission de données de véhicule aux secours routiers du concessionnaire.
  - **API publique :** Il n'existe pas d'API REST publique pour déclencher des appels d'urgence (pour des raisons évidentes de sécurité et d'évitement de faux appels), mais l'intégration de la fonctionnalité passe par le renvoi de l'utilisateur vers l'application SOS Autoroute ou le numéro d'urgence européen **112** hors réseau concédé.

---

## 3. Tarifs et Segments de Péage (Prix des trajets autoroutiers)

Le calcul du tarif des péages en France est complexe car le réseau est divisé en concessions (Vinci, Sanef, APRR, etc.) fonctionnant en système "ouvert" (tarif forfaitaire par barrière de péage) ou "fermé" (tarif calculé selon la gare d'entrée et la gare de sortie).

### A. Jeux de Données Ouverts (Statiques & Géographiques)
* **Gares de péage du réseau routier national concédé (Ministère de la Transition écologique) :**
  - **Description :** Base de données contenant la géolocalisation de toutes les gares de péage en France, le nom de l'autoroute associée et le concessionnaire exploitant.
  - **Accès API/Téléchargement :** [Gares de péage sur Data.gouv.fr](https://www.data.gouv.fr/datasets/gares-de-peage-du-reseau-routier-national-concede)
* **Péages en Flux Libre (Free Flow) :**
  - **Description :** Base recensant les nouveaux portiques de péage en flux libre (sans barrière physique, ex: A79, A14), essentiels pour le calcul automatique et le paiement ultérieur.
  - **Accès :** [Autoroutes et péages en flux libre sur Data.gouv.fr](https://www.data.gouv.fr/datasets/autoroutes-et-peages-en-flux-libre)

### B. APIs de Calcul d'Itinéraire avec Calcul du Coût des Péages
Pour obtenir le tarif exact en temps réel d'un segment ou d'un trajet complet, il est recommandé d'utiliser des APIs d'itinéraires qui intègrent les matrices de tarifs de toutes les sociétés d'autoroute selon la classe du véhicule (Classe 1 : Voitures, Classe 2 : Utilitaires/Camping-cars, Classe 5 : Motos, etc.).

#### 1. IGN Géoplateforme (Calcul d'itinéraire - Public/Gratuit)
* **Description :** L'IGN propose un service de navigation et de calcul d'itinéraire officiel pour le territoire français. Sa configuration permet d'exposer les tronçons routiers et les calculs associés.
* **Documentation :** [Calcul d'itinéraire | cartes.gouv.fr](https://cartes.gouv.fr/aide/fr/guides-utilisateur/utiliser-les-services-de-la-geoplateforme/calcul-itineraire/)
* **URL de base :** `https://data.geopf.fr/navigation/`

#### 2. APIs Commerciales Spécialisées (Précises & Clé en main)
* **TollGuru API (Recommandé - Leader du secteur) :**
  - **Description :** Service mondial spécialisé dans le calcul du coût exact des péages, des tunnels, des ponts et du carburant pour tous types de véhicules (France et Europe incluses). Gère le système fermé/ouvert et le flux libre.
  - **Essai gratuit :** Fournit un niveau gratuit (Free tier) pour les développeurs.
  - **Site web :** [TollGuru](https://tollguru.com/)
* **Tollman / Toll Collector API :**
  - **Description :** Autre alternative dédiée exclusivement au calcul des tarifs de péages européens. Gère parfaitement les grilles tarifaires annuelles de l'ASFA en France.
* **APIs de Cartographie Professionnelle (HERE, TomTom, Google Maps) :**
  - **HERE Routing API (v8) :** L'un des calculateurs les plus robustes pour les véhicules professionnels (poids lourds, voitures). Fournit un détail extrêmement précis du coût du péage segment par segment (`tollSystem`, `tollRoad`).
  - **Google Maps Routes API (v2) :** Fournit désormais une estimation globale des tarifs des péages autoroutiers le long de l'itinéraire calculé.
  - **TomTom Routing API :** Calcule également le coût financier précis des péages européens en fonction des profils de véhicules.
