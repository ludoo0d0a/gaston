# Fonctionnement du Cache des Stations (POI)

Cette documentation détaille les mécanismes de mise en cache, les durées de validité (TTL) et les politiques d'éviction pour les stations et points d'intérêt (POI) dans l'application Gaston.

## 1. Architecture à deux niveaux

L'application utilise un système de cache hybride pour optimiser les performances et limiter les appels réseau :

### Cache en mémoire (`SelectorPoiProvider`)
- **Rôle :** Accès ultra-rapide aux données lors de la navigation active sur la carte.
- **Capacité :** Limité à environ **1200 stations** et **12 zones géographiques** (`LoadedPoiRegion`).
- **Gestion :** Utilise une politique de proximité (les stations les plus éloignées du centre de la carte sont supprimées en premier lors du nettoyage).

### Cache sur disque (Room Database)
- **Table :** `poi_cache`
- **Rôle :** Persistance des données entre les sessions de l'application.
- **Stockage :** Les POI sont stockés sous forme JSON pour conserver l'intégralité des détails fournis par les différents providers.

## 2. Durées de validité (Time To Live - TTL)

Les durées de validité sont différenciées selon la volatilité des données (définies dans `PoiFetchCache.kt`) :

| Catégorie | TTL | Description |
|-----------|-----|-------------|
| **Énergie** | 12 heures | Stations de carburant (Gas) et bornes de recharge (Irve). Concerne les prix et la disponibilité. |
| **Équipements** | 3 jours | Points d'intérêt stables : toilettes, points d'eau, parkings, restaurants, etc. |
| **Rétention Disque**| 3 jours | Durée maximale de conservation de n'importe quel POI en base de données. |
| **Historique Prix** | 30 jours | Données utilisées pour le calcul de l'indice de fiabilité des prix. |

## 3. Logique de couverture géographique

Le cache ne fonctionne pas seulement par station individuelle, mais par **régions chargées** (`LoadedPoiRegion`) :

1. **Vérification de couverture :** Avant chaque recherche, l'application vérifie si la zone demandée (centre + rayon) est entièrement incluse dans une région déjà présente en cache.
2. **Fraîcheur par catégorie :** Une région peut être géographiquement couverte mais avoir des données périmées pour une catégorie spécifique (ex: les prix des carburants ont plus de 12h, mais les restaurants sont toujours valides).
3. **Chargement incrémental :** Si une zone est couverte géographiquement mais qu'il manque certains types de données ou que certaines sont périmées, l'application n'interroge les providers que pour les données manquantes.

## 4. Invalidation et Nettoyage

- **Changement de réglages :** Le cache géographique est immédiatement vidé si l'utilisateur modifie ses sources de données (providers) pour éviter d'afficher des résultats incohérents.
- **Nettoyage automatique :**
    - Une tâche de fond s'exécute toutes les heures pour supprimer les entrées périmées de la base de données.
    - Lors de l'ajout de nouvelles stations en mémoire, un élagage (`trim`) est effectué pour respecter les limites de capacité.

## 5. Classes clés (Code source)

- `SelectorPoiProvider.kt` : Logique principale de coordination du cache.
- `PoiFetchCache.kt` : Définition des constantes TTL et fonctions de calcul de couverture.
- `PoiCacheDao.kt` / `PoiCacheEntity.kt` : Interface et modèle pour le stockage persistant Room.
