# Radars & norme NF 469 — état des lieux

Document de suivi pour la fonctionnalité d’alertes radars / zones de danger dans Gaston, et son écart avec le cadre français (code de la route + certification **NF 469** « Assistant d’aide à la conduite »).

> **Disclaimer :** ce document n’est pas un avis juridique. Le référentiel NF 469 complet est commercial (AFNOR / INFOCERT). Les distances et formulations ci‑dessous reprennent les pratiques publiques du protocole AFFTAC / sécurité routière, pas le texte certifié ligne à ligne.

**Statut global (sept. 2026) :** prototype technique **fonctionnel** côté téléphone, **non conforme** au modèle « assistant d’aide à la conduite » attendu en France. Pas de certification NF 469. Pas d’alertes dédiées Android Auto.

---

## 1. Cadre légal et NF 469 (rappel)

### Code de la route

| Référence | Effet |
|-----------|--------|
| **Décret n° 2012-3** / art. **R. 413-15** | Interdiction de détenir / transporter / utiliser un dispositif qui **signale précisément** la réalisation d’un contrôle routier (amende + retrait de points). |
| **Art. L. 130-11** + **décret n° 2021-468** | Les opérateurs de services d’aide à la conduite / navigation peuvent se voir imposer d’**occulter** temporairement la rediffusion de signalements communautaires sur certaines zones de contrôles (alcool, stupéfiants, etc. — pas les contrôles vitesse « classiques »). |

### Certification NF 469 (volontaire)

- Organisme : **AFNOR Certification**, secrétariat technique **INFOCERT**.
- Cible : matériels, apps mobiles, bases de données / flux qui informent le conducteur (visuel et/ou sonore) sur des **zones** où peuvent se trouver des contrôles.
- Atteste surtout : qualité produit, SAV, et respect des exigences **zones de danger**, **limites de vitesse**, **messages de sécurité**, **données communautaires**.
- **N’est pas obligatoire** par décret, mais c’est le référentiel de place pour les AAC (Coyote, Waze « zones », GPS constructeurs, etc.) face à R. 413-15.

### Pratiques attendues d’un AAC « type NF 469 »

| Attendu | Interdit / à éviter |
|---------|---------------------|
| Parler de **zone de danger** / **zone de vigilance** | Parler de « radar à X m », pin exact du contrôle |
| Informer sur la **limitation de vitesse** | Pointer uniquement le dispositif de contrôle |
| Zones **étendues** (pas un point GPS) | Marqueur carte = position exacte du radar |
| Distances d’alerte selon le réseau (usage courant du protocole) : **~4 km** autoroute, **~2 km** hors agglo, **~300 m** en ville | Distance libre « à l’approche du point » |
| Intégrer aussi des zones de danger **sans** radar (accidentalité, etc.) | Base = uniquement liste des radars fixes |
| Messages sécurité routière | Formulation « avertisseur de radars » |

---

## 2. Architecture actuelle dans Gaston

```
data.gouv.fr CSV (radars fixes FR)     OSM Overpass (highway=speed_camera)
        │                                         │
        ▼                                         ▼
 FranceRadarsClient/Provider              OverpassProvider
        │                                         │
        └────────────► SelectorPoiProvider ◄──────┘
                              │
              PoiCategory.Radar (markers carte + détail)
                              │
              MainActivity loop (~2 s) si radarWarningEnabled
                              │
                      RadarAlertManager
                              │
                 RadarTrajectoryHelper (bearing, VMA, distance)
                              │
                      RadarAudioNotifier (beeps + TTS)
```

| Couche | Fichiers clés |
|--------|----------------|
| Données FR | `shared/.../api/radars/FranceRadarsClient.kt`, `FranceRadarsProvider.kt` |
| POI / merge | `PoiCategory.Radar`, `PoiProviderType.FranceRadars`, `SelectorPoiProvider` |
| OSM | `OverpassProvider` (`speed_camera`) |
| Alertes | `androidApp/.../radar/RadarAlertManager.kt`, `RadarTrajectoryHelper.kt`, `RadarAudioNotifier.kt` |
| Réglages | `SettingsManager` (`radarWarningEnabled`, `radarWarningDistanceMeters`), UI dans `SettingsScreen` |
| Boucle GPS | `MainActivity` (permission + provider `search` catégorie Radar) |
| Tests | `FranceRadarsTest`, `RadarAlertTest` |

**Source open data :** CSV « Liste des radars fixes en France » (data.gouv.fr, Licence Ouverte). URL **figée** dans le client (snapshot daté, ex. `…-12-2025.csv`), cache mémoire **24 h**.

