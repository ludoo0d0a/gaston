# Comparatif MapLibre Native Android : Rendu par Tuiles vs Rendu "Sans Tuile" via GeoJSON

Ce document propose une analyse comparative approfondie concernant l'utilisation de **MapLibre Native Android** dans l'application Gaston, en confrontant le paradigme traditionnel de rendu par **tuiles** (vectorielles ou raster) à une alternative **"sans tuile" basée à 100% sur du GeoJSON**.

---

## 1. Version de MapLibre dans l'Application Gaston

L'application Gaston utilise actuellement la version **13.3.0** du SDK MapLibre Native pour Android :
* **Dépendance Gradle :** `org.maplibre.gl:android-sdk:13.3.0` (définie via `maplibre-android` dans `gradle/libs.versions.toml`).
* **Usage actuel dans l'application :**
  * Un fond de carte basé sur des **tuiles vectorielles distantes** via un style OpenFreeMap (chargé dans `VectorMapScreen.kt` et `CarMapLibreRenderer.kt`).
  * Les couches de données métier (stations POI, tracés d'itinéraires et rayons de recherche) sont déjà superposées localement à l'aide de sources **GeoJSON** dynamiques via la classe d'assistance `MapLibreSharedHelper.kt` (en utilisant `GeoJsonSource`, `SymbolLayer` et `LineLayer`).

---

## 2. Qu'est-ce qu'une carte "sans tuile" via GeoJSON ?

Dans un rendu cartographique classique, le monde est découpé en une grille de tuiles (images raster `.png`/`.jpg` ou vecteurs binaires `.pbf`/`.mvt`) à différents niveaux de zoom.

Une approche **"sans tuile" (tile-free)** avec GeoJSON consiste à injecter directement une structure géométrique complète (points, lignes, polygones, frontières) via un ou plusieurs fichiers ou flux GeoJSON locaux ou distants dans le moteur MapLibre, sans s'appuyer sur une infrastructure de serveur de tuiles pour le fond de carte.

---

## 3. Tableau Comparatif : Tuiles vs GeoJSON (MapLibre Android 13.3.0)

| Critère | Rendu classique par Tuiles (Vectorielles / Raster) | Rendu "Sans Tuile" via GeoJSON |
| :--- | :--- | :--- |
| **Mécanisme de chargement** | Découpage géographique en grilles (tuiles). Chargement à la demande uniquement pour la zone visible de l'écran. | Chargement en mémoire de l'intégralité du jeu de données géographiques (ex: pays entier, réseau routier complet). |
| **Efficacité Réseau** | **Excellente (dynamique) :** Seules les données de la zone affichée à l'écran et au niveau de zoom actuel sont téléchargées. | **Médiocre au départ, excellente ensuite :** Nécessite le téléchargement initial d'un fichier GeoJSON lourd, mais ne consomme plus rien par la suite. |
| **Performance d'affichage (FPS)** | **Optimisée & Stable :** Le moteur MapLibre est conçu pour n'afficher et ne décoder que les géométries indexées dans les dalles visibles. | **Dégressive selon la taille :** Excellente pour de petits jeux de données. Chute drastique des FPS si le fichier contient des millions de nœuds (ex: routes mondiales). |
| **Consommation Mémoire (RAM)** | **Contrôlée :** Système de cache LRU natif sur les dalles visibles. Libération automatique des dalles hors écran. | **Élevée & Linéaire :** Toutes les géométries et attributs du GeoJSON doivent résider en mémoire RAM pour être projetés et dessinés par le GPU. |
| **Fonctionnement Hors-Ligne** | Nécessite la configuration d'un cache de tuiles (Offline Manager) complexe et volumineux. | Extrêmement simple. Il suffit d'embarquer le fichier `.geojson` localement dans les `assets` de l'APK. |
| **Niveau de détail / Zoom** | **Illimité :** Adaptation automatique de la précision géographique en fonction du niveau de zoom (Overzooming / Simplification native). | **Fixe :** Pas de simplification automatique à la volée. Zoomer trop loin ou dézoomer trop haut peut déformer ou saturer l'affichage. |
| **Esthétique / Style** | Style complet et riche (bâtiments 3D, reliefs, étiquetages de rues complexes, parcs, rivières). | Très épuré et limité aux seules géométries fournies dans le GeoJSON. Pas de détails de voirie fine sans alourdir le fichier. |

---

## 4. Analyse de l'Efficacité : Pourquoi et quand le GeoJSON est-il plus efficace ?

### 🟢 Les cas où le GeoJSON "sans tuile" est plus efficace :
1. **Application 100% Hors-ligne / Mode Embarqué** : Si l'application doit fonctionner en plein désert sans aucun réseau et sans base de données de tuiles complexe, charger un fichier GeoJSON des points d'intérêt (POIs) ou des frontières simplifiées est extrêmement léger (quelques Mo) et ne nécessite aucun serveur de cartographie.
2. **Affichage thématique ou métier** : Pour dessiner uniquement des frontières administratives, des lignes de transport spécifiques ou des stations de recharge sans avoir besoin de voir les maisons, les forêts ou les petits axes routiers. Le GPU dessine directement ces lignes très rapidement.
3. **Mises à jour instantanées** : Modifier un attribut dans un GeoJSON local (par exemple, la disponibilité d'une borne ou le prix d'un carburant) et appeler `.setGeoJson()` rafraîchit immédiatement la carte sans devoir invalider ou reconstruire des tuiles vectorielles.

### 🔴 Les limites majeures du GeoJSON avec MapLibre Native Android :
1. **La limite de taille du parser de MapLibre** : MapLibre Android (v13.3.0) utilise un parseur C++ interne pour convertir le GeoJSON en structures de données utilisables par le GPU. Si le fichier GeoJSON dépasse **20 à 50 Mo**, le parsing au démarrage peut bloquer le thread principal (ANR - App Not Responding) ou saturer la mémoire (OOM - Out of Memory).
2. **Absence de simplification automatique (Tiling) à la volée** : Contrairement aux tuiles vectorielles où chaque niveau de zoom a ses propres géométries simplifiées, le GeoJSON force le moteur à traiter toutes les coordonnées géographiques à chaque frame. Si vous dézoomez sur une carte contenant un tracé routier extrêmement précis de 100 000 points, le processeur graphique va ralentir fortement (chute du taux de rafraîchissement).
3. **Rendu visuel pauvre** : Sans tuiles de fond de carte, l'utilisateur navigue sur un fond uni (noir, gris ou blanc) avec uniquement vos tracés GeoJSON par-dessus. Pour une application d'aide à la conduite ou de navigation (Gaston), l'absence de rues secondaires, de noms de villes ou d'éléments de repérage visuel (rivières, parcs) nuit gravement à l'expérience de conduite.

---

## 5. Synthèse et Recommandation pour Gaston

L'architecture actuelle de Gaston (MapLibre 13.3.0) implémente déjà le **meilleur des deux mondes (approche hybride)** :
1. **Les Tuiles Vectorielles distantes (OpenFreeMap)** pour le fond de carte : Elles apportent la fluidité, le niveau de détail géographique indispensable pour la navigation, la légèreté des transferts réseau par dalles, et la gestion des différents niveaux de zoom sans surcharger la mémoire de l'appareil.
2. **Le GeoJSON local (`MapLibreSharedHelper`)** pour les couches dynamiques : Gaston utilise à juste titre le GeoJSON pour charger les POIs (stations-services, bornes électriques) et l'itinéraire. Comme le nombre de POIs affichés simultanément est limité par la zone géographique ou la recherche, les performances restent optimales (FPS à 60 Hz).

### Conclusion
Générer une carte **complètement sans tuile** à l'aide de GeoJSON ne serait **pas plus efficace** pour Gaston en tant qu'application de navigation complète, car la quantité de données géographiques nécessaires pour afficher un fond de carte routier complet (routes, autoroutes, adresses, limites administratives) sous forme de GeoJSON brut saturerait instantanément la mémoire des smartphones et des boîtiers Android Auto.

Cependant, l'utilisation actuelle du GeoJSON pour **les seules couches de données métier** superposées sur un fond de tuiles vectorielles représente le standard de l'industrie pour une efficacité et une réactivité maximales.
