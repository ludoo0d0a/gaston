#!/usr/bin/env bash
# Fail if tracked files contain likely API keys or credentials.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "check-no-secrets: not a git repository" >&2
  exit 1
fi

fail=0

should_skip() {
  case "$1" in
    docs/*|scripts/check-no-secrets.sh|http/http-client.env.json|*.md)
      return 0
      ;;
    */*Test.kt|*/commonTest/*|lint-baseline.xml)
      return 0
      ;;
  esac
  return 1
}

check_pattern() {
  local name="$1"
  local pattern="$2"
  local f hits

  while IFS= read -r f; do
    [ -z "$f" ] && continue
    should_skip "$f" && continue
    if grep -qE "$pattern" "$f" 2>/dev/null; then
      if [ -z "${hits:-}" ]; then
        hits="$f"
      else
        hits="$hits"$'\n'"$f"
      fi
    fi
  done <<EOF
$(git ls-files)
EOF

  if [ -n "${hits:-}" ]; then
    echo "check-no-secrets: $name matched in:" >&2
    echo "$hits" | sed 's/^/  /' >&2
    fail=1
  fi
}

check_pattern "Google API key (AIza…)" 'AIza[0-9A-Za-z_-]{20,}'
check_pattern "AWS access key" 'AKIA[0-9A-Z]{16}'
check_pattern "private key block" 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY'
check_pattern "service account private_key" '"private_key"[[:space:]]*:'
check_pattern "hardcoded Fastned key prefix" 'wVOx5Bf5EU6FLEkqBtV3h5fXj5MLFcJA'
check_pattern "hardcoded Chargy key" '486ac6e4-93b8-4369-9c6a-28f7c4e1a81f'
check_pattern "hardcoded Romania Parse keys" 'YueWcf0orjSz3IQmaT8yBNDTM5POP0mOU6EDyE3U|ctPx9Ahrz9aaXhEvN0oWCzlX8FHX1cv3r7vZwxH8'

if [ "$fail" -ne 0 ]; then
  echo >&2
  echo "Remove secrets from source and use local.properties / GitHub Secrets (see docs/ENV_VARS.md)." >&2
  exit 1
fi

echo "check-no-secrets: OK"
