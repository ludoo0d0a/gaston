#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Defaults (repo root)
DEFAULT_SERVICE_ACCOUNT_JSON="$ROOT/androidApp/service-account.json"
DEFAULT_CERT_DER="$ROOT/androidApp/deployment_cert.der"
DEFAULT_OUT="androidApp/google-services.json"

usage() {
  cat <<'EOF'
Sync Play App Signing certificate fingerprints into Firebase, then download google-services.json.

Why: Google Sign-In on Play-installed builds requires Firebase to know the Play "App signing key certificate" SHA-1/SHA-256.

Prereqs:
  - You must download the Play Console "App signing key certificate" (DER) once.
    Play Console → your app → App signing → App signing key certificate → Download certificate
  - A service account JSON with access to:
      - Firebase Management API on the Firebase project
      - (Optional) none for Play here, we do NOT call Play APIs (Play doesn't expose SHA-1 reliably via REST).

Defaults (repo root):
  --service-account-json androidApp/service-account.json
  --cert-der             androidApp/deployment_cert.der
  --firebase-android-app-id  read from androidApp/google-services.json (mobilesdk_app_id) when present

Optional args:
  --android-package <pkg>      Override Android package name (default: read from google-services.json).
  --service-account-json <path>   Override service account JSON path.
  --firebase-android-app-id <id>  Override Firebase Android appId (e.g. 1:305319734071:android:...).
  --cert-der <path>               Override Play "App signing key certificate" .der path.
  --out <path>                    Where to write google-services.json (default: androidApp/google-services.json)
  --dry-run                       Print actions without calling APIs
  --check                         List required files and extracted app ID; exit 1 if anything missing

Example (all defaults):
  ./scripts/sync_firebase_play_signing.sh

  ./scripts/sync_firebase_play_signing.sh --check
EOF
}

# Prints OK/MISS for each required local file. Returns 0 only if all present.
check_required_files() {
  local missing=0
  local app_id

  echo "Required files (under androidApp/, not committed):"
  if [[ -f "$SERVICE_ACCOUNT_JSON" ]]; then
    echo "  OK   service-account.json"
  else
    echo "  MISS service-account.json"
    missing=1
  fi
  if [[ -f "$CERT_DER" ]]; then
    echo "  OK   deployment_cert.der"
  else
    echo "  MISS deployment_cert.der"
    missing=1
  fi
  if [[ -f "$GS_JSON" ]]; then
    echo "  OK   google-services.json"
    app_id="$(read_firebase_app_id_from_gs "$GS_JSON" 2>/dev/null || true)"
    if [[ -n "$app_id" && "$app_id" != "null" ]]; then
      echo "  OK   mobilesdk_app_id → $app_id"
    else
      echo "  MISS mobilesdk_app_id in $OUT (package $ANDROID_PACKAGE)"
      missing=1
    fi
  else
    echo "  MISS google-services.json (needed to read Firebase Android app ID)"
    missing=1
  fi

  return "$missing"
}

die() { echo "❌ $*" >&2; exit 1; }

ANDROID_PACKAGE=""

read_android_package_from_gs() {
  local gs_json="$1"
  # If multiple package names exist, this returns the first (stable ordering from file).
  jq -r '[.client[]?.client_info?.android_client_info?.package_name] | map(select(. != null and . != "")) | .[0] // empty' "$gs_json"
}

# Reads mobilesdk_app_id from google-services.json (Firebase Android app ID for Management API).
read_firebase_app_id_from_gs() {
  local gs_json="$1"
  jq -r --arg pkg "$ANDROID_PACKAGE" '
    [.client[]
      | select(.client_info.android_client_info.package_name == $pkg)
      | .client_info.mobilesdk_app_id
    ][0] // empty
  ' "$gs_json"
}

SERVICE_ACCOUNT_JSON="$DEFAULT_SERVICE_ACCOUNT_JSON"
FIREBASE_ANDROID_APP_ID=""
CERT_DER="$DEFAULT_CERT_DER"
OUT="$DEFAULT_OUT"
DRY_RUN="false"
CHECK_ONLY="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --android-package) ANDROID_PACKAGE="${2:-}"; shift 2 ;;
    --service-account-json) SERVICE_ACCOUNT_JSON="${2:-}"; shift 2 ;;
    --firebase-android-app-id) FIREBASE_ANDROID_APP_ID="${2:-}"; shift 2 ;;
    --cert-der) CERT_DER="${2:-}"; shift 2 ;;
    --out) OUT="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN="true"; shift 1 ;;
    --check) CHECK_ONLY="true"; shift 1 ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown arg: $1 (use --help)" ;;
  esac
