# Gaston — agent instructions

Instructions for AI agents working in this repository. Behavioral rules also live in `.cursor/rules/` (Cursor loads them automatically).

## Project

**Gaston** is a Kotlin Multiplatform app (Android + Android Auto) for fuel stations, EV charging, rest stops, and route-aware search across Europe.

| Module | Role |
|--------|------|
| `:shared` | Models, Ktor clients, POI/fuel/EV/traffic/weather providers |
| `:androidApp` | Jetpack Compose (phone) + Car App Library (Android Auto) |

Human-oriented docs: [`README.md`](README.md), data sources [`sources.md`](sources.md), API keys [`docs/API_KEYS.md`](docs/API_KEYS.md).

## Build & test

```bash
./gradlew :androidApp:assembleFullDebug
./gradlew --no-daemon :androidApp:assembleDebug :androidApp:lintFullDebug :shared:build
./gradlew :shared:testAndroidHostTest
```

Integration tests (real APIs, optional in CI): `CountryStationLoadRealApiTests` via workflow in `.github/workflows/station-load-integration.yml`.

Prerequisites: JDK 17+, `local.properties` with `sdk.dir` (and optional `GOOGLE_MAPS_KEY`). Never commit secrets.

## Where to change things

- New country/API feed → `shared/.../api/<country>/` following existing `*Client` + `*Provider` pairs
- POI merge/selection → `shared/.../poi/`
- Phone map/UI → `androidApp/.../ui/`
- Android Auto screens → `androidApp/.../auto/`
- On-device DB → `androidApp/.../persistence/`

## Behavioral guidelines (Karpathy)

Apply these on every task. Full text: [`.cursor/rules/karpathy-guidelines.mdc`](.cursor/rules/karpathy-guidelines.mdc) (from [andrej-karpathy-skills](https://github.com/multica-ai/andrej-karpathy-skills)).

### 1. Think before coding

State assumptions; ask when uncertain. Present multiple interpretations instead of picking silently. Push back if a simpler approach exists.

### 2. Simplicity first

Minimum code for the request — no extra features, abstractions, configurability, or handling of impossible cases. If the diff is much larger than needed, simplify.

### 3. Surgical changes

Touch only what the task requires. Match existing style. Mention unrelated dead code; do not delete it unless asked. Remove orphans only from your own edits.

### 4. Goal-driven execution

Turn requests into verifiable outcomes (tests passing, lint clean, repro fixed). For multi-step work, list steps with a verify check per step.

**Tradeoff:** These bias toward caution over speed; use judgment on trivial edits.

## Gaston-specific guardrails

- Keep `:shared` free of Android UI dependencies; platform code stays in `:androidApp`
- Android Auto changes must remain driver-safe (templates, large touch targets, no phone-only flows in car)
- Do not commit API keys, `local.properties`, or Play service account JSON
- Only create git commits or open PRs when the user explicitly asks

## Success signals

- Diff lines trace to the user request
- `./gradlew` build/lint targets relevant to the change pass
- Clarifying questions happen before large implementations