---

## 3. Ce qui va

### Données & carte

- [x] Provider dédié France (CSV officiel radars **fixes**), filtré FR, rayon configurable.
- [x] Parsing CSV robuste (en-têtes ; / , VMA, lat/lon), mapping vers `Poi` + `rawSourceData` (`vma`, `type`, `id`).
- [x] Catégorie `PoiCategory.Radar`, icônes / markers / filtres carte (`speed_camera`).
- [x] Complément OSM via Overpass (hors FR ou en secours selon sélection de providers).
- [x] Tests unitaires parse + search `FranceRadars`.

### Alertes téléphone

- [x] Opt-in/out réglages + distance d’avertissement (chips 300 / 500 / 1000 / 1500 / 2000 m).
- [x] Boucle localisation périodique (~2 s) branchée sur le `poiProvider` agrégé.
- [x] Filtrage trajectoire : radar **devant** (bearing ±40°) au-delà d’une vitesse mini.
- [x] Extraction VMA (`vma` ou parse du nom) ; distinction vitesse OK vs excès.
- [x] Déduplication d’alerte par `radar.id` + reset hors zone / derrière.
- [x] Audio : bips discrets (OK) / bip + TTS (excès).
- [x] Tests `RadarAlertTest` (bearing, VMA, overspeed, dédup).

### Intégration produit

- [x] Provider listé dans les réglages sources (`provider_france_radars`).
- [x] DI Koin (`MapModule` → `franceRadars`).

---

## 4. Ce qui ne va pas

### Conformité légale / NF 469 (bloquant produit FR)

| Problème | Détail dans Gaston |
|----------|---------------------|
| **Position exacte** | Chaque radar est un **POI ponctuel** (carte + alerte sur coordonnées CSV/OSM). Pas de zone tampon / polygone. |
| **Libellés « radar »** | UI : « Avertisseur de radars », « Alerte radars en approche ». TTS : *« Attention, radar à X km/h »*. Noms POI : `Radar 130 km/h`. |
| **Distances** | Choix utilisateur fixe (défaut **1000 m**), **pas** 4 km / 2 km / 300 m selon type de voie. |
| **Pas de zones hors radar** | Aucune base de zones de danger / accidentalité / vigilance à mélanger aux alertes. |
| **Activation par défaut** | `radarWarningEnabled = true` → comportement type avertisseur dès l’install. |
| **Pas de NF 469** | Aucune démarche AFNOR/INFOCERT, pas de référentiel qualité/SAV associé. |
| **Communautaire** | Pas de flux users ; donc pas de pipeline L. 130-11 — mais aussi **pas** de modèle AAC complet. |

### Limites techniques / produit

| Problème | Détail |
|----------|--------|
| **Android Auto** | Pas de boucle d’alerte dédiée AA (icône `speed_camera` seulement dans des dashboards POI). |
| **URL CSV figée** | Risque de dataset périmé ; pas de résolution dynamique de la dernière ressource data.gouv. |
| **Radars mobiles / chantiers / tronçons** | Dataset = fixes ; types CSV partiellement exposés (`type`) mais UX peu différenciée. |
| **Hors France** | FranceRadars = FR only ; ailleurs = OSM seulement (qualité variable). |
| **Visuel d’alerte** | Surtout sonore ; pas de bandeau / HUD « zone de danger + VMA » type AAC. |
| **Performance** | `search` Radar toutes les ~2 s via provider agrégé (dépend des providers actifs / réseau) — OK en proto, à revoir pour prod. |
| **Docs / store** | Absents de `features.md`, privacy, terms — aucun encadrement utilisateur. |

---

## 5. Todo list — devenir un AAC type NF 469

Objectif : passer d’un **avertisseur de radars** à un **assistant d’aide à la conduite** aligné sur l’esprit NF 469 / R. 413-15, puis (optionnel) certification AFNOR.

Deux niveaux :

| Niveau | Signification |
|--------|----------------|
| **A — Conformité produit FR** | Comportement & données acceptables face à R. 413-15 (zones, libellés, VMA). **Minimum pour exposer la feature en France.** Checklist dédiée : [`RADARS_NF469_TODO_NIVEAU_A.md`](RADARS_NF469_TODO_NIVEAU_A.md). |
| **B — Certification NF 469** | Référentiel acheté + process qualité/SAV + audit INFOCERT. **Optionnel** (volontaire), mais c’est la marque de place. |

