# Guide des Sources Open Data Routières, Autoroutières et Trafic

Ce document recense de manière exhaustive les sources de données ouvertes (Open Data) relatives aux routes, autoroutes, au trafic en temps réel, aux comptages routiers et aux événements de circulation en France, en mettant un accent particulier sur le catalogue du **Cerema** (AVATAR/Trafic Routier) et d'autres grands portails nationaux.

---

## 1. Normes, Standards et Protocoles d'Échange

Afin d'exploiter efficacement les données de trafic routier, il convient de comprendre les principaux standards utilisés par les gestionnaires routiers en France et en Europe.

### A. DATEX II (Norme Européenne)
*   **Description :** Norme européenne (CEN/TS 16157) standardisant les échanges de données de trafic en temps réel (événements, chantiers, bouchons, temps de parcours, capteurs).
*   **Format :** Historiquement XML, de plus en plus disponible en JSON.
*   **Accès :** Gratuit, généralement ouvert via des flux de publication nationaux ou régionaux.

### B. SIREDO
*   **Description :** Système Informatique de Recueil de Données national pour le réseau routier national (RRN) géré par l'État. Il standardise les informations issues des stations de comptage physiques (boucles électromagnétiques, radars).
*   **Indicateurs principaux :**
    *   **Débit (Q) :** Nombre de véhicules par unité de temps.
    *   **Taux d'occupation (T) :** Pourcentage de temps durant lequel un capteur est occupé par un véhicule.
    *   **Vitesse moyenne (V) :** Vitesse moyenne des flux de circulation.

### C. TMJA (Trafic Moyen Journalier Annuel)
*   **Description :** Indicateur de trafic moyen sur 24 heures calculé sur une année complète, essentiel pour l'analyse de l'usure des routes et la planification des infrastructures.

---

## 2. Données Nationales, Autoroutes & Voies Rapides

