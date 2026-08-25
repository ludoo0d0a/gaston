# Audit Technique : MapLibre sur Android Auto (Gaston)

Ce document présente l'audit technique complet du fonctionnement de MapLibre sur Android Auto dans l'application Gaston, les raisons pour lesquelles la mise en œuvre actuelle ne fonctionne pas, la comparaison des 3 moteurs de cartes (`Native`, `Custom`, `MapLibre`), et l'architecture requise pour un moteur vectoriel pur sur Android Auto.

---

## 1. Diagnostic & Causes d'Échec de l'Implémentation Actuelle

Actuellement, l'intégration de MapLibre sur Android Auto (`CarMapContainer.kt`, `CarMapLibreRenderer.kt`, `MapLibrePoiScreen.kt`) essaie de rendre un `MapView` en arrière-plan et de copier son image (`Bitmap`) vers la surface d'Android Auto. Cette approche échoue pour les raisons suivantes :

### 1.1 `WindowManager.BadTokenException` sur le `CarContext`
* **Emplacement** : `CarMapContainer.kt` (lignes 75-76) & `CarContextExt.kt` (ligne 9).
* **Cause** : `CarContext` sur Android Auto (`androidx.car.app`) est un contexte de service (`CarAppService` / `Session`), **et non une `Activity`**. Dans `CarContextExt.kt`, `carWindowManager` appelle `getSystemService(Context.WINDOW_SERVICE)`. Lorsque `CarMapContainer` fait `carWindowManager.addView(this, windowLayoutParams())` avec `TYPE_PRIVATE_PRESENTATION`, Android 10+ (API 29+) lève une exception `WindowManager$BadTokenException: Unable to add window -- permission denied for this window type` car `CarContext` ne possède pas de token de fenêtre valide rattaché à un écran.
* **Impact** : L'application plante immédiatement dès que l'utilisateur ouvre l'écran MapLibre sur Android Auto.

### 1.2 Vue Hors-Écran Non Mesurée & Moteur OpenGL Non Initialisé
* **Emplacement** : `CarMapContainer.kt` (lignes 73-85).
* **Cause** : Le `MapView` de MapLibre nécessite d'être rattaché à une fenêtre active rattachée à un écran pour exécuter sa boucle de rendu C++ OpenGL ES. Comme le `MapView` hors-écran n'est jamais rattaché à une hiérarchie de vues mesurée, `getMapAsync()` ne se termine jamais ou `onMapReady` n'est jamais appelé, laissant l'écran noir/vide.

### 1.3 Contrainte de Surface Binder IPC (`SurfaceContainer.surface`)
* **Cause** : `SurfaceContainer.surface` fournie par le car host est une surface distante Binder IPC gérée par le processus Android Auto. Le moteur C++ natif de MapLibre (`libmaplibre-gl.so`) attend une fenêtre locale `ANativeWindow` / `SurfaceView` gérée par l'application locale.
* **Catégorie d'application** : Gaston est déclaré comme une application POI (`androidx.car.app.category.POI`). Les applications de catégorie POI ont accès à `MapWithContentTemplate` et `SurfaceCallback`, mais doivent dessiner directement sur la surface du car host.

### 1.4 Pression Mémoire Extreme (GC Thrashing) via `TextureView.getBitmap()`
* **Emplacement** : `CarMapLibreRenderer.kt` (lignes 290-295).
* **Cause** : Dans `drawMapOnCanvas()`, l'application appelle `textureView.bitmap` et le dessine sur le `Canvas` d'Android Auto. L'appel à `textureView.getBitmap()` alloue un nouvel objet `Bitmap` `ARGB_8888` à chaque frame (`addOnWillStartRenderingFrameListener`). À 30-60 FPS, une résolution de 800x480 alloue entre **100 et 200 Mo de mémoire poubelle par seconde**.
* **Impact** : Micro-saccades majeures (Garbage Collection pauses), ralentissements sévères de l'UI et risque de `OutOfMemoryError` ou ANR.

