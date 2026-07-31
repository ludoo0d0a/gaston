# Guide des API de Parkings Gratuites et Temps Réel (Europe)

Ce document liste et décrit les API publiques, gratuites et en temps réel identifiées pour lister les parkings et enrichir les recherches avec un maximum d'informations en Europe, avec un focus prioritaire sur la France et le Luxembourg, suivi de l'Espagne, du Portugal, de l'Italie, de la Norvège, et des pays d'Europe centrale (Allemagne, Suisse, Danemark).

Toutes ces API évitent les services payants ou les freemiums à quota restrictif. Elles s'appuient sur l'open data officiel national, régional et municipal.

---

## 1. France

La France dispose d'un éco-système Open Data très riche, structuré au niveau national par la Loi d'Orientation des Mobilités (LOM) et mis en œuvre localement par les métropoles.

### A. Base Nationale Consolidée des Lieux de Stationnement (BNLS - National)
* **Portail** : `transport.data.gouv.fr` et `data.gouv.fr`
* **Type de données** : Statique et métadonnées d'infrastructure (tarification complète, recharge électrique, PMR).
* **Format** : Base CSV consolidée selon le schéma national `schema-stationnement` (Etalab).
* **Endpoint de téléchargement** : `https://transport.data.gouv.fr/datasets/base-nationale-des-lieux-de-stationnement`
* **Informations clés fournies** :
  * `capacite` : Capacité totale du parking.
  * `places_recharge_elec` : **Nombre de places équipées de bornes de recharge pour véhicules électriques**.
  * `places_pmr` : Nombre de places réservées PMR (handicapé).
  * Grilles tarifaires détaillées : `tarif_1h`, `tarif_2h`, `tarif_3h`, `tarif_4h`, `tarif_24h`, `tarif_pmr`.
  * `hauteur_max` : Hauteur maximale autorisée en mètres.
  * `horaires_ouverture` : Plages horaires de fonctionnement.

### B. Paris & Île-de-France (SAEMES - Concessionnaire Majeur)
* **Portail** : Saemes OpenData (moteur Opendatasoft)
* **Type de données** : **Temps Réel (rafraîchi toutes les 1 à 2 minutes)**.
* **Endpoint de l'API** :
  `https://opendata.saemes.fr/api/explore/v2.1/catalog/datasets/places-disponibles-parkings-saemes/records`
* **Paramètres utiles** : `limit` (nombre de résultats), `where` (requêtes géographiques ou textuelles via ODSQL).
* **Informations clés fournies** :
  * `nom` : Nom du parking.
  * `coordonnees` : Latitude et Longitude.
  * `dp_place_disponible` : Places libres globales (voitures standard).
  * `dp_nb_places_dispo_electric` : **Nombre de places de recharge électrique actuellement libres**.
  * `dp_nb_places_dispo_pmr` : Places handicapés libres.
  * `dp_nb_places_dispo_moto` : Places moto libres.
  * `id` : Identifiant unique.

### C. Métropole de Lyon (Grand Lyon)
* **Portail** : Data Grand Lyon (Opendatasoft)
* **Type de données** : Temps réel.
* **Endpoint de l'API** :
  `https://data.grandlyon.com/api/explore/v2.1/catalog/datasets/lpa_mobilite.donnees_temps_reel/records`
* **Informations clés fournies** :
  * `nom` : Nom du parking.
  * `commune` : Ville (ex: Lyon 2e).
  * `capacite` : Nombre de places totales.
  * `places_libres` : Nombre de places actuellement disponibles.
  * `etat` : État d'ouverture ("OUVERT", "FERME", "COMPLET").

### D. Métropole de Bordeaux
* **Portail** : Bordeaux Métropole Open Data (Opendatasoft)
* **Type de données** : Temps réel.
* **Endpoint de l'API** :
  `https://data.bordeaux-metropole.fr/api/explore/v2.1/catalog/datasets/st_park_p/records`
* **Informations clés fournies** :
  * `nom` : Nom du parking.
  * `total` : Places totales.
  * `libre` : Places libres en temps réel.
  * `places_elec` : **Nombre de places électriques**.
  * `places_pmr` : Nombre de places PMR.
  * `tarifs` : Informations tarifaires (ex: Parcub, tarifs horaires).