### A. Bison Futé (Ministère de la Transition Écologique)
*   **Données fournies :** Événements de trafic en temps réel (accidents, pannes, obstacles, bouchons, chantiers), temps de parcours, états des routes, prévisions de trafic.
*   **Type d'accès :** API publique gratuite sans authentification.
*   **URL API / Flux :** Disponible sur le portail [data.gouv.fr](https://www.data.gouv.fr/fr/datasets/flux-datex-bison-fute/).
*   **Limitation / Quotas :** Pas de clé requise. Pas de limite stricte documentée, mais mise en cache recommandée (rafraîchissement toutes les 5 à 10 minutes).
*   **Licence :** Licence Ouverte d'Etalab (LO 2.0).

### B. Sytadin (Direction des Routes Île-de-France - DIR-IF)
*   **Données fournies :** Trafic en temps réel en Île-de-France, vitesses moyennes par segment, temps de parcours calculés, fermetures de voies, événements de circulation.
*   **Type d'accès :** Gratuit. Flux XML/JSON Datex II.
*   **URL API / Flux :** [Sytadin API](http://www.sytadin.fr/) (ou via le portail open data régional d'Île-de-France).
*   **Limitation / Quotas :** Pas de clé requise pour les flux de base. Re-requêtage limité à toutes les 2-3 minutes.

### C. Réseau Routier National (TMJA & Caractéristiques Géographiques)
*   **Données fournies :** Données de trafic moyen annuel (TMJA) avec répartition PL (Poids Lourds) sur l'ensemble du réseau routier national (RRN).
*   **Type d'accès :** Téléchargement de fichiers géographiques (Shapefiles, GeoJSON) ou d'API géographiques (WMS/WFS).
*   **URL API / Flux :** [data.gouv.fr - Trafic moyen journalier annuel sur le réseau routier national](https://www.data.gouv.fr/fr/datasets/trafic-moyen-journalier-annuel-sur-le-reseau-routier-national/).
*   **Limitation / Quotas :** Aucune limitation, accès direct gratuit sans clé.

### D. Concessionnaires Autoroutiers (Vinci Autoroutes, APRR, Sanef)
*   **Données fournies :** Tarifs de péage, tracés des segments, aires de services et de repos, disponibilité des bornes de recharge ultra-rapides, données de trafic temps réel (via flux Datex II ou API dédiées).
*   **Type d'accès :**
    *   **Tarifs et segments :** API d'estimation de prix de péage via des structures de données consolidées ou des portails open data régionaux.
    *   **Trafic et aires :** Données en libre accès sur data.gouv.fr ou via les portails open data des groupes concessionnaires.
*   **Limitations :** Gratuit, pas de clé d'API requise pour la majorité des jeux de données ouverts.

---

## 3. Index Détaillé des Sources Open Data Régionales & Locales (Catalogue Cerema)

Cet index référence de façon exhaustive les sources d'accès aux données de trafic routier présentées dans le réseau de recensement du **Cerema** (Centre d'études et d'expertise sur les risques, l'environnement, la mobilité et l'aménagement).

| Gestionnaire / Source | Couverture Géographique | Type de Données | URL API ou Portail Open Data | Clé API requise ? | Limitations / Quotas | Licence |
|---|---|---|---|---|---|---|
| **Métropole de Strasbourg / Eurométropole** | Strasbourg (67) | Flux de trafic temps réel, capteurs SIRAC, événements routiers | [data.strasbourg.eu](https://data.strasbourg.eu/explore/dataset/sirac_flux_trafic/) | **Non** | Utilisation libre avec mise en cache recommandée. | LO 2.0 |
| **Communauté Urbaine de Dunkerque** | Dunkerque (59) | Comptages routiers, débits horaires, points de mesure | [opendata.communaute-urbaine-dunkerque.fr](https://opendata.communaute-urbaine-dunkerque.fr/) | **Non** | Téléchargement bulk & API Opendatasoft (limites standards de la plateforme). | LO 2.0 |
| **Conseil Départemental de l'Oise** | Oise (60) | Trafic routier départemental, comptages annuels, TMJA | [Portail Open Data de l'Oise](https://www.oise.fr/) / [data.gouv.fr](https://www.data.gouv.fr/) | **Non** | Accès libre aux fichiers géographiques. | LO 2.0 |
| **Conseil Départemental de Saône-et-Loire** | Saône-et-Loire (71) | Comptages de trafic, vitesses de circulation, TMJA | [opendata.saoneetloire71.fr](https://opendata.saoneetloire71.fr/) | **Non** | API REST Opendatasoft publique. | LO 2.0 |
| **Conseil Départemental de Maine-et-Loire** | Maine-et-Loire (49) | Comptage routier permanent et temporaire, TMJA | [opendata.maine-et-loire.fr](https://opendata.maine-et-loire.fr/) | **Non** | API REST libre d'accès. | LO 2.0 |
| **Conseil Départemental des Hauts-de-Seine** | Hauts-de-Seine (92) | Débits de trafic en temps réel, capteurs physiques, TMJA | [opendata.hauts-de-seine.fr](https://opendata.hauts-de-seine.fr/) | **Non** | Requêtes API Opendatasoft temps réel autorisées. | LO 2.0 |
| **Conseil Départemental de Haute-Garonne** | Haute-Garonne (31) | Points de comptage routier, statistiques annuelles, TMJA | [data.haute-garonne.fr](https://data.haute-garonne.fr/) | **Non** | API REST libre d'accès. | LO 2.0 |
| **Conseil Départemental d'Eure-et-Loir** | Eure-et-Loir (28) | Trafic routier par section, comptages routiers | [data.gouv.fr](https://www.data.gouv.fr/fr/datasets/comptages-routiers-en-eure-et-loir/) | **Non** | Pas de restriction. | LO 2.0 |
| **Conseil Départemental des Alpes de Haute-Provence** | Alpes de Haute-Provence (04) | Données géographiques de trafic routier, TMJA | [Portail Géographique Départemental](https://www.alpes-de-haute-provence.gouv.fr/) | **Non** | Accès libre aux jeux de données Shapefile/GeoJSON. | LO 2.0 |
| **Conseil Départemental du Var** | Var (83) | Sections de trafic, comptages routiers, TMJA | [Var Open Data](https://data.gouv.fr/) | **Non** | Fichiers SIG et CSV en libre téléchargement. | LO 2.0 |
| **DREAL Poitou-Charentes** | ex Poitou-Charentes (16, 17, 79, 86) | Trafic routier des routes nationales de la région, TMJA | [Portail DREAL Nouvelle-Aquitaine](http://www.nouvelle-aquitaine.developpement-durable.gouv.fr/) / [data.gouv.fr](https://www.data.gouv.fr/) | **Non** | Téléchargement libre. | LO 2.0 |
| **DIR Centre-Est** | Centre-Est de la France | Événements de trafic en temps réel, chantiers, bouchons (Datex II) | [Portail d'information routière de l'État](https://www.data.gouv.fr/) | **Non** | Flux de données temps réel sans quota d'accès. | LO 2.0 |
| **Grand Lyon Métropole** | Lyon (69) | Trafic temps réel, vitesses moyennes, taux d'occupation, événements | [data.grandlyon.com](https://data.grandlyon.com/) | **Optionnelle** | Clé gratuite requise pour l'utilisation intensive des API temps réel de production. | Licence Ouverte Lyon (compatible LO 2.0) |
| **Bordeaux Métropole** | Bordeaux (33) | Capteurs de trafic, débits de circulation, comptages aux heures de pointe | [data.bordeaux-metropole.fr](https://data.bordeaux-metropole.fr/) | **Optionnelle** | Clé gratuite requise pour l'exploitation intensive des services web SIG et WFS. | LO 2.0 |
| **Montpellier Méditerranée Métropole** | Montpellier (34) | Mesures physiques de trafic routier, éco-compteurs, temps réel | [data.montpellier3m.fr](https://data.montpellier3m.fr/) | **Non** | API REST Opendatasoft en libre accès. | LO 2.0 |
| **Métropole Européenne de Lille (MEL)** | Lille (59) | Temps de parcours, état du trafic routier, congestion routière | [opendata.lillemetropole.fr](https://opendata.lillemetropole.fr/) | **Non** | API REST Opendatasoft publique. | LO 2.0 |
| **Toulouse Métropole** | Toulouse (31) | Indices de fluidité du trafic routier, capteurs temps réel | [data.toulouse-metropole.fr](https://data.toulouse-metropole.fr/) | **Non** | API temps réel publique sans restriction. | LO 2.0 |
| **Open Data Loire-Atlantique** | Loire-Atlantique (44) | Comptages routiers, données géographiques, TMJA | [data.loire-atlantique.fr](https://data.loire-atlantique.fr/) | **Non** | Téléchargement bulk ou API géographiques libres. | LO 2.0 |
| **Open Data Paris** | Paris (75) | Débits et taux d'occupation en temps réel, capteurs sous chaussée | [opendata.paris.fr](https://opendata.paris.fr/explore/dataset/comptages-routiers-permanents/) | **Non** | API REST temps réel en accès libre. | LO 2.0 / ODBL |
| **Open Data des Côtes d'Armor** | Côtes d'Armor (22) | Points de comptage routier, trafic routier, TMJA | [opendata.cotesdarmor.fr](https://opendata.cotesdarmor.fr/) | **Non** | Accès libre. | LO 2.0 |
| **Région Bretagne** | Bretagne | Logiciel IRIS (données de trafic et de vitesses de circulation) | [data.bretagne.bzh](https://data.bretagne.bzh/) | **Non** | Téléchargement libre et outils d'exploitation intégrés. | LO 2.0 |
| **Ville d'Agen** | Agen (47) | Comptages routiers locaux, débits de circulation | [data.gouv.fr](https://www.data.gouv.fr/) | **Non** | Téléchargement libre des CSV. | LO 2.0 |
| **Ville d'Angers / Angers Loire Métropole** | Angers (49) | Flux de trafic temps réel, boucles électromagnétiques | [data.angers.fr](https://data.angers.fr/) | **Non** | API REST Opendatasoft libre. | LO 2.0 |
| **Ville d'Issy-les-Moulineaux** | Issy-les-Moulineaux (92) | Capteurs de trafic, stationnement et fluidité routière | [data.issy.com](https://data.issy.com/) | **Non** | API REST libre. | LO 2.0 / ODBL |

---

## 4. Synthèse et Recommandations d'Intégration API

### A. Gratuité et Clés API
95 % des sources listées par le Cerema sont **totalement gratuites et ne nécessitent aucune clé d'API routière**. Pour les grandes métropoles (ex. Grand Lyon, Bordeaux Métropole), une création de compte gratuite est parfois demandée pour s'authentifier lors de requêtes à haut débit (afin d'éviter les attaques par déni de service).

### B. Limitations de Vitesse & Quotas
*   **Plateforme Opendatasoft (Strasbourg, Lille, Montpellier, etc.) :** Utilise un système de limitations dynamique (en général 10 000 à 50 000 requêtes gratuites par jour et par adresse IP).
*   **Recommandation de mise en cache :** Pour les données temps réel (flux de trafic SIRAC ou débits de boucles), un intervalle de rafraîchissement de **5 minutes** est optimal. Requêter à une fréquence supérieure surcharge les serveurs publics pour aucun gain d'information réel.

### C. Choix de Licence (Rappel Juridique)
*   La grande majorité de ces bases de données routières sont publiées sous la **Licence Ouverte (LO 2.0)** d'Etalab (conçue par l'État français). Elle vous autorise à reproduire, copier, modifier, adapter, distribuer et exploiter commercialement les données à la seule condition d'en mentionner la paternité (par exemple : *"Source : Données de trafic de la Métropole de Strasbourg - Open Data"*).
*   Certaines villes (comme Paris ou Issy-les-Moulineaux) utilisent parfois la licence **ODbL (Open Database License)**. Cette dernière vous oblige à repartager vos bases de données dérivées sous la même licence ODbL.
