# Audit & Roadmap Technique : MapLibre sur Android Auto (Gaston)

Ce document analyse la situation actuelle de **MapLibre sur Android Auto** dans l'application Gaston, confirme l'option actuellement implémentée, et définit la **roadmap technique** pour migrer vers l'Option 2 (Rendu Natif EGL Direct).

---

## 1. État Actuel dans Gaston : Option 1 Implémentée

Actuellement, Gaston implémente l'**Option 1 (Vue Hors-Écran via `WindowManager`)** dans le package `fr.geoking.gaston.auto.maplibre` :
* **`CarMapContainer.kt`** : Crée un `MapView` Android hors-écran et tente de l'ajouter au Window Manager via `carWindowManager.addView()`.
* **`CarMapLibreRenderer.kt`** : Écoute chaque frame, extrait `textureView.bitmap` et copie l'image bitmap sur le `Canvas` d'Android Auto.
* **`MapLibrePoiScreen.kt`** : Écran principal rattaché au `CarMapLibreRenderer`.

### Diagnostic de l'Implémentation Actuelle
L'Option 1 **ne fonctionne pas sur Android Auto** pour les raisons suivantes :
1. **Crash `BadTokenException`** : `CarContext` est un service et n'a pas de token de fenêtre pour `carWindowManager.addView()`.
2. **Pression Mémoire Accablante** : L'extraction de `TextureView.bitmap` à 60 FPS génère entre **100 et 200 Mo/s d'allocations de mémoire poubelle**, saturant le Garbage Collector.
3. **Moteur GL Non Mesuré** : La vue n'étant pas rattachée à un écran réel, l'EGLContext d'origine ne s'initialise pas.

---

## 2. Comparatif des 3 Implémentations MapLibre

| Critère / Fonctionnalité | **Option 1 : Vue Hors-Écran (`MapView` + `WindowManager`)** *(Actuel)* | **Option 2 : Rendu Natif EGL Direct (`EGLContext` + C++ `NativeMapView`)** *(Recommandé)* | **Option 3 : Pipeline Vectoriel-vers-Raster (`MapSnapshotter`)** |
| :--- | :--- | :--- | :--- |
| **Principe** | Ajoute un `MapView` hors-écran via `carWindowManager.addView()`, capture `TextureView.bitmap` et copie sur AA | Lie un `EGLContext` / `EGLSurface` directement sur `SurfaceContainer.surface` via le moteur C++ MapLibre | Rend les styles vectoriels MapLibre en tuiles/snapshots d'arrière-plan puis affichage Canvas |
| **Compatibilité Android Auto** | **Incompatible / Crash (`BadTokenException`)** | **100% Conforme & In-Process (`CarContext`)** | **100% Conforme & Sans crash `WindowManager`** |
| **Résultat d'Affichage** | Plante au lancement sur Android 10+ (ou écran noir) | **Carte Vectorielle Temps Réel 60 FPS** | Carte basée sur le style vectoriel MapLibre, rafraîchie par tuiles |
| **Consommation Mémoire** | **Lourde / Catastrophique** (~100–200 Mo/s d'allocations `Bitmap`) | **Optimale / Zero Copy** (Permutation GPU `eglSwapBuffers`) | **Faible / Contrôlée** (Cache `LruCache` des tuiles) |
| **Fluidité Pan & Zoom** | Saccadée (si non planté) | **Fluide à 60 FPS (Accélération GPU directe)** | Fluide au pan (tuiles en cache), rendu lors de l'arrêt caméra |
| **Complexité de Code** | Faible (API Android haut niveau MapLibre) | **Élevée** (Nécessite du code JNI / EGL natif bas niveau) | **Moyenne** (Gestion d'un pool `MapSnapshotter`) |

---

## 3. Roadmap Technique : Migration vers l'Option 2 (Rendu Natif EGL Direct)

Pour offrir un véritable rendu vectoriel MapLibre fluide et sans crash sur Android Auto, voici la feuille de route de développement :

```
[ Style JSON OpenFreeMap (PBF) ]
               │
               ▼
   [ NativeMapView C++ MapLibre ]
               │
      (Commandes OpenGL ES)
               │
               ▼
    [ EGLDisplay / EGLSurface ]  <-- eglCreateWindowSurface(surface)
               │
               ▼
   [ SurfaceContainer.surface ]  <-- Surface Distante Android Auto
```

### Phase 1 : Gestionnaire de Surface EGL Natif (`CarEglSurfaceRenderer.kt`)
* Créer un composant `CarEglSurfaceRenderer` gérant le cycle de vie EGL OpenGL ES 2.0/3.0.
* Lors de `SurfaceCallback.onSurfaceAvailable(surfaceContainer)`, récupérer l'objet `android.view.Surface`.
* Initialiser `eglGetDisplay()`, `eglCreateContext()` et créer la surface d'affichage avec `eglCreateWindowSurface()`.
* Gérer le redimensionnement (`onVisibleAreaChanged`) et la libération propre (`eglDestroySurface`, `eglDestroyContext`).

### Phase 2 : Découplage du Moteur C++ MapLibre (`NativeMapView`)
* Instancier directement le cœur natif C++ de MapLibre (`org.maplibre.android.maps.NativeMapView` ou `MapRenderer`) sans utiliser la vue Android `MapView` / `TextureView`.
* Associer les handles `EGLContext` et `EGLSurface` directement à la boucle de rendu C++.
* Charger le style JSON OpenFreeMap directement dans l'engine natif (`nativeMapView.setStyleUrl()`).

### Phase 3 : Boucle de Rendu & Permutation de Buffers GPU (Zero Copy)
* Associer le rappel de frame C++ de MapLibre (`onFrameRendered` / `requestRender`) à l'appel `eglSwapBuffers(eglDisplay, eglSurface)`.
* Éliminer toute allocation de `Bitmap` et tout appel `Canvas.drawBitmap()`.
* Obtenir un rendu vectoriel matériellement accéléré à 60 FPS.

### Phase 4 : Couches GeoJSON, Caméra & Hit-Testing des Marqueurs
* Adapter `MapLibreSharedHelper.kt` pour piloter `NativeMapView` (sources GeoJSON, couches de marqueurs bornes/carburant, cercles de recherche et couleurs de disponibilité).
* Synchroniser la position caméra (`centerLat`, `centerLon`, `zoom`, `bearing`, `pitch`).
* Mapper les coordonnées de clics de `SurfaceCallback.onClick(x, y)` vers `nativeMapView.queryRenderedFeatures()` en tenant compte des marges hôte.

### Phase 5 : Validation & Banc d'Essai Android Auto (DHU)
* Valider l'absence totale de crash `BadTokenException` sur l'émulateur Android Auto DHU et véhicules physiques.
* Mesurer la consommation mémoire (vérifier < 30 Mo de RAM et 0 Mo/s d'allocations poubelle).
* Valider la fluidité du suivi de position et de la rotation Heading-Up.
