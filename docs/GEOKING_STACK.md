# GeoKing tools / CI in Gaston

Gaston **uses geoking-ci** for Play release and debug CI. Shared scripts come from
`geoking-tools` via `./scripts/gk` (see `link-scripts.sh`).

## Active workflows

| Workflow | Trigger | Backend |
|---|---|---|
| `release-play.yml` | push `main` / tags `v*` / dispatch | `ludoo0d0a/geoking-ci` → `bundlePlaystoreRelease` → Play internal |
| `android-ci.yml` | push / PR `main` | geoking-ci assemble + local lint + `check-no-secrets` |
| `station-load-integration.yml` | Gaston-specific | keep |
| `pages.yml` | website | keep |

## Local scripts

```bash
./scripts/gk --list
./scripts/release-play-local.sh   # when Actions credits are out
./scripts/debug-play-dhu.sh       # Gaston-specific DHU (custom, not symlink)
./scripts/run-dhu.sh
```

## Secret aliases (no rename required)

geoking-ci accepts both naming schemes:

| geoking-ci | Gaston legacy (still works) |
|---|---|
| `KEYSTORE_BASE64` | `SIGNING_KEY` |
| `KEYSTORE_PASSWORD` | `KEY_STORE_PASSWORD` |
| `KEY_ALIAS` | `ALIAS` |
| `KEY_PASSWORD` | `KEY_PASSWORD` |
| `PLAY_SERVICE_ACCOUNT_JSON` | `SERVICE_ACCOUNT_JSON` |
| `WEB_CLIENT_ID` | `GOOGLE_WEB_CLIENT_ID` |
| `GOOGLE_SERVICES_JSON` | plain JSON **or** base64 |

## Safe to remove now

| Path | Why |
|---|---|
| ~~`deploy.yml`~~ | Replaced by `release-play.yml` |
| ~~`pr-build.yml`~~ | Replaced by `android-ci.yml` |
| `.github/play-release-notes/` | Optional; `playstore/whatsnew.xml` is the source now |

### Later (optional)

| Path | When |
|---|---|
| `scripts/sync_firebase_play_signing.sh` | After you’re happy with `./scripts/pull-google-services.sh` + `setup-release.sh play-sha` |
| Custom `debug-play-dhu.sh` / `run-dhu.sh` | After validating tools versions, delete customs and re-run `link-scripts.sh` |

### Keep

`check-no-secrets.sh`, `deploy.sh`, `gen_assets.py`, `ev_prices_fr.py`, `regenerate_screenshots.sh`, `station-load-integration.yml`, `pages.yml`.
