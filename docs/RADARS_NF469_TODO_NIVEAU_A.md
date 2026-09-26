# Todo — niveau A (conformité produit R. 413-15)

Checklist pour transformer l’avertisseur de radars actuel en **assistant d’aide à la conduite** tenable en France.

- **Niveau A** = comportement produit conforme à l’esprit de **R. 413-15** (zones, VMA, pas de localisation précise de contrôle).
- **Niveau B** (certificat NF 469) = hors scope de ce fichier → voir [`RADARS_NF469.md`](RADARS_NF469.md) §5 phase 6.

Contexte & écarts : [`RADARS_NF469.md`](RADARS_NF469.md).

**Critère de sortie :** en France, l’app n’affiche ni ne dit la position précise d’un contrôle ; elle annonce des **zones de danger** (dont hors radars) et la **VMA** ; distances selon le réseau ; feature documentée et supportée.

---

## Phase 0 — Go / no-go

- [x] Décider de viser l’AAC FR (niveau A)
- [x] Geler le comportement actuel sur Play FR tant que A n’est pas atteint (feature off, flag build, ou hors listing FR)
- [ ] Avis juridique écrit sur le périmètre A (recommandé)

### Décision (phase 0)

| Item | Décision |
|------|----------|
| Objectif | **Niveau A** (conformité produit R. 413-15 / AAC FR). **Niveau B (NF 469)** hors scope. |
| Play FR | Feature alertes **off par défaut** ; flavor `playstore` : `BuildConfig.AAC_ALERTS_AVAILABLE=false` tant que A n’est pas atteint (toggle UI masqué / boucle GPS inactive). Flavor `full` : disponible pour tests internes, toujours **off** par défaut. |
| Avis juridique | **Recommandé** avant exposition large Play FR — *non fourni ici* (pas d’avis inventé). Owner produit / légal à solliciter. |

Historique phase 0 : 2026-09 — go niveau A + gel Playstore.

## Phase 1 — Modèle « zones » (cœur)

- [x] Introduire `DangerZone` (géométrie, VMA?, kind, source)
- [x] Convertir radars fixes CSV → **zones étendues** (plus de point de contrôle exposé)
- [x] Distances selon réseau : ~4 km autoroute / ~2 km hors agglo / ~300 m agglo
- [x] Classification de voie pour choisir la distance
- [x] Alerte sur **entrée / présence dans la zone**
- [x] En FR : plus de pin carte « radar exact » pour les alertes
- [x] Tests : géométrie, distances, pas de fuite de coordonnée contrôle

### Notes phase 1

- Modèle : `shared/.../aac/DangerZone.kt` (+ `DangerZoneFactory`, `DangerZoneEvaluator`, `DangerZoneAlertCopy`).
- Conversion : `FranceRadarRecord.toDangerZone()` ; `FranceRadarsProvider.search` ne renvoie plus de pins (zones via records).
- Classification voie : heuristique VMA (≥110 autoroute, ≥70 hors agglo, sinon agglo) — map-matching reporté.
- FR : OSM `speed_camera` filtré dans `OverpassProvider` sur bbox France.

## Phase 2 — Libellés / UX

- [ ] UI : « zones de danger » / AAC, plus « avertisseur de radars »
- [ ] TTS : « Zone de danger » + VMA, plus « Attention, radar… »
- [ ] Plus de noms d’alerte du type `Radar X km/h`
- [ ] HUD : VMA + entrée en zone
- [ ] Feature **off** par défaut (ou on seulement si conforme)
- [ ] Retirer / borner le choix libre 300–2000 m
- [ ] Au moins un canal de messages de sécurité routière
- [ ] i18n FR conforme
- [ ] Android Auto : même vocabulaire zone/VMA

## Phase 3 — Données

- [ ] CSV data.gouv : URL dynamique + versioning
- [ ] Cache disque + TTL
- [ ] Types CSV → kinds de zone sans dire « contrôle ici »
- [ ] Désactiver OSM `speed_camera` pour les **alertes** en FR
- [ ] Source(s) de zones **hors radar** (accidentalité / vigilance…)
- [ ] Mix d’alertes (pas 100 % issues de radars)
- [ ] Pas de communautaire « forces de l’ordre » sans process L. 130-11

## Phase 4 — Moteur & qualité

- [ ] Boucle GPS découplée du `search` carte → cache local de zones
- [ ] `DangerZoneAlertManager` (remplace / évolue `RadarAlertManager`)
- [ ] Trajectoire : tronçon concerné, pas chaussée opposée si possible
- [ ] Perf / batterie (plus de fetch réseau toutes les 2 s)
- [ ] Tests non-régression libellés (pas de « radar + distance »)
- [ ] Doc technique architecture AAC

## Phase 5 — Store / legal / support

- [ ] Privacy / terms : AAC, pas avertisseur de contrôles
- [ ] Play listing / captures : zone de danger + VMA
- [ ] FAQ / À propos
- [ ] Canal support utilisateur
- [ ] Feature flag de coupure rapide

---

## Ordre recommandé

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
```
