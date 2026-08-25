# Audit & Comparatif des 3 Implémentations Techniques de MapLibre sur Android Auto

Ce document analyse spécifiquement **MapLibre sur Android Auto** et compare les **3 approches techniques possibles** pour intégrer le moteur vectoriel MapLibre dans l'environnement Android Auto (`androidx.car.app`).

---

## 1. Contexte & Problématique d'Android Auto

Sur Android Auto (`CarContext`), l'application s'exécute sous forme de service arrière-plan (`CarAppService` / `Session`) et **n'est pas une `Activity`**. L'affichage s'effectue via une surface distante fournie par le car host dans un Binder IPC (`SurfaceContainer.surface`).

---

## 2. Tableau Comparatif des 3 Implémentations Techniques de MapLibre

| Critère / Fonctionnalité | **Approche 1 : Vue Hors-Écran (`MapView` + `WindowManager`)** *(Actuel)* | **Approche 2 : Rendu EGL Natif Direct (`EGLContext` + C++ `NativeMapView`)** | **Approche 3 : Pipeline Vectoriel-vers-Raster (`MapSnapshotter` / Offline Tile)** |
| :--- | :--- | :--- | :--- |
| **Description** | `MapView` rattaché via `carWindowManager.addView()`, capture `TextureView.bitmap` et copie sur le Canvas AA | Liaison directe d'un `EGLContext` / `EGLSurface` sur `SurfaceContainer.surface` via l'engine C++ MapLibre | Rendu des styles vectoriels MapLibre en tuiles/snapshots bitmap en arrière-plan puis affichage Canvas |
| **Compatibilité Android Auto** | **Incompatible / Crash (`BadTokenException`)** | **100% In-Process & Conforme `CarContext`** | **100% Conforme & Sans crash `WindowManager`** |
| **Affiche de la carte** | Écran noir / Plante au lancement sur Android 10+ | **Carte Vectorielle Temps Réel 60 FPS** | Carte basée sur le style vectoriel MapLibre, rafraîchie par snapshots |
| **Consommation Mémoire & GC** | **Massive / Catastrophique** (~100-200 Mo/s d'allocations `Bitmap`) | **Nulle / Optimale** (Permutation de buffers GPU `eglSwapBuffers`) | **Faible / Contrôlée** (Mise en cache `LruCache` des snapshots) |
| **Fluidité Pan & Zoom** | Saccadée (si cela ne plante pas) | **Fluide à 60 FPS (Accélération GPU directe)** | Fluide au pan/zoom (tuiles en cache), rendu lors de l'arrêt caméra |
| **Complexité de Code** | Faible (Réutilise l'API Android haut niveau MapLibre) | **Élevée** (Nécessite du code JNI / EGL natif ou APIs privées MapLibre) | **Moyenne** (Gestion d'un pool de worker `MapSnapshotter`) |
| **Prise en charge 3D / Inclinaison** | Théorique | **Oui (Support natif de la caméra 3D MapLibre)** | Non (Rendu 2D plat par tuiles) |

---

## 3. Détail des 3 Implémentations

### Implémentation 1 : `MapView` Hors-Écran via `WindowManager` *(Implémentation Actuelle Gaston)*
* **Fonctionnement** :
  1. Instancie un `MapView` Android standard (`textureMode = true`).
  2. Tente de l'ajouter au Window Manager système via `carWindowManager.addView()`.
  3. À chaque frame, extrait le `Bitmap` du `TextureView` enfant et le dessine sur la `Surface` Android Auto via `lockHardwareCanvas()`.
* **Causes de l'Échec** :
  * `CarContext` n'est pas une `Activity` -> `WindowManager$BadTokenException`.
  * La vue hors-écran n'étant pas mesurée par un écran réel, l'EGLContext de `MapView` ne s'initialise pas correctement.
  * La création de `Bitmap` à 60 FPS sature le Garbage Collector (~100-200 Mo/s).

---

### Implémentation 2 : Rendu Natif EGL Direct sur `SurfaceContainer.surface` *(Solution Idéale Vectorielle)*
* **Fonctionnement** :
  1. Élimine complètement `MapView`, `TextureView` et `WindowManager`.
  2. Lors de `SurfaceCallback.onSurfaceAvailable(surfaceContainer)`, récupère la `Surface` Android native.
  3. Initialise un `EGLDisplay`, un `EGLContext` et crée une `EGLSurface` (`eglCreateWindowSurface()`).
  4. Lie le moteur C++ natif de MapLibre (`NativeMapView` / `OffscreenTile` / `MapRenderer`) directement à cet `EGLSurface`.
  5. Effectue les appels de rendu GL directement sur la GPU et permute les buffers via `eglSwapBuffers()`.
* **Avantages** :
  * **Accélération GPU directe sans aucune allocation de Bitmap**.
  * Rendu vectoriel fluide 60 FPS avec rotation, inclinaison 3D et styles JSON OpenFreeMap.
  * Aucune dépendance à `WindowManager` -> Aucune exception `BadTokenException`.
* **Inconvénients** :
  * Nécessite d'écrire du code d'intégration EGL/OpenGL ES bas niveau en C++/JNI ou d'utiliser les classes internes natives de MapLibre.

---

### Implémentation 3 : Pipeline Vectoriel-vers-Raster via `MapSnapshotter`
* **Fonctionnement** :
  1. Utilise l'API `MapSnapshotter` de MapLibre (ou un pool d'instances vectorielles en arrière-plan) pour générer des images/tuiles raster à partir des styles vectoriels MapLibre JSON.
  2. Envoie ces images vectorielles rendu-en-raster à un moteur Canvas (comme `AutoSurfaceRenderer`).
* **Avantages** :
  * Conserve la richesse des styles vectoriels MapLibre (OpenFreeMap, thèmes personnalisés).
  * Totalement conforme au modèle de service d'Android Auto.
  * Évite les allocations mémoires continues grâce au cache LRU des snapshots.
* **Inconvénients** :
  * Ce n'est pas un rendu vectoriel temps réel continu à 60 FPS lors des déplacements de caméra.

---

## 4. Conclusion & Recommandation

Si l'objectif est d'avoir un **moteur MapLibre vectoriel fonctionnel sur Android Auto** :
1. **L'Implémentation 1 (actuelle) doit être abandonnée** car elle est techniquement incompatible avec l'architecture d'Android Auto (`BadTokenException` et thrashing mémoire).
2. **L'Implémentation 2 (Rendu Natif EGL Direct)** est la seule vraie solution vectorielle temps réel 60 FPS pour MapLibre sur Android Auto.
3. **L'Implémentation 3 (Snapshotter Vectoriel)** est une alternative intermédiaire pour bénéficier des styles vectoriels MapLibre sans complexité EGL native.