### 1.5 Concurrence et Plantages lors du Lock du Canvas Matériel
* **Emplacement** : `CarMapLibreRenderer.kt` (lignes 279-287).
* **Cause** : `drawOnSurface()` fait un `surface.lockHardwareCanvas()` sur le thread principal UI. Si `SurfaceCallback.onSurfaceDestroyed` survient lors du changement d'écran, un appel concurrent à `lockHardwareCanvas()` sur une surface libérée provoque une `IllegalArgumentException` ou un crash natif `libhwui`.

---

## 2. Comparatif des 3 Moteurs de Cartes sur Android Auto

| Élément | **1. Native (`CarMapMode.Native`)** | **2. Custom (`CarMapMode.Custom`)** | **3. MapLibre (`CarMapMode.MapLibre`)** |
| :--- | :--- | :--- | :--- |
| **Technologie** | `PlaceListMapTemplate` (Moteur Google Maps du car host) | `AutoSurfaceRenderer.kt` (Moteur Canvas Raster léger) | SDK MapLibre Android (`CarMapLibreRenderer.kt`) |
| **Format des Tuiles** | Vectoriel / Raster Google | Tuiles Raster PNG/JPG (Carto, OSM, Esri via HTTP) | Tuiles Vectorielles PBF (OpenFreeMap) |
| **Utilise MapLibre ?** | **Non** | **Non** (Développement Canvas indépendant) | **Oui** |
| **Stabilité** | **100% Stable** | **100% Stable** | **Incompatible / Crash (`BadTokenException`)** |
| **Performance & Mémoire** | Empreinte RAM minimale | Très faible (Cache LRU ~20 Mo) | Allocation mémoire massive (~100-200 Mo/s) |
| **Mode Hors-Ligne** | Cache Google Maps système | Cache disque & mémoire + tuiles de secours | Dépend des requêtes PBF vectorielles |
| **Personnalisation** | Limitée aux thèmes système | Fonds de carte personnalisés (Carto, Voyager, Dark, Esri) | Styles JSON MapLibre vectoriels |

---

## 3. Architecture Recommandée pour un Moteur Vectoriel Pur sur Android Auto

Pour faire fonctionner un **véritable moteur vectoriel MapLibre** sur Android Auto sans utiliser de tuiles raster ni de hack `WindowManager` :

```
[ Tuiles Vectorielles OpenFreeMap (PBF) ]
                   │
                   ▼
       [ Moteur C++ MapLibre Native ]  (libmaplibre-gl.so)
                   │
           (Commandes OpenGL ES)
                   │
                   ▼
        [ EGLDisplay / EGLSurface ]  <-- Relié directement via EGLWindowSurface
                   │
                   ▼
       [ SurfaceContainer.surface ]  <-- Surface Distante Android Auto
```

### Plan de Mise en Œuvre
1. **Créer un `CarEglSurfaceRenderer` Native** : Éliminer `CarMapContainer` et l'appel `carWindowManager.addView()`.
2. **Lier l'EGLContext à `SurfaceContainer.surface`** : Lors de `onSurfaceAvailable()`, initialiser l'EGLDisplay, l'EGLContext, et créer une `EGLSurface` directement sur la `Surface` Android Auto.
3. **Rendu GPU Direct** : Passer le handle EGL directement au moteur C++ de MapLibre via son API de rendu hors-écran (`OffscreenTile` / NativeMapView).
4. **Permutation de Buffers (Zero Copy)** : Utiliser `eglSwapBuffers()` pour envoyer directement les images vectorielles calculées sur GPU vers l'écran d'Android Auto. **0 allocation de Bitmap intermédiaire**, rendu à 60 FPS.

---

## 4. Recommandation pour Gaston

1. **Conserver `Custom` (`CarMapMode.Custom`) comme moteur personnalisé principal** : `AutoSurfaceRenderer` fonctionne à 100%, supporte les tuiles haute densité `@2x`, le cache hors-ligne, les thèmes personnalisés et les barres de disponibilité des bornes sans aucun problème de stabilité.
2. **Masquer ou désactiver `MapLibre` sur Android Auto** tant que la liaison EGL direct n'est pas développée, tout en conservant MapLibre sur l'application mobile téléphone (Compose `LibreMap`) où il fonctionne parfaitement.