### E. Métropole de Nantes (Naolib)
* **Portail** : Nantes Métropole Open Data (Opendatasoft)
* **Type de données** : Temps réel.
* **Endpoint de l'API** :
  `https://data.nantesmetropole.fr/api/explore/v2.1/catalog/datasets/244400404_parkings-publics-nantes-disponibilites/records`
* **Informations clés fournies** :
  * `grp_nom` : Nom du parking.
  * `grp_exploitation` : Nombre de places occupées ou disponibles en temps réel.
  * `grp_capacite` : Capacité maximale.
  * `grp_disponible` : Nombre de places libres en direct.
  * `grp_statut` : Statut d'accès (1 = Ouvert, 2 = Complet, 5 = Fermé).

### F. Métropole Européenne de Lille (MEL)
* **Portail** : MEL Open Data (Opendatasoft)
* **Type de données** : Temps réel.
* **Endpoint de l'API** :
  `https://opendata.lillemetropole.fr/api/explore/v2.1/catalog/datasets/disponibilite-temps-reel-des-parkings-mel/records`
* **Informations clés fournies** :
  * `nom` : Nom de l'ouvrage.
  * `etat` : Statut ("OUVERT", "COMPLET", etc.).
  * `places_dispo` : Places disponibles.
  * `places_totales` : Capacité totale.

### G. Métropole d'Aix-Marseille-Provence
* **Portail** : AMP Open Data (Opendatasoft)
* **Type de données** : Temps réel.
* **Endpoint de l'API** :
  `https://data.ampmetropole.fr/api/explore/v2.1/catalog/datasets/disponibilites-des-places-de-parkings/records`
* **Informations clés fournies** :
  * `nom` : Nom du parking.
  * `disponibilite` : Nombre de places disponibles.
  * `capacite` : Capacité totale de l'ouvrage.
  * `latitude` / `longitude` : Géolocalisation.

---

## 2. Luxembourg

Le Luxembourg propose une API temps réel centralisée et exhaustive couvrant l'ensemble des parkings du pays.

### A. Transport for Luxembourg (TfL API)
* **Portail** : Portail Open Data du Gouvernement du Luxembourg
* **Type de données** : **Temps Réel officiel, mis à jour toutes les minutes**.
* **Endpoint de l'API** :
  `https://api.tfl.lu/v1/Occupancy/CarPark`