done

GS_JSON="$ROOT/$OUT"

command -v jq >/dev/null || die "jq is required"

if [[ -z "$ANDROID_PACKAGE" ]]; then
  if [[ -f "$GS_JSON" ]]; then
    ANDROID_PACKAGE="$(read_android_package_from_gs "$GS_JSON")"
  fi
fi
[[ -n "$ANDROID_PACKAGE" && "$ANDROID_PACKAGE" != "null" ]] || die "Missing Android package name. Pass --android-package or ensure $OUT exists with client_info.android_client_info.package_name."

if [[ "$CHECK_ONLY" == "true" ]]; then
  if check_required_files; then
    echo "✅ All required files present."
    exit 0
  fi
  echo "❌ Fix missing files (see docs/FIREBASE_PLAY_SIGNING_SYNC.md)."
  exit 1
fi

[[ -f "$SERVICE_ACCOUNT_JSON" ]] || die "Service account JSON not found: $SERVICE_ACCOUNT_JSON (place androidApp/service-account.json or pass --service-account-json)"
[[ -f "$CERT_DER" ]] || die "Certificate file not found: $CERT_DER (download App signing key certificate to androidApp/deployment_cert.der or pass --cert-der)"

if [[ -z "$FIREBASE_ANDROID_APP_ID" ]]; then
  if [[ -f "$GS_JSON" ]]; then
    FIREBASE_ANDROID_APP_ID="$(read_firebase_app_id_from_gs "$GS_JSON")"
  fi
fi
if [[ -z "$FIREBASE_ANDROID_APP_ID" || "$FIREBASE_ANDROID_APP_ID" == "null" ]]; then
  die "Missing Firebase Android app ID (mobilesdk_app_id).
  - Pass --firebase-android-app-id, or
  - Ensure $OUT exists and contains client_info.mobilesdk_app_id for package $ANDROID_PACKAGE.
  - Firebase Console → Project settings → Your apps → Android → Application ID."
fi
if [[ ! "$FIREBASE_ANDROID_APP_ID" =~ ^[0-9]+:[0-9]+:android:[0-9a-z]+$ ]]; then
  die "Invalid mobilesdk_app_id format: $FIREBASE_ANDROID_APP_ID (expected e.g. 1:305319734071:android:0a5bbce83d2bd52b2688b2)"
fi
command -v openssl >/dev/null || die "openssl is required"
command -v curl >/dev/null || die "curl is required"

sha_fingerprint() {
  local alg="$1"
  local der="$2"
  openssl x509 -inform DER -in "$der" -noout -fingerprint "-$alg" \
    | sed 's/^.*=//; s/\r$//'
}

SHA1="$(sha_fingerprint sha1 "$CERT_DER")"
SHA256="$(sha_fingerprint sha256 "$CERT_DER")"

echo "Play App signing certificate fingerprints (from $CERT_DER):"
echo "  SHA-1:   $SHA1"
echo "  SHA-256: $SHA256"

mkdir -p "$(dirname "$ROOT/$OUT")"
echo "Firebase Android app ID: $FIREBASE_ANDROID_APP_ID"

base64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

now="$(date +%s)"
exp="$((now + 3600))"