Les cases `[ ]` ci‑dessous = backlog global (A+B). **Pour cocher le niveau A uniquement**, utiliser [`RADARS_NF469_TODO_NIVEAU_A.md`](RADARS_NF469_TODO_NIVEAU_A.md).

---

### Phase 0 — Go / no-go

- [ ] **Décider** : viser AAC FR (niveau A) ± certification (niveau B)
- [ ] **Geler** le comportement actuel pour les builds Play FR tant que A n’est pas atteint (feature off, flag build, ou hors listing FR)
- [ ] **Acheter le référentiel NF 469** (AFNOR) — dès que B est envisagé ; sinon au moins un avis juridique écrit sur A
- [ ] Lister les écarts référentiel ↔ code dans une checklist d’audit interne (après lecture du référentiel)

---

### Phase 1 — Modèle domaine « zones » (cœur NF 469)

Remplacer l’unité d’alerte « point radar » par « zone ».

- [ ] Introduire `DangerZone` (id, géométrie segment/cercle, `speedLimitKmH?`, `kind`, source)
  - kinds minimaux : `SpeedControlArea`, `AccidentProne`, `HeightenedVigilance`, `RoadSafetyMessage` (noms internes, pas UX)
- [ ] Convertisseur : radar fixe (CSV) → **zone étendue**, jamais exposée comme point de contrôle
- [ ] Distances / longueurs selon réseau (usage protocole AFFTAC, à confirmer référentiel) :
  - [ ] ~**4 km** autoroute
  - [ ] ~**2 km** hors agglomération
  - [ ] ~**300 m** en agglomération
- [ ] Classification voie (map matching / type OSM / heuristique vitesse-réseau) pour choisir la distance
- [ ] Moteur d’alerte sur **entrée / présence dans la zone** (plus : distance au pin radar)
- [ ] Interdire en FR le pin carte « radar exact » pour les alertes (masquer ou ne pas dessiner `PoiCategory.Radar` comme contrôle)
- [ ] Tests unitaires : géométrie zone, distances par type de voie, pas de fuite de coordonnée « contrôle »

---

### Phase 2 — Libellés, messages, UX (restrictions d’information)

Exigences publiques NF 469 : modalités d’info **limites de vitesse**, **zones de danger**, **messages de sécurité**, **zones de vigilance**, **points d’info routière**, et **restrictions** sur la localisation des contrôles.

- [ ] Renommer toute l’UI FR : plus « Avertisseur de radars » → « Assistant d’aide à la conduite » / « Zones de danger »
- [ ] TTS & sons : plus « Attention, radar… » → « Zone de danger » + annonce **VMA** si connue
- [ ] Noms / adresses POI dérivés : ne plus préfixer `Radar X km/h` dans les flux d’alerte
- [ ] HUD / bandeau : **VMA** prioritaire + entrée en zone (visuel avant / avec le son)
- [ ] Défaut feature : **off**, ou on seulement après parcours conforme
- [ ] Réglages : retirer le choix libre 300–2000 m **ou** le restreindre aux bornes légitimes du type de voie
- [ ] Messages de sécurité routière (au moins un canal : tip périodique / entrée zone vigilance) — exigence NF 469 « messages de sécurité »
- [ ] i18n : strings FR conformes ; autres langues sans réintroduire « radar pin » en FR
- [ ] Android Auto : même vocabulaire zone/VMA ; pas de liste « radars à proximité » (voir `docs/android-auto.md`)

---

### Phase 3 — Données (fixes + hors radars)

Sans zones **non radar**, on reste un avertisseur déguisé.

- [ ] Pipeline CSV data.gouv : URL **dynamique** (dernière ressource) + versioning
- [ ] Cache **disque** + TTL (offline court, MAJ à l’initiative app)
- [ ] Mapper types CSV (fixe, feu, tronçon, …) → kinds de zone **sans** dire « contrôle ici »
- [ ] **Désactiver OSM `speed_camera` pour les alertes en FR** (ou ne l’utiliser que pour enrichir une zone déjà « danger », jamais comme pin)
- [ ] Source(s) zones hors radar (à trancher) :
  - [ ] accidentalité / points noirs open data
  - [ ] et/ou zones fournies / dérivées d’un jeu « vigilance accrue »
- [ ] Ratio / politique : alertes danger **mixées** (pas 100 % issues de radars)
- [ ] Pas de flux communautaire **forces de l’ordre** tant que pas de process L. 130-11 ; si communautaire plus tard :
  - [ ] pas de rediffusion « contrôle police ici »
  - [ ] canal d’occultation préfectorale (décret 2021-468)

