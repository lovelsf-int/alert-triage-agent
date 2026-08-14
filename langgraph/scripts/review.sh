#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <analysis-id> [true|false]" >&2
  exit 2
fi

BASE_URL="${BASE_URL:-http://localhost:8081}"
ANALYSIS_ID="$1"
APPROVED="${2:-true}"

curl --fail-with-body -sS -X POST \
  "${BASE_URL}/api/v1/alert-analyses/${ANALYSIS_ID}/reviews" \
  -H 'Content-Type: application/json' \
  -d "{\"approved\": ${APPROVED}, \"comment\": \"manual review from script\"}" \
  | python -m json.tool