SA_EMAIL="$(jq -r .client_email "$SERVICE_ACCOUNT_JSON")"
SA_KEY="$(jq -r .private_key "$SERVICE_ACCOUNT_JSON")"
[[ "$SA_EMAIL" != "null" && -n "$SA_EMAIL" ]] || die "client_email missing in service account JSON"
[[ "$SA_KEY" != "null" && -n "$SA_KEY" ]] || die "private_key missing in service account JSON"

tmpdir="$(mktemp -d)"
cleanup() { rm -rf "$tmpdir"; }
trap cleanup EXIT

key_pem="$tmpdir/key.pem"
printf '%s\n' "$SA_KEY" > "$key_pem"

header='{"alg":"RS256","typ":"JWT"}'
claim="$(jq -nc --arg iss "$SA_EMAIL" --arg aud "https://oauth2.googleapis.com/token" \
  --arg scope "https://www.googleapis.com/auth/firebase https://www.googleapis.com/auth/cloud-platform" \
  --argjson iat "$now" --argjson exp "$exp" \
  '{iss:$iss,scope:$scope,aud:$aud,iat:$iat,exp:$exp}')"

unsigned="$(printf '%s' "$header" | base64url).$(printf '%s' "$claim" | base64url)"
sig="$(printf '%s' "$unsigned" | openssl dgst -sha256 -sign "$key_pem" | base64url)"
jwt="$unsigned.$sig"

if [[ "$DRY_RUN" == "true" ]]; then
  echo "DRY RUN: would request OAuth token and call Firebase APIs."
  exit 0
fi

token_resp="$tmpdir/token.json"
curl -sS -X POST https://oauth2.googleapis.com/token \
  -d grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer \
  -d assertion="$jwt" > "$token_resp"

ACCESS_TOKEN="$(jq -r .access_token "$token_resp")"
[[ "$ACCESS_TOKEN" != "null" && -n "$ACCESS_TOKEN" ]] || die "Failed to obtain access token. Response: $(cat "$token_resp")"

authz=(-H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json")

firebase_base="https://firebase.googleapis.com/v1beta1/projects/-/androidApps/$FIREBASE_ANDROID_APP_ID"

existing="$tmpdir/existing_sha.json"
curl -sS "${authz[@]}" "$firebase_base/sha" > "$existing" || true

has_sha() {
  local want="$1"
  jq -e --arg want "$(echo "$want" | tr '[:upper:]' '[:lower:]')" \
    '.certificates[]?.shaHash? | ascii_downcase == $want' "$existing" >/dev/null 2>&1
}

create_sha() {
  local certType="$1"
  local sha="$2"
  curl -sS "${authz[@]}" -X POST "$firebase_base/sha" \
    -d "$(jq -nc --arg shaHash "$sha" --arg certType "$certType" '{shaHash:$shaHash,certType:$certType}')" \
    >/dev/null
}

if has_sha "$SHA1"; then
  echo "Firebase already has SHA-1."
else
  echo "Adding SHA-1 to Firebase..."
  create_sha "SHA_1" "$SHA1"
fi

if has_sha "$SHA256"; then
  echo "Firebase already has SHA-256."
else
  echo "Adding SHA-256 to Firebase..."
  create_sha "SHA_256" "$SHA256"
fi

config_json="$tmpdir/config.json"
curl -sS "${authz[@]}" "$firebase_base/config" > "$config_json"

# getConfig returns either configFileContents (base64) or a file payload depending on API revision.
contents_b64="$(jq -r '.configFileContents // empty' "$config_json")"
if [[ -n "$contents_b64" && "$contents_b64" != "null" ]]; then
  echo "$contents_b64" | base64 --decode > "$ROOT/$OUT"
else
  # Fallback: sometimes the API returns the JSON directly as a string field.
  direct="$(jq -r '.configFile // .configFileContent // empty' "$config_json")"
  [[ -n "$direct" && "$direct" != "null" ]] || die "Unexpected getConfig response: $(cat "$config_json")"
  printf '%s' "$direct" > "$ROOT/$OUT"
fi

echo "✅ Wrote $OUT"