---

### Phase 4 — Moteur technique & qualité logicielle

NF 469 s’appuie aussi sur ISO/CEI **25051** (qualité logiciel) + moyens de contrôle qualité.

- [ ] Découpler boucle GPS d’alerte du `poiProvider.search` carte → cache local de zones autour du véhicule
- [ ] `DangerZoneAlertManager` (évolution / remplacement de `RadarAlertManager`)
- [ ] Bearing / trajectoire : alerter sur le **tronçon concerné**, pas le point opposé de chaussée si possible
- [ ] Batterie / perf : fréquence adaptée à la vitesse ; pas de fetch réseau toutes les 2 s
- [ ] Suite de tests : zones, VMA, libellés FR, non-régression « aucun message contenant radar+distance »
- [ ] Doc technique interne : architecture AAC + matrice exigences ↔ modules
- [ ] (Option) télémétrie anonyme qualité d’alerte — sans PII

---

### Phase 5 — Store, legal, support (niveau A livrable)

- [ ] Mentions dans privacy / terms : nature AAC, pas avertisseur de contrôles
- [ ] Play listing / captures : vocabulaire zone de danger / VMA
- [ ] FAQ / écran À propos : explication courte + lien sécurité routière
- [ ] Canal support utilisateur (mail / form) — exigence SAV même avant audit
- [ ] Feature flag remote ou build : coupure rapide si non-conformité découverte

---

### Phase 6 — Certification NF 469 (niveau B, optionnel)

- [ ] Candidature AFNOR / INFOCERT (catégorie **application logicielle** mobile)
- [ ] Dossier : description produit, bases de données, modalités d’info (visuel/sonore), process MAJ données
- [ ] Preuves exigées typiques :
  - [ ] contrôle qualité produit (tests, recette, non-régression)
  - [ ] engagements SAV / support
  - [ ] conformité zones de danger / VMA / messages / restrictions contrôles
- [ ] Audit sur site / à distance → plan d’actions → certificat + logo
- [ ] Surveillance annuelle + renouvellement
- [ ] Budget & owner légal/produit nommés

---

### Ordre de travail recommandé

```
0 Go/no-go (+ geler Play FR)
    ↓
1 DangerZone + distances réseau     ← bloque tout le reste
    ↓
2 Libellés / TTS / HUD / settings
    ↓
3 Données (CSV dynamique + zones hors radar)
    ↓
4 Moteur + tests + perf
    ↓
5 Legal / store / SAV          →  Niveau A atteint
    ↓
6 Référentiel + audit NF 469   →  Niveau B (si go business)
```

**Critère de sortie niveau A :** en France, l’app n’affiche ni ne dit la position précise d’un contrôle ; elle annonce des **zones de danger** (dont hors radars) et la **VMA** ; distances selon le réseau ; feature documentée et supportée.

**Critère de sortie niveau B :** certificat NF 469 valide + logo utilisable + process de surveillance en place.

---

## 6. Matrice rapide conformité

| Exigence (esprit NF 469 / R. 413-15) | État Gaston |
|--------------------------------------|-------------|
| Pas de localisation précise du contrôle | Non — points GPS + markers |
| Vocabulaire zone de danger / VMA | Non — « radar » partout |
| Distances selon réseau | Non — distance utilisateur fixe |
| Zones de danger hors radars | Absent |
| Qualité / SAV / process certif | Absent |
| Alertes trajectoire + VMA | Oui (technique) |
| Open data radars fixes FR | Oui |
| Tests unitaires alertes / CSV | Oui |

---

## 7. Liens utiles

- [AFNOR — NF Assistant d’aide à la conduite (NF469)](https://certification.afnor.org/qualite/nf-assistant-d-aide-a-la-conduite)
- [INFOCERT — NF469](https://infocert.org/nf469/)
- [data.gouv — Liste des radars fixes en France](https://www.data.gouv.fr/fr/datasets/liste-des-radars-fixes-en-france/)
- Code : `androidApp/.../radar/`, `shared/.../api/radars/`

---

## 8. Historique

| Date | Note |
|------|------|
| 2026-09 | Création du doc : inventaire code actuel vs écart NF 469 / R. 413-15. |
| 2026-09 | Todo list phasée niveau A (conformité produit) / B (certification NF 469). |
| 2026-09 | Checklist niveau A extraite dans [`RADARS_NF469_TODO_NIVEAU_A.md`](RADARS_NF469_TODO_NIVEAU_A.md). |
