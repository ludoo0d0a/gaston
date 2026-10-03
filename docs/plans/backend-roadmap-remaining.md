# Backend Gaston — roadmap restante

> Mis à jour 2026-10-02.  
> **Fait :** Phase 0 scaffold, Phase 1 dumps FR/ES/IT + merge IRVE, WE ops Free remote, BE NAP + DOT-NL, fuel history/forecast API, garde-fou writes D1 (upsert).  
> Worker : `https://gaston-api.ludovic-valente.workers.dev`

---

## État actuel (done)

| Bloc | Statut |
|------|--------|
| Worker Free + D1 + R2 + auth Bearer | ✅ remote |
| GeoJSON `/v1/pois`, `/v1/zones` | ✅ |
| Ingest Gireve, QualiCharge, merged-irve, Minetur, MIMIT, radars FR/LU | ✅ (remote : 1 gros source/jour) |
| Belgium NAP + DOT-NL (stations) | ✅ remote |
| `/v1/fuel/history` + `/v1/fuel/forecast` (FR + marché) | ✅ |
| `POST /v1/toll/estimate` + ingest `open-toll-data` (R2) | ✅ code |
| GHA schedule LU + fuel-history | ✅ code ; secrets GHA à vérifier |
| Upsert + refuse remote > ~90k writes | ✅ |

**Contrainte permanente :** D1 Free **100k writes/jour**, reset **00:00 UTC**. Pas de `--replace` sur gros dumps.

---

## Roadmap restante (ordre suggéré)

### R0 — Ops & hygiène (court, bloque la confiance)

1. ~~Vérifier secrets GHA `CLOUDFLARE_API_TOKEN` + `CLOUDFLARE_ACCOUNT_ID`~~ — `ACCOUNT_ID` ✅ ; **token encore à créer** (`backend/scripts/ops/set-gha-cf-token.sh`)
2. Calendrier ingest remote post-quota : **1 source lourde / jour** (ex. J+1 gireve ou merged-irve si pas encore sync remote ; ne pas rejouer BE+NL le même jour).
3. Smoke : `backend/scripts/ops/smoke-remote.sh` (health + fuel + BE). Worker redéployé 2026-10-02.

### R1 — Phase D proxies clés Ouest (si besoin produit)

| Item | API | Notes |
|------|-----|--------|
| **Lufop** | lazy SWR sur `/v1/zones` ou route dédiée | Clé dans Worker secret ; FR/BE/LU AAC |
| **Tankerkönig** | `GET /v1/pois?source=tankerkoenig` nearby | Clé Worker ; DE only ; pas de dump national Free |

Hors Free-friendly si volume élevé → garder **lazy** (pas d’ingest massif).

### R1b — Toll (OpenTollData)

1. Ingest remote `open-toll-data` (R2 `toll/open_toll_data.json`) — no D1 writes.
2. Deploy Worker with `POST /v1/toll/estimate`.
3. Client opt-in (feature flag) with local `TollCalculator` fallback.

### R2 — Fuel élargi (léger)

1. Moyenne nationale **ES** / **IT** dérivée du dump du jour (1 point/jour → `fuel_national_daily`).
2. Optionnel V1.5 : `fuel_station_daily` agrégé geohash (pas chaque station/heure).
3. Garder Yahoo fallback documenté (Stooq souvent 403 depuis CI).

### R3 — Données remote manquantes / refresh

Ingest **remote** encore à planifier (jours séparés, upsert) :

- `gireve` / `qualicharge` / `merged-irve` (si pas déjà sync)
- `minetur` / `mimit` refresh
- `france-radars` (gros CSV)

Local peut déjà être à jour ; remote = ce que l’API publique voit.

### R4 — Client Android (opt-in, hors backend pur)

1. Feature flag : lire forecast/history depuis Worker, fallback Room/on-device.
2. Optionnel POI nearby via `/v1/pois` pour pays déjà en D1 (BE/NL/…), fallback providers locaux.
3. **Ne pas** retirer les providers File tant que le backend n’est pas stable + quota maîtrisé.

### R5 — Carte / perf (bas priorité, gros chantier)

- Overlays GeoJSON / tuiles geohash POI
- Styles carte hébergés
- Hors scope tant que R0–R2 ne sont pas stables

---

## Explicitement hors scope (rester simple)

- NOBIL, EIPA, Nordiques, Est, hors Europe
- Merge QualiCharge + Belib Paris
- ML / forecast au-delà des règles actuelles
- Workers Paid / Cron CF (rester GHA)
- Play Billing / Firebase

---

## Critères de sortie “backend WE utilisable”

- [ ] GHA schedule vert 7 jours (LU + fuel-history)
- [ ] Remote D1 : FR IRVE merge **ou** gireve + BE + NL queryables
- [ ] `/v1/fuel/forecast` FR non vide après chaque reset quotidien
- [ ] Aucun ingest remote > 90k writes sans `--force` documenté
- [ ] (Optionnel) Lufop lazy **ou** décision “pas de proxy clé”

---

## Prochaine action recommandée

**R0** demain après **00:00 UTC** : valider secrets GHA + un seul ingest remote manquant prioritaire (pas DOT-NL/BE le même jour), puis décider **R1 Lufop** vs **R4 client forecast** selon le besoin produit.