* **Format** : GeoJSON (les caractéristiques sont imbriquées dans l'objet `properties` de chaque feature).
* **Informations clés fournies** :
  * `id` : Identifiant unique (ex: `vdl:22`).
  * `name` : Nom (ex: `Glacis`).
  * `total` : Capacité totale.
  * `free` : **Places actuellement libres**.
  * `trend` : Tendance de l'occupation ("up", "down", "stable").
  * `meta.open` : Statut d'ouverture (booléen).
  * `meta.address` : Adresse complète (`street`, `exit`).
  * `meta.reserved_for_disabled` : Places réservées PMR.
  * `meta.reserved_for_women` : Places réservées femmes.
  * `meta.motorbike_lots` : Places réservées motos.
  * `meta.bicycle_docks` : Docks pour vélos.
  * `meta.payment_methods` : Modes de paiement acceptés (`cash`, `visa`, `mastercard`, `call2park`, etc.).

---

## 3. Espagne

L'Espagne s'appuie sur des portails municipaux riches avec des flux JSON de haute qualité.

### A. Madrid (Ayuntamiento de Madrid)
* **Portail** : Portal de Datos Abiertos de Madrid
* **Type de données** : Temps réel et métadonnées d'infrastructure.
* **Endpoint d'occupation Temps Réel** :
  `https://datos.madrid.es/egob/catalogo/50027-0-aparcamientosocupacionyservicios.json`
* **Endpoint de l'annuaire statique (pour les coordonnées et fiches)** :
  `https://datos.madrid.es/egob/catalogo/300110-0-aparcamientos-publicos.json`
* **Informations clés fournies** :
  * Nom de l'établissement et localisation géospatiale.
  * `plazas` : Capacité totale du parking.
  * `plazasDisponibles` : Places libres actuelles.
  * `plazasRechargeElectrica` : **Places de recharge pour voitures électriques**.
  * `plazasPMR` : Places réservées PMR.
  * Tarifs moyens et horaires de service.

---

## 4. Portugal

Le Portugal centralise sa mobilité urbaine majeure sur Lisbonne et Porto.

### A. Lisbonne (EMEL - Empresa de Mobilidade de Lisboa)
* **Portail** : EMEL Data Platform (moteur CKAN / Open Data)
* **Type de données** : Temps réel.
* **Endpoint de l'API** :
  `https://dados.emel.pt`
  *(Possibilité de lister et requêter via l'API CKAN native : `api/3/action/datastore_search`)*
* **Informations clés fournies** :
  * Nom et géolocalisation des parcs fermés d'EMEL.
  * Capacité totale installée.
  * Ocupation temps réel (nombre de voitures actuellement garées).
  * Présence de chargeurs de véhicules électriques intégrés via le réseau EMEL/Mobi.E.

---

## 5. Italie

L'Italie propose des solutions régionales et municipales robustes accessibles par API.

### A. Milan (Comune di Milano)
* **Portail** : Open Data Milano
* **Type de données** : Temps Réel.
* **Endpoint de l'API** :
  `https://dati.comune.milano.it/api/v1/coop/parcheggi/struttura`
* **Informations clés fournies** :
  * Identifiant et nom du parking.
  * Places maximales autorisées.
  * Places libres en temps réel (actualisation toutes les 5 à 10 minutes).
  * Coordonnées WGS84.

### B. Florence (Comune di Firenze)
* **Portail** : Open Data Firenze
* **Type de données** : Temps Réel.
* **Endpoint de l'API** :
  `https://opendata.comune.fi.it/api/v1/parcheggi/disponibilita`
* **Informations clés fournies** :
  * Nom du parking (ex: "Stazione SMN").
  * Places libres actuelles.
  * Statut d'occupation.

---

## 6. Norvège

La Norvège, leader de la transition électrique, intègre les informations de parking dans sa plateforme nationale unifiée de transport.

### A. Norvège Nationale (Entur GraphQL National Transport Platform)
* **Portail** : Entur API
* **Type de données** : Temps réel et statique (National).
* **Endpoint GraphQL de l'API** :
  `https://api.entur.io/journey-planner/v3/graphql`
* **Exemple de requête GraphQL** :
  ```graphql
  {
    parkings(latitude: 59.91, longitude: 10.75, radius: 2000) {
      id
      name
      geometry { coordinates }
      totalCapacity
      realtimeOccupancy {
        vacantSpaces
        status
      }
      chargingSpaces # Bornes électriques !
      disabledSpaces # Places PMR
      priceInfo
    }
  }
  ```
* **Informations clés fournies** :
  * Nom, géométrie exacte.
  * Capacité totale (`totalCapacity`).
  * `realtimeOccupancy.vacantSpaces` : Places libres en direct (provenant du standard DATEX II).
  * `chargingSpaces` : **Nombre exact de places de recharge électrique** !
  * `disabledSpaces` : Places handicapés.
  * Informations de prix (`priceInfo`).

---

## 7. Allemagne, Suisse & Danemark

Ces pays sont déjà intégrés dans l'application Gaston via l'API **ParkAPI**, un agrégateur temps réel open-source performant.

### A. ParkAPI (parkendd.de)
* **Portail** : Offenes Dresden / ParkAPI
* **Type de données** : Temps réel officiel.
* **Endpoint de l'API** :
  `https://api.parkendd.de/{city_slug}`
* **Villes majeures supportées (active_support: true)** :
  * **Allemagne** : Dresden, Hamburg, Freiburg, Heidelberg, Karlsruhe, Nürnberg, Ulm, Wiesbaden, Ingolstadt, Kaiserslautern.
  * **Suisse** : Zürich, Basel.
  * **Danemark** : Aarhus.
* **Informations clés fournies** :
  * `free` : Places disponibles.
  * `total` : Capacité totale.
  * `state` : Statut ("open", "closed").
  * `coords` : Coordonnées.
  * Nom, adresse de l'ouvrage.
