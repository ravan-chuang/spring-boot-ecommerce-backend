#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repository_root}"

failed=0

if git ls-files --error-unmatch observability/secrets/discord_webhook_url.txt >/dev/null 2>&1; then
  printf 'FAIL: the runtime Discord webhook file is tracked by Git.\n' >&2
  failed=1
fi

if git grep -q -I -E 'discord(app)?\.com/api/webhooks/[0-9]+/[A-Za-z0-9_-]+' -- .; then
  printf 'FAIL: a Discord webhook-shaped value exists in a tracked file.\n' >&2
  failed=1
fi

if git grep -q -I -E '(JWT_SECRET|DB_PASSWORD|POSTGRES_PASSWORD)=[^<{][^[:space:]]{15,}' -- ':!*.example' ':!*.md'; then
  printf 'FAIL: a production-secret-shaped assignment exists in a tracked file.\n' >&2
  failed=1
fi

if [[ ${failed} -ne 0 ]]; then
  exit 1
fi

printf 'PASS: targeted secret patterns are absent from tracked files.\n'
