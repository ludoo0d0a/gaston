# Offline map download — déclaration Play `dataSync`

Le CI Play a refusé l’AAB parce que le manifeste déclarait `FOREGROUND_SERVICE_DATA_SYNC` sans formulaire Console. **Le formulaire n’est pas dans Fiche Play / Store listing.**

Le build `playstoreRelease` déclare `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` pour que Play affiche la 11ᵉ carte. Remplir le formulaire **avant** que le commit Internal ne soit accepté.

---

## Où cliquer (lien unique)

**[Gaston → Contenu de l’application](https://play.google.com/console/u/0/developers/8648842673731499425/app/4974244435243198358/app-content/overview?hl=fr)**

Menu gauche : **Règles et programmes → Contenu de l’application**  
(EN : *Policy and programs → App content*)

Sur cette page, carte **Autorisations des services de premier plan** (*Foreground service permissions*) → **Démarrer la déclaration**.

Si tu ne vois pas la carte :

1. Même page, cherche « premier plan » / « foreground ».
2. [Publishing overview](https://play.google.com/console/u/0/developers/8648842673731499425/app/4974244435243198358/publishing?hl=fr) → tâches de déclaration incomplètes.
3. La carte **n’existe que si Play a vu un AAB avec** `FOREGROUND_SERVICE*`. Sans ça (build actuel), le formulaire est masqué. Il faut renvoyer un AAB **avec** la permission (Internal) : l’upload peut encore échouer au *commit*, mais la carte App content apparaît ensuite.

Règles : [FGS / vidéo Play](https://support.google.com/googleplay/android-developer/answer/13392821?hl=fr).

---

## Remplir le formulaire

| Champ | Valeur |
|--------|--------|
| L’app utilise des FGS ? | **Oui** |
| Type | **Data sync** (`FOREGROUND_SERVICE_DATA_SYNC`) |
| Cas d’usage | **Network transfer: Upload or download** |
| **Vidéo** | https://youtube.com/shorts/zyETEeD1fa4 |

**Description (coller) :**

> L’utilisateur lance le téléchargement d’une carte hors-ligne Mapsforge ou PMTiles depuis Paramètres → Cartes hors ligne par région, ou depuis Android Auto. Une notification « Téléchargement de carte hors-ligne » affiche la progression jusqu’à l’installation. Ce n’est pas une synchro automatique.

**Si la tâche est reportée ou interrompue :**

> Le fichier reste partiel ; hors-ligne / Android Auto pour cette région reste indisponible. L’utilisateur doit relancer Download (reprise HTTP Range sur le `.tmp`).

Enregistrer. Attendre que la carte soit **terminée** avant de remettre le FGS dans le manifeste (sinon le CI rebloque).

Vidéo locale (regénérer) : [`docs/playstore/fgs-data-sync-demo.mp4`](playstore/fgs-data-sync-demo.mp4) — `python3 docs/playstore/generate_fgs_data_sync_demo.py`

---

## Remettre le FGS dans le code (après acceptation Play)

- Manifeste : `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` (enlever `tools:node="remove"`), `foregroundServiceType="dataSync"` sur `MapDownloadService`
- `startForegroundService` + `ServiceCompat.startForeground(..., DATA_SYNC)` au début de `onStartCommand`
- Flavor **main** / `playstoreRelease` (pas seulement `full`)

---

Voir aussi `docs/park-stop-fgs.md` (FGS location, même piège de carte masquée).
