#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"

curl --fail-with-body -sS -X POST "${BASE_URL}/api/v1/alert-analyses" \
  -H 'Content-Type: application/json' \
  -d '{
    "alertId": "ALT-20260814-001",
    "alertType": "AUTH_BRUTE_FORCE",
    "title": "多来源登录失败后出现成功登录",
    "description": "5 分钟内发生 137 次登录失败，来自 19 个异常 IP，随后出现一次成功登录。",
    "severity": "HIGH",
    "source": "iam",
    "assetId": "user:demo-account",
    "attributes": {
      "failedAttempts": 137,
      "sourceIpCount": 19,
      "followedBySuccess": true
    }
  }' | python -m json.tool
